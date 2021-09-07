/*
 * 3D City Database - The Open Source CityGML Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2013 - 2021
 * Chair of Geoinformatics
 * Technical University of Munich, Germany
 * https://www.lrg.tum.de/gis/
 *
 * The 3D City Database is jointly developed with the following
 * cooperation partners:
 *
 * Virtual City Systems, Berlin <https://vc.systems/>
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
package org.citydb.core.operation.importer.util;

import org.citydb.core.operation.importer.CityGMLImportException;
import org.citydb.core.util.CoreConstants;
import org.citydb.util.log.Logger;
import org.citygml4j.model.gml.geometry.AbstractGeometry;
import org.citygml4j.model.gml.geometry.primitives.AbstractRing;
import org.citygml4j.util.child.ChildInfo;

import java.util.List;

public class RingValidator {
	private final Logger log = Logger.getInstance();
	private final boolean failOnError;

	public RingValidator(boolean failOnError) {
		this.failOnError = failOnError;
	}

	public RingValidator() {
		this(false);
	}

	public boolean validate(List<Double> coordinates, AbstractRing ring) throws CityGMLImportException {
		if (coordinates == null || ring.hasLocalProperty(CoreConstants.GEOMETRY_INVALID)) {
			return false;
		}

		// check closedness
		if (coordinates.size() >= 9 && !isClosed(coordinates)) {
			log.debug(getGeometrySignature(ring) + ": Fixed unclosed ring by appending its first point.");
		}

		// too few points
		if (coordinates.size() / 3 < 4) {
			logOrThrowErrorMessage(ring, "Ring has too few points.");
			return false;
		}

		return true;
	}

	private boolean isClosed(List<Double> coords) {
		Double x = coords.get(0);
		Double y = coords.get(1);
		Double z = coords.get(2);

		int nrOfPoints = coords.size();
		if (!x.equals(coords.get(nrOfPoints - 3))
				|| !y.equals(coords.get(nrOfPoints - 2))
				|| !z.equals(coords.get(nrOfPoints - 1))) {

			// repair unclosed ring...
			coords.add(x);
			coords.add(y);
			coords.add(z);

			return false;
		}

		return true;
	}

	public String getGeometrySignature(AbstractRing ring) {
		AbstractGeometry geometry = new ChildInfo().getParentGeometry(ring);
		if (geometry == null) {
			geometry = ring;
		}

		String signature = "gml:" + geometry.getGMLClass().toString();
		String gmlId = geometry.hasLocalProperty(CoreConstants.OBJECT_ORIGINAL_GMLID) ?
				(String) geometry.getLocalProperty(CoreConstants.OBJECT_ORIGINAL_GMLID) :
				geometry.getId();

		if (gmlId != null) {
			signature += " '" + gmlId + "'";
		} else {
			signature += " (unknown gml:id)";
		}

		return signature;
	}

	private void logOrThrowErrorMessage(AbstractRing ring, String message) throws CityGMLImportException {
		ring.setLocalProperty(CoreConstants.GEOMETRY_INVALID, message);
		message = getGeometrySignature(ring) + ": " + message;

		if (!failOnError) {
			log.error(message);
		} else {
			throw new CityGMLImportException(message);
		}
	}
}
