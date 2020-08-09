/*
 * 3D City Database - The Open Source CityGML Database
 * http://www.3dcitydb.org/
 *
 * Copyright 2013 - 2019
 * Chair of Geoinformatics
 * Technical University of Munich, Germany
 * https://www.gis.bgu.tum.de/
 *
 * The 3D City Database is jointly developed with the following
 * cooperation partners:
 *
 * virtualcitySYSTEMS GmbH, Berlin <http://www.virtualcitysystems.de/>
 * M.O.S.S. Computer Grafik Systeme GmbH, Taufkirchen <http://www.moss.de/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.citydb.citygml.exporter.database.content;

import org.citydb.citygml.exporter.CityGMLExportException;
import org.citydb.citygml.exporter.util.AttributeValueSplitter;
import org.citydb.citygml.exporter.util.AttributeValueSplitter.SplitValue;
import org.citydb.config.geometry.GeometryObject;
import org.citydb.database.schema.TableEnum;
import org.citydb.database.schema.mapping.FeatureType;
import org.citydb.query.filter.lod.LodFilter;
import org.citydb.query.filter.lod.LodIterator;
import org.citydb.query.filter.projection.CombinedProjectionFilter;
import org.citydb.query.filter.projection.ProjectionFilter;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.select.Select;
import org.citydb.sqlbuilder.select.join.JoinFactory;
import org.citydb.sqlbuilder.select.operator.comparison.ComparisonName;
import org.citygml4j.model.citygml.waterbody.AbstractWaterBoundarySurface;
import org.citygml4j.model.citygml.waterbody.BoundedByWaterSurfaceProperty;
import org.citygml4j.model.citygml.waterbody.WaterBody;
import org.citygml4j.model.citygml.waterbody.WaterSurface;
import org.citygml4j.model.gml.basicTypes.Code;
import org.citygml4j.model.gml.geometry.aggregates.MultiCurveProperty;
import org.citygml4j.model.module.citygml.CityGMLModuleType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DBWaterBody extends AbstractFeatureExporter<WaterBody> {
	private final DBSurfaceGeometry geometryExporter;
	private final DBCityObject cityObjectExporter;
	private final GMLConverter gmlConverter;

	private final String waterBodyModule;
	private final LodFilter lodFilter;
	private final AttributeValueSplitter valueSplitter;
	private final boolean hasObjectClassIdColumn;
	private final boolean useXLink;
	private final List<Table> bodyADEHookTables;
	private List<Table> surfaceADEHookTables;

	public DBWaterBody(Connection connection, CityGMLExportManager exporter) throws CityGMLExportException, SQLException {
		super(WaterBody.class, connection, exporter);

		CombinedProjectionFilter projectionFilter = exporter.getCombinedProjectionFilter(TableEnum.WATERBODY.getName());
		CombinedProjectionFilter boundarySurfaceProjectionFilter = exporter.getCombinedProjectionFilter(TableEnum.WATERBOUNDARY_SURFACE.getName());
		waterBodyModule = exporter.getTargetCityGMLVersion().getCityGMLModule(CityGMLModuleType.WATER_BODY).getNamespaceURI();
		lodFilter = exporter.getLodFilter();
		String schema = exporter.getDatabaseAdapter().getConnectionDetails().getSchema();		
		hasObjectClassIdColumn = exporter.getDatabaseAdapter().getConnectionMetaData().getCityDBVersion().compareTo(4, 0, 0) >= 0;
		useXLink = exporter.getExportConfig().getXlink().getFeature().isModeXLink();

		table = new Table(TableEnum.WATERBODY.getName(), schema);
		Table waterBoundarySurface = new Table(TableEnum.WATERBOUNDARY_SURFACE.getName(), schema);

		select = new Select().addProjection(table.getColumn("id"));
		if (hasObjectClassIdColumn) select.addProjection(table.getColumn("objectclass_id"));
		if (projectionFilter.containsProperty("class", waterBodyModule)) select.addProjection(table.getColumn("class"), table.getColumn("class_codespace"));
		if (projectionFilter.containsProperty("function", waterBodyModule)) select.addProjection(table.getColumn("function"), table.getColumn("function_codespace"));
		if (projectionFilter.containsProperty("usage", waterBodyModule)) select.addProjection(table.getColumn("usage"), table.getColumn("usage_codespace"));
		if (lodFilter.isEnabled(0)) {
			if (projectionFilter.containsProperty("lod0MultiCurve", waterBodyModule)) select.addProjection(exporter.getGeometryColumn(table.getColumn("lod0_multi_curve")));
			if (projectionFilter.containsProperty("lod0MultiSurface", waterBodyModule)) select.addProjection(table.getColumn("lod0_multi_surface_id"));
		}
		if (lodFilter.isEnabled(1)) {
			if (projectionFilter.containsProperty("lod1MultiCurve", waterBodyModule)) select.addProjection(exporter.getGeometryColumn(table.getColumn("lod1_multi_curve")));
			if (projectionFilter.containsProperty("lod1MultiSurface", waterBodyModule)) select.addProjection(table.getColumn("lod1_multi_surface_id"));
			if (projectionFilter.containsProperty("lod1Solid", waterBodyModule)) select.addProjection(table.getColumn("lod1_solid_id"));
		}
		if (lodFilter.isEnabled(2) && projectionFilter.containsProperty("lod2Solid", waterBodyModule)) select.addProjection(table.getColumn("lod2_solid_id"));
		if (lodFilter.isEnabled(3) && projectionFilter.containsProperty("lod3Solid", waterBodyModule)) select.addProjection(table.getColumn("lod3_solid_id"));
		if (lodFilter.isEnabled(4) && projectionFilter.containsProperty("lod4Solid", waterBodyModule)) select.addProjection(table.getColumn("lod4_solid_id"));
		if (lodFilter.containsLodGreaterThanOrEuqalTo(2)
				&& projectionFilter.containsProperty("boundedBy", waterBodyModule)) {
			Table waterBodToWaterBndSrf = new Table(TableEnum.WATERBOD_TO_WATERBND_SRF.getName(), schema);
			select.addJoin(JoinFactory.left(waterBodToWaterBndSrf, "waterbody_id", ComparisonName.EQUAL_TO, table.getColumn("id")))
			.addJoin(JoinFactory.left(waterBoundarySurface, "id", ComparisonName.EQUAL_TO, waterBodToWaterBndSrf.getColumn("waterboundary_surface_id")))
			.addProjection(waterBoundarySurface.getColumn("id", "ws_id"), waterBoundarySurface.getColumn("objectclass_id", "ws_objectclass_id"));
			if (boundarySurfaceProjectionFilter.containsProperty("waterLevel", waterBodyModule)) select.addProjection(waterBoundarySurface.getColumn("water_level"), waterBoundarySurface.getColumn("water_level_codespace"));
			if (lodFilter.isEnabled(2) && boundarySurfaceProjectionFilter.containsProperty("lod2Surface", waterBodyModule)) select.addProjection(waterBoundarySurface.getColumn("lod2_surface_id"));
			if (lodFilter.isEnabled(3) && boundarySurfaceProjectionFilter.containsProperty("lod3Surface", waterBodyModule)) select.addProjection(waterBoundarySurface.getColumn("lod3_surface_id"));
			if (lodFilter.isEnabled(4) && boundarySurfaceProjectionFilter.containsProperty("lod4Surface", waterBodyModule)) select.addProjection(waterBoundarySurface.getColumn("lod4_surface_id"));

			surfaceADEHookTables = addJoinsToADEHookTables(TableEnum.WATERBOUNDARY_SURFACE, waterBoundarySurface);
		}
		bodyADEHookTables = addJoinsToADEHookTables(TableEnum.WATERBODY, table);

		cityObjectExporter = exporter.getExporter(DBCityObject.class);
		geometryExporter = exporter.getExporter(DBSurfaceGeometry.class);
		gmlConverter = exporter.getGMLConverter();
		valueSplitter = exporter.getAttributeValueSplitter();
	}

	@Override
	protected Collection<WaterBody> doExport(long id, WaterBody root, FeatureType rootType, PreparedStatement ps) throws CityGMLExportException, SQLException {
		ps.setLong(1, id);

		try (ResultSet rs = ps.executeQuery()) {
			long currentWaterBodyId = 0;
			WaterBody waterBody = null;
			ProjectionFilter projectionFilter = null;
			Map<Long, WaterBody> waterBodies = new HashMap<>();

			while (rs.next()) {
				long waterBodyId = rs.getLong("id");

				if (waterBodyId != currentWaterBodyId || waterBody == null) {
					currentWaterBodyId = waterBodyId;

					waterBody = waterBodies.get(waterBodyId);
					if (waterBody == null) {
						FeatureType featureType;
						if (waterBodyId == id & root != null) {
							waterBody = root;
							featureType = rootType;
						} else {
							if (hasObjectClassIdColumn) {
								// create water body object
								int objectClassId = rs.getInt("objectclass_id");
								waterBody = exporter.createObject(objectClassId, WaterBody.class);
								if (waterBody == null) {
									exporter.logOrThrowErrorMessage("Failed to instantiate " + exporter.getObjectSignature(objectClassId, waterBodyId) + " as water body object.");
									continue;
								}

								featureType = exporter.getFeatureType(objectClassId);
							} else {
								waterBody = new WaterBody();
								featureType = exporter.getFeatureType(waterBody);
							}
						}

						// get projection filter
						projectionFilter = exporter.getProjectionFilter(featureType);

						// export city object information
						cityObjectExporter.addBatch(waterBody, waterBodyId, featureType, projectionFilter);

						if (projectionFilter.containsProperty("class", waterBodyModule)) {
							String clazz = rs.getString("class");
							if (!rs.wasNull()) {
								Code code = new Code(clazz);
								code.setCodeSpace(rs.getString("class_codespace"));
								waterBody.setClazz(code);
							}
						}

						if (projectionFilter.containsProperty("function", waterBodyModule)) {
							for (SplitValue splitValue : valueSplitter.split(rs.getString("function"), rs.getString("function_codespace"))) {
								Code function = new Code(splitValue.result(0));
								function.setCodeSpace(splitValue.result(1));
								waterBody.addFunction(function);
							}
						}

						if (projectionFilter.containsProperty("usage", waterBodyModule)) {
							for (SplitValue splitValue : valueSplitter.split(rs.getString("usage"), rs.getString("usage_codespace"))) {
								Code usage = new Code(splitValue.result(0));
								usage.setCodeSpace(splitValue.result(1));
								waterBody.addUsage(usage);
							}
						}

						LodIterator lodIterator = lodFilter.iterator(0, 1);
						while (lodIterator.hasNext()) {
							int lod = lodIterator.next();

							if (!projectionFilter.containsProperty("lod" + lod + "MultiCurve", waterBodyModule))
								continue;

							Object multiCurveObj = rs.getObject("lod" + lod + "_multi_curve");
							if (rs.wasNull())
								continue;

							GeometryObject multiCurve = exporter.getDatabaseAdapter().getGeometryConverter().getMultiCurve(multiCurveObj);
							if (multiCurve != null) {
								MultiCurveProperty multiCurveProperty = gmlConverter.getMultiCurveProperty(multiCurve, false);
								if (multiCurveProperty != null) {
									switch (lod) {
									case 0:
										waterBody.setLod0MultiCurve(multiCurveProperty);
										break;
									case 1:
										waterBody.setLod1MultiCurve(multiCurveProperty);
										break;
									}
								}
							}
						}

						lodIterator.reset();
						while (lodIterator.hasNext()) {
							int lod = lodIterator.next();

							if (!projectionFilter.containsProperty("lod" + lod + "MultiSurface", waterBodyModule))
								continue;

							long geometryId = rs.getLong("lod" + lod + "_multi_surface_id");
							if (rs.wasNull())
								continue;

							switch (lod) {
								case 0:
									geometryExporter.addBatch(geometryId, waterBody::setLod0MultiSurface);
									break;
								case 1:
									geometryExporter.addBatch(geometryId, waterBody::setLod1MultiSurface);
									break;
							}
						}

						lodIterator = lodFilter.iterator(1, 4);
						while (lodIterator.hasNext()) {
							int lod = lodIterator.next();

							if (!projectionFilter.containsProperty("lod" + lod + "Solid", waterBodyModule))
								continue;

							long geometryId = rs.getLong("lod" + lod + "_solid_id");
							if (rs.wasNull())
								continue;

							switch (lod) {
								case 1:
									geometryExporter.addBatch(geometryId, waterBody::setLod1Solid);
									break;
								case 2:
									geometryExporter.addBatch(geometryId, waterBody::setLod2Solid);
									break;
								case 3:
									geometryExporter.addBatch(geometryId, waterBody::setLod3Solid);
									break;
								case 4:
									geometryExporter.addBatch(geometryId, waterBody::setLod4Solid);
									break;
							}
						}
						
						// delegate export of generic ADE properties
						if (bodyADEHookTables != null) {
							List<String> adeHookTables = retrieveADEHookTables(bodyADEHookTables, rs);
							if (adeHookTables != null)
								exporter.delegateToADEExporter(adeHookTables, waterBody, waterBodyId, featureType, projectionFilter);
						}

						waterBody.setLocalProperty("projection", projectionFilter);
						waterBodies.put(waterBodyId, waterBody);
					} else
						projectionFilter = (ProjectionFilter)waterBody.getLocalProperty("projection");
				}

				// water boundary surfaces
				if (!lodFilter.containsLodGreaterThanOrEuqalTo(2)
						|| !projectionFilter.containsProperty("boundedBy", waterBodyModule))
					break;

				long waterBoundarySurfaceId = rs.getLong("ws_id");
				if (rs.wasNull())
					continue;

				// create new water boundary surface object
				int objectClassId = rs.getInt("ws_objectclass_id");
				AbstractWaterBoundarySurface waterBoundarySurface = exporter.createObject(objectClassId, AbstractWaterBoundarySurface.class);
				if (waterBoundarySurface == null) {
					exporter.logOrThrowErrorMessage("Failed to instantiate " + exporter.getObjectSignature(objectClassId, waterBoundarySurfaceId) + " as water boundary surface object.");
					continue;
				}

				// get projection filter
				FeatureType waterBoundarySurfaceType = exporter.getFeatureType(objectClassId);
				ProjectionFilter waterBoundarySurfaceProjectionFilter = exporter.getProjectionFilter(waterBoundarySurfaceType);

				// export city object information
				cityObjectExporter.addBatch(waterBoundarySurface, waterBoundarySurfaceId, waterBoundarySurfaceType, waterBoundarySurfaceProjectionFilter);

				if (waterBoundarySurface.isSetId()) {
					// process xlink
					if (exporter.lookupAndPutObjectUID(waterBoundarySurface.getId(), waterBoundarySurfaceId, objectClassId)) {
						if (useXLink) {
							BoundedByWaterSurfaceProperty boundedByProperty = new BoundedByWaterSurfaceProperty();
							boundedByProperty.setHref("#" + waterBoundarySurface.getId());
							waterBody.addBoundedBySurface(boundedByProperty);
							continue;
						} else 
							waterBoundarySurface.setId(exporter.generateNewGmlId(waterBoundarySurface));
					}
				}

				if (waterBoundarySurface instanceof WaterSurface
						&& waterBoundarySurfaceProjectionFilter.containsProperty("waterLevel", waterBodyModule)) {
					String waterLevel = rs.getString("water_level");
					if (!rs.wasNull()) {
						Code code = new Code(waterLevel);
						code.setCodeSpace(rs.getString("water_level_codespace"));
						((WaterSurface)waterBoundarySurface).setWaterLevel(code);
					}
				}

				LodIterator lodIterator = lodFilter.iterator(2, 4);
				while (lodIterator.hasNext()) {
					int lod = lodIterator.next();

					if (!waterBoundarySurfaceProjectionFilter.containsProperty("lod" + lod + "Surface", waterBodyModule))
						continue;

					long geometryId = rs.getLong("lod" + lod + "_surface_id");
					if (rs.wasNull())
						continue;

					switch (lod) {
						case 2:
							geometryExporter.addBatch(geometryId, waterBoundarySurface::setLod2Surface);
							break;
						case 3:
							geometryExporter.addBatch(geometryId, waterBoundarySurface::setLod3Surface);
							break;
						case 4:
							geometryExporter.addBatch(geometryId, waterBoundarySurface::setLod4Surface);
							break;
					}
				}
				
				// delegate export of generic ADE properties
				if (surfaceADEHookTables != null) {
					List<String> adeHookTables = retrieveADEHookTables(surfaceADEHookTables, rs);
					if (adeHookTables != null)
						exporter.delegateToADEExporter(adeHookTables, waterBoundarySurface, waterBoundarySurfaceId, waterBoundarySurfaceType, waterBoundarySurfaceProjectionFilter);
				}

				BoundedByWaterSurfaceProperty boundedByProperty = new BoundedByWaterSurfaceProperty(waterBoundarySurface);
				waterBody.addBoundedBySurface(boundedByProperty);
			}

			return waterBodies.values();
		}
	}

}
