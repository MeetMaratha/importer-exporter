/*
 * This file is part of the 3D City Database Importer/Exporter.
 * Copyright (c) 2007 - 2013
 * Institute for Geodesy and Geoinformation Science
 * Technische Universitaet Berlin, Germany
 * http://www.gis.tu-berlin.de/
 * 
 * The 3D City Database Importer/Exporter program is free software:
 * you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program. If not, see 
 * <http://www.gnu.org/licenses/>.
 * 
 * The development of the 3D City Database Importer/Exporter has 
 * been financially supported by the following cooperation partners:
 * 
 * Business Location Center, Berlin <http://www.businesslocationcenter.de/>
 * virtualcitySYSTEMS GmbH, Berlin <http://www.virtualcitysystems.de/>
 * Berlin Senate of Business, Technology and Women <http://www.berlin.de/sen/wtf/>
 */
package de.tub.citydb.modules.citygml.importer.database.xlink.resolver;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;

import de.tub.citydb.api.concurrent.WorkerPool;
import de.tub.citydb.api.event.EventDispatcher;
import de.tub.citydb.config.internal.Internal;
import de.tub.citydb.database.TableEnum;
import de.tub.citydb.log.Logger;
import de.tub.citydb.modules.citygml.common.database.cache.CacheTable;
import de.tub.citydb.modules.citygml.common.database.cache.CacheTableManager;
import de.tub.citydb.modules.citygml.common.database.cache.model.CacheTableModelEnum;
import de.tub.citydb.modules.citygml.common.database.xlink.DBXlink;
import de.tub.citydb.modules.citygml.common.database.xlink.DBXlinkBasic;
import de.tub.citydb.modules.citygml.common.database.xlink.DBXlinkDeprecatedMaterial;
import de.tub.citydb.modules.citygml.common.database.xlink.DBXlinkGroupToCityObject;
import de.tub.citydb.modules.citygml.common.database.xlink.DBXlinkLibraryObject;
import de.tub.citydb.modules.citygml.common.database.xlink.DBXlinkSurfaceGeometry;
import de.tub.citydb.modules.citygml.common.database.xlink.DBXlinkTextureFile;
import de.tub.citydb.modules.citygml.common.database.xlink.DBXlinkTextureParam;
import de.tub.citydb.modules.citygml.common.database.xlink.DBXlinkTextureParamEnum;
import de.tub.citydb.modules.common.event.StatusDialogMessage;
import de.tub.citydb.modules.common.event.StatusDialogProgressBar;

public class DBXlinkSplitter {
	private final Logger LOG = Logger.getInstance();

	private final CacheTableManager cacheTableManager;
	private final WorkerPool<DBXlink> xlinkResolverPool;
	private final WorkerPool<DBXlink> tmpXlinkPool;
	private final EventDispatcher eventDispatcher;
	private volatile boolean shouldRun = true;

	public DBXlinkSplitter(CacheTableManager cacheTableManager, 
			WorkerPool<DBXlink> xlinkResolverPool, 
			WorkerPool<DBXlink> tmpXlinkPool, 
			EventDispatcher eventDispatcher) {
		this.cacheTableManager = cacheTableManager;
		this.xlinkResolverPool = xlinkResolverPool;
		this.tmpXlinkPool = tmpXlinkPool;
		this.eventDispatcher = eventDispatcher;
	}

	public void shutdown() {
		shouldRun = false;
	}

	public void startQuery() throws SQLException {	
		basicXlinks();
		groupMemberXLinks(true);
		appearanceXlinks();
		libraryObjectXLinks();

		if (!shouldRun)
			return;
		
		// restart xlink worker pools
		// just to make sure all appearance xlinks have been handled
		// before starting to work on geometry xlinks
		try {
			xlinkResolverPool.join();
			tmpXlinkPool.join();
		} catch (InterruptedException e) {
			//
		}

		// xlinks to deprecated appearances can only be handled if
		// appearances have been fully written - otherwise information is
		// missing in tables SURFACE_DATA and TEXTURPARAM
		deprecatedMaterialXlinks();

		// handling geometry xlinks is more tricky...
		// the reason is that we really hard copy the entries within the database.
		// now imagine the following situation: a geometry referenced by an xlink
		// itself points to another geometry. in order to really copy any information
		// we have to resolve the inner xlink firstly. afterwards we can deal with the
		// outer xlink. thus, we need a recursive strategy here...
		surfaceGeometryXlinks(true);
	}

	private void basicXlinks() throws SQLException {
		if (!shouldRun)
			return;

		Statement stmt = null;
		ResultSet rs = null;

		try {
			CacheTable cacheTable = cacheTableManager.getCacheTable(CacheTableModelEnum.BASIC);	
			if (cacheTable == null)
				return;

			LOG.info("Resolving feature XLinks...");
			eventDispatcher.triggerEvent(new StatusDialogProgressBar(0, 0, this));
			eventDispatcher.triggerEvent(new StatusDialogMessage(Internal.I18N.getString("import.dialog.basicXLink.msg"), this));
			
			int max = (int)cacheTable.size();
			int current = 0;
			
			stmt = cacheTable.getConnection().createStatement();
			rs = stmt.executeQuery("select * from " + cacheTable.getTableName());

			while (rs.next() && shouldRun) {
				eventDispatcher.triggerEvent(new StatusDialogProgressBar(++current, max, this));

				long id = rs.getLong("ID");
				int fromTable = rs.getInt("FROM_TABLE");
				String gmlId = rs.getString("GMLID");
				int toTable = rs.getInt("TO_TABLE");
				String attrName = rs.getString("ATTRNAME");

				// set initial context...
				DBXlinkBasic xlink = new DBXlinkBasic(
						id,
						TableEnum.fromInt(fromTable),
						gmlId,
						TableEnum.fromInt(toTable));

				if (attrName != null && attrName.length() != 0)
					xlink.setAttrName(attrName);

				xlinkResolverPool.addWork(xlink);
			}
		} finally {
			if (rs != null) {
				rs.close();
				rs = null;
			}

			if (stmt != null) {
				stmt.close();
				stmt = null;
			}
		}
	}
	
	private void groupMemberXLinks(boolean checkRecursive) throws SQLException {
		if (!shouldRun)
			return;

		CacheTable cacheTable = cacheTableManager.getCacheTable(CacheTableModelEnum.GROUP_TO_CITYOBJECT);		
		if (cacheTable == null)
			return;

		LOG.info("Resolving CityObjectGroup XLinks...");

		queryGroupMemberXLinks(cacheTable, checkRecursive, -1, 1);
	}

	private void queryGroupMemberXLinks(CacheTable cacheTable, 
			boolean checkRecursive, 
			long remaining, 
			int pass) throws SQLException {
		Statement stmt = null;
		ResultSet rs = null;

		try {					
			eventDispatcher.triggerEvent(new StatusDialogProgressBar(0, 0, this));
			String text = Internal.I18N.getString("import.dialog.groupXLink.msg");
			Object[] args = new Object[]{ pass };
			eventDispatcher.triggerEvent(new StatusDialogMessage(MessageFormat.format(text, args), this));

			int max = (remaining == -1) ? (int)cacheTable.size() : (int)remaining;
			int current = 0;

			CacheTable mirrorTable = cacheTable.mirrorAndIndex();
			cacheTable.truncate();
			
			stmt = mirrorTable.getConnection().createStatement();
			rs = stmt.executeQuery("select * from " + mirrorTable.getTableName());

			while (rs.next() && shouldRun) {
				eventDispatcher.triggerEvent(new StatusDialogProgressBar(++current, max, this));

				long groupId = rs.getLong("GROUP_ID");
				String gmlId = rs.getString("GMLID");
				int isParent = rs.getInt("IS_PARENT");
				String role = rs.getString("ROLE");

				// set initial context...
				DBXlinkGroupToCityObject xlink = new DBXlinkGroupToCityObject(
						groupId,
						gmlId,
						isParent == 1);

				xlink.setRole(role);
				xlinkResolverPool.addWork(xlink);
			}

			if (checkRecursive && shouldRun) {
				rs.close();
				stmt.close();

				try {
					xlinkResolverPool.join();
					tmpXlinkPool.join();
				} catch (InterruptedException e) {
					//
				}

				long unresolved = cacheTable.size();
				if (unresolved > 0) {
					if (unresolved != remaining) {
						// we still have unresolved xlinks... so do another recursion
						cacheTable.dropMirrorTable();
						queryGroupMemberXLinks(cacheTable, checkRecursive, unresolved, ++pass);
					} else {
						// we detected a cycle and cannot resolve the remaining xlinks
						LOG.error("Illegal graph cycle in grouping detected. XLink references cannot be resolved.");
					}
				}
			}
		} finally {
			if (rs != null) {
				rs.close();
				rs = null;
			}

			if (stmt != null) {
				stmt.close();
				stmt = null;
			}
		}
	}

	private void appearanceXlinks() throws SQLException {
		if (!shouldRun)
			return;

		Statement stmt = null;
		ResultSet rs = null;

		try {
			if (!cacheTableManager.existsCacheTable(CacheTableModelEnum.TEXTUREPARAM) && 
					!cacheTableManager.existsCacheTable(CacheTableModelEnum.TEXTURE_FILE))
				return;			

			LOG.info("Resolving appearance XLinks...");
			eventDispatcher.triggerEvent(new StatusDialogProgressBar(0, 0, this));
			eventDispatcher.triggerEvent(new StatusDialogMessage(Internal.I18N.getString("import.dialog.appXlink.msg"), this));

			// first run: resolve texture param
			if (cacheTableManager.existsCacheTable(CacheTableModelEnum.TEXTUREPARAM)) {			
				CacheTable temporaryTable = cacheTableManager.getCacheTable(CacheTableModelEnum.TEXTUREPARAM);				
				temporaryTable.enableIndexes();				
				
				if (cacheTableManager.existsCacheTable(CacheTableModelEnum.LINEAR_RING))
					cacheTableManager.getCacheTable(CacheTableModelEnum.LINEAR_RING).enableIndexes();

				int max = (int)temporaryTable.size();
				int current = 0;

				stmt = temporaryTable.getConnection().createStatement();
				rs = stmt.executeQuery("select * from " + temporaryTable.getTableName() + 
						" where not TYPE=" + DBXlinkTextureParamEnum.XLINK_TEXTUREASSOCIATION.ordinal());

				while (rs.next() && shouldRun) {
					eventDispatcher.triggerEvent(new StatusDialogProgressBar(++current, max, this));

					long id = rs.getLong("ID");
					String gmlId = rs.getString("GMLID");
					int appType = rs.getInt("TYPE");

					// set initial context...
					DBXlinkTextureParam xlink = new DBXlinkTextureParam(
							id,
							gmlId,
							DBXlinkTextureParamEnum.fromInt(appType));

					int isTexPara = rs.getInt("IS_TEXTURE_PARAMETERIZATION");
					if (!rs.wasNull())
						xlink.setTextureParameterization(isTexPara != 0);

					String texParamGmlId = rs.getString("TEXPARAM_GMLID");
					if (!rs.wasNull())
						xlink.setTexParamGmlId(texParamGmlId);

					String worldToTexture = rs.getString("WORLD_TO_TEXTURE");
					if (!rs.wasNull())
						xlink.setWorldToTexture(worldToTexture);

					String textureCoord = rs.getString("TEXTURE_COORDINATES");
					if (!rs.wasNull())
						xlink.setTextureCoord(textureCoord);

					String targetURI = rs.getString("TARGET_URI");
					if (!rs.wasNull())
						xlink.setTargetURI(targetURI);

					String texCoordListId = rs.getString("TEXCOORDLIST_ID");
					if (!rs.wasNull())
						xlink.setTexCoordListId(texCoordListId);

					xlinkResolverPool.addWork(xlink);
				}

				rs.close();
				stmt.close();
			}

			// second run: import texture images and world files
			if (cacheTableManager.existsCacheTable(CacheTableModelEnum.TEXTURE_FILE)) {		
				LOG.info("Importing texture images...");
				eventDispatcher.triggerEvent(new StatusDialogProgressBar(0, 0, this));
				eventDispatcher.triggerEvent(new StatusDialogMessage(Internal.I18N.getString("import.dialog.texImg.msg"), this));
				
				CacheTable temporaryTable = cacheTableManager.getCacheTable(CacheTableModelEnum.TEXTURE_FILE);

				int max = (int)temporaryTable.size();
				int current = 0;
				
				stmt = temporaryTable.getConnection().createStatement();
				rs = stmt.executeQuery("select * from " + temporaryTable.getTableName());

				while (rs.next() && shouldRun) {
					eventDispatcher.triggerEvent(new StatusDialogProgressBar(++current, max, this));
					
					long id = rs.getLong("ID");
					String imageURI = rs.getString("FILE_URI");
					boolean isWorldFile = rs.getBoolean("IS_WORLD_FILE");

					xlinkResolverPool.addWork(new DBXlinkTextureFile(id, imageURI, isWorldFile));
				}

				rs.close();
				stmt.close();
			}

			// restart xlink worker pools
			try {
				xlinkResolverPool.join();
				tmpXlinkPool.join();
			} catch (InterruptedException e) {
				//
			}

			if (!shouldRun)
				return;

			// third run: identifying xlinks to texture association elements...
			if (cacheTableManager.existsCacheTable(CacheTableModelEnum.TEXTUREPARAM) && 
					cacheTableManager.existsCacheTable(CacheTableModelEnum.TEXTUREASSOCIATION)) {
				eventDispatcher.triggerEvent(new StatusDialogProgressBar(0, 0, this));
				eventDispatcher.triggerEvent(new StatusDialogMessage(Internal.I18N.getString("import.dialog.appXlink.msg"), this));
				
				CacheTable cacheTable = cacheTableManager.getCacheTable(CacheTableModelEnum.TEXTUREPARAM);
				cacheTableManager.getCacheTable(CacheTableModelEnum.TEXTUREASSOCIATION).enableIndexes();

				int max = (int)cacheTable.size();
				int current = 0;
				
				stmt = cacheTable.getConnection().createStatement();
				rs = stmt.executeQuery("select * from " + cacheTable.getTableName() + " where TYPE=" + DBXlinkTextureParamEnum.XLINK_TEXTUREASSOCIATION.ordinal());

				while (rs.next() && shouldRun) {
					eventDispatcher.triggerEvent(new StatusDialogProgressBar(++current, max, this));

					long id = rs.getLong("ID");
					String gmlId = rs.getString("GMLID");
					String targetURI = rs.getString("TARGET_URI");

					DBXlinkTextureParam xlink = new DBXlinkTextureParam(
							id,
							gmlId,
							DBXlinkTextureParamEnum.XLINK_TEXTUREASSOCIATION);

					xlink.setTargetURI(targetURI);
					xlinkResolverPool.addWork(xlink);
				}
			}

		} finally {
			if (rs != null) {
				rs.close();
				rs = null;
			}

			if (stmt != null) {
				stmt.close();
				stmt = null;
			}
		}
	}

