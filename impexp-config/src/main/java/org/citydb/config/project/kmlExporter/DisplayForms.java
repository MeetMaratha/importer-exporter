/*
 * 3D City Database - The Open Source CityGML Database
 * http://www.3dcitydb.org/
 *
 * Copyright 2013 - 2020
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

package org.citydb.config.project.kmlExporter;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public class DisplayForms {
    private final Map<DisplayFormType, DisplayForm> displayForms = new EnumMap<>(DisplayFormType.class);

    public DisplayForms() {
    }

    public boolean isEmpty() {
        return displayForms.isEmpty();
    }

    public DisplayForm get(DisplayFormType type) {
        return displayForms.get(type);
    }

    public DisplayForm getOrDefault(DisplayFormType type) {
        DisplayForm displayForm = displayForms.get(type);
        return displayForm != null ? displayForm : DisplayForm.of(type);
    }

    public void add(DisplayForm displayForm) {
        displayForms.put(displayForm.getType(), displayForm);
    }

    public int getActiveDisplayFormsAmount() {
        return displayForms.values().stream()
                .filter(DisplayForm::isActive)
                .mapToInt(v -> 1)
                .sum();
    }

    public Collection<DisplayForm> values() {
        return displayForms.values();
    }
}