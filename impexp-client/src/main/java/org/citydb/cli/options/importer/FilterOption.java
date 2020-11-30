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

package org.citydb.cli.options.importer;

import org.citydb.config.project.importer.ImportAppearance;
import org.citydb.config.project.importer.ImportFilter;
import org.citydb.config.project.query.simple.SimpleAttributeFilter;
import org.citydb.plugin.cli.CliOption;
import org.citydb.plugin.cli.ResourceIdOption;
import org.citydb.plugin.cli.TypeNamesOption;
import org.citydb.registry.ObjectRegistry;
import picocli.CommandLine;

public class FilterOption implements CliOption {
    @CommandLine.ArgGroup(exclusive = false)
    private TypeNamesOption typeNamesOption;

    @CommandLine.ArgGroup
    private ResourceIdOption idOption;

    @CommandLine.ArgGroup(exclusive = false)
    private BoundingBoxOption boundingBoxOption;

    @CommandLine.ArgGroup(exclusive = false)
    private CounterOption counterOption;

    @CommandLine.ArgGroup
    private AppearanceOption appearanceOption;

    public ImportFilter toImportFilter() {
        ImportFilter importFilter = new ImportFilter();

        if (typeNamesOption != null) {
            importFilter.setFeatureTypeFilter(typeNamesOption.toFeatureTypeFilter());
            importFilter.setUseTypeNames(true);
        }

        if (idOption != null) {
            SimpleAttributeFilter attributeFilter = new SimpleAttributeFilter();
            attributeFilter.setGmlIdFilter(idOption.toResourceIdOperator());
            importFilter.setAttributeFilter(attributeFilter);
            importFilter.setUseAttributeFilter(true);
        }

        if (boundingBoxOption != null) {
            importFilter.setBboxFilter(boundingBoxOption.toBBOXOperator());
            importFilter.setUseBboxFilter(true);
        }

        if (counterOption != null) {
            importFilter.setCounterFilter(counterOption.toCounterFilter());
            importFilter.setUseCountFilter(true);
        }

        if (appearanceOption != null && !appearanceOption.isExportAppearances()) {
            ImportAppearance appearance = ObjectRegistry.getInstance().getConfig().getImportConfig().getAppearances();
            appearance.setImportAppearances(false);
        }

        return importFilter;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (typeNamesOption != null) {
            typeNamesOption.preprocess(commandLine);
        }

        if (boundingBoxOption != null) {
            boundingBoxOption.preprocess(commandLine);
        }

        if (counterOption != null) {
            counterOption.preprocess(commandLine);
        }
    }
}