	private void libraryObjectXLinks() throws SQLException {
		if (!shouldRun)
			return;
		
		Statement stmt = null;
		ResultSet rs = null;

		try {
			CacheTable cacheTable = cacheTableManager.getCacheTable(CacheTableModelEnum.LIBRARY_OBJECT);	
			if (cacheTable == null)
				return;
			
			LOG.info("Importing library objects...");
			eventDispatcher.triggerEvent(new StatusDialogProgressBar(0, 0, this));
			eventDispatcher.triggerEvent(new StatusDialogMessage(Internal.I18N.getString("import.dialog.libObj.msg"), this));
				
			int max = (int)cacheTable.size();
			int current = 0;
			
			stmt = cacheTable.getConnection().createStatement();
			rs = stmt.executeQuery("select * from " + cacheTable.getTableName());

			while (rs.next() && shouldRun) {
				eventDispatcher.triggerEvent(new StatusDialogProgressBar(++current, max, this));

				long id = rs.getLong("ID");
				String imageURI = rs.getString("FILE_URI");

				// set initial context
				DBXlinkLibraryObject xlink = new DBXlinkLibraryObject(
						id,
						imageURI);
				
				xlinkResolverPool.addWork(xlink);
			}
		} finally {
			if (rs != null) {
				rs.close();
				rs = null;
			}

			if (stmt != null) {
				stmt.close();
				stmt = null;
			}
		}
	}
	
	private void deprecatedMaterialXlinks() throws SQLException {
		if (!shouldRun)
			return;

		Statement stmt = null;
		ResultSet rs = null;

		try {
			CacheTable cacheTable = cacheTableManager.getCacheTable(CacheTableModelEnum.DEPRECATED_MATERIAL);
			if (cacheTable == null)
				return;

			LOG.info("Resolving TexturedSurface XLinks...");
			eventDispatcher.triggerEvent(new StatusDialogProgressBar(0, 0, this));
			eventDispatcher.triggerEvent(new StatusDialogMessage(Internal.I18N.getString("import.dialog.depMat.msg"), this));
			
			int max = (int)cacheTable.size();
			int current = 0;
			
			stmt = cacheTable.getConnection().createStatement();
			rs = stmt.executeQuery("select * from " + cacheTable.getTableName());

			while (rs.next() && shouldRun) {
				eventDispatcher.triggerEvent(new StatusDialogProgressBar(++current, max, this));

				long appearanceId = rs.getLong("ID");
				String gmlId = rs.getString("GMLID");
				long surfaceGeometryId = rs.getLong("SURFACE_GEOMETRY_ID");

				// set initial context
				DBXlinkDeprecatedMaterial xlink = new DBXlinkDeprecatedMaterial(
						appearanceId,
						gmlId,
						surfaceGeometryId);

				xlinkResolverPool.addWork(xlink);
			}
		} finally {
			if (rs != null) {
				rs.close();
				rs = null;
			}

			if (stmt != null) {
				stmt.close();
				stmt = null;
			}
		}
	}

	private void surfaceGeometryXlinks(boolean checkRecursive) throws SQLException {
		if (!shouldRun)
			return;

		CacheTable cacheTable = cacheTableManager.getCacheTable(CacheTableModelEnum.SURFACE_GEOMETRY);
		if (cacheTable == null)
			return;

		LOG.info("Resolving geometry XLinks...");

		querySurfaceGeometryXlinks(cacheTable, checkRecursive, -1, 1);
	}

	private void querySurfaceGeometryXlinks(CacheTable cacheTable, 
			boolean checkRecursive, 
			long remaining, 
			int pass) throws SQLException {
		Statement stmt = null;
		ResultSet rs = null;

		try {
			eventDispatcher.triggerEvent(new StatusDialogProgressBar(0, 0, this));
			String text = Internal.I18N.getString("import.dialog.geomXLink.msg");
			Object[] args = new Object[]{ pass };
			eventDispatcher.triggerEvent(new StatusDialogMessage(MessageFormat.format(text, args), this));

			int max = (remaining == -1) ? (int)cacheTable.size() : (int)remaining;
			int current = 0;
			
			CacheTable mirrorTable = cacheTable.mirrorAndIndex();
			cacheTable.truncate();

			stmt = mirrorTable.getConnection().createStatement();
			rs = stmt.executeQuery("select * from " + mirrorTable.getTableName());

			while (rs.next() && shouldRun) {
				eventDispatcher.triggerEvent(new StatusDialogProgressBar(++current, max, this));
				
				long id = rs.getLong("ID");
				long parentId = rs.getLong("PARENT_ID");
				long rootId = rs.getLong("ROOT_ID");
				boolean reverse = rs.getInt("REVERSE") == 1;
				String gmlId = rs.getString("GMLID");
				long cityObjectId = rs.getLong("CITYOBJECT_ID");
				int fromTable = rs.getInt("FROM_TABLE");
				String attrName = rs.getString("ATTRNAME");

				// set initial context...
				DBXlinkSurfaceGeometry xlink = new DBXlinkSurfaceGeometry(
						id,
						parentId,
						rootId,
						reverse,
						gmlId,
						cityObjectId,
						TableEnum.fromInt(fromTable),
						attrName);

				xlinkResolverPool.addWork(xlink);
			}

			if (checkRecursive && shouldRun) {
				rs.close();
				stmt.close();

				try {
					xlinkResolverPool.join();
					tmpXlinkPool.join();
				} catch (InterruptedException e) {
					//
				}

				long unresolved = cacheTable.size();
				if (unresolved > 0) {
					if (unresolved != remaining) {
						// we still have unresolved xlinks... so do another recursion
						cacheTable.dropMirrorTable();
						querySurfaceGeometryXlinks(cacheTable, checkRecursive, unresolved, ++pass);
					} else {
						// we detected a cycle and cannot resolve the remaining xlinks
						LOG.error("Illegal graph cycle in geometry detected. XLink references cannot be resolved.");
					}
				}
			}
		} finally {
			if (rs != null) {
				rs.close();
				rs = null;
			}

			if (stmt != null) {
				stmt.close();
				stmt = null;
			}
		}
	}
}
