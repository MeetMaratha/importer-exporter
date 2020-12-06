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

package org.citydb.cli;

import org.citydb.cli.options.vis.ColladaOption;
import org.citydb.cli.options.vis.DisplayOption;
import org.citydb.cli.options.vis.ElevationOption;
import org.citydb.cli.options.vis.GltfOption;
import org.citydb.cli.options.vis.QueryOption;
import org.citydb.config.Config;
import org.citydb.config.project.database.DatabaseConnection;
import org.citydb.config.project.kmlExporter.KmlExportConfig;
import org.citydb.database.DatabaseController;
import org.citydb.log.Logger;
import org.citydb.modules.kml.controller.KmlExportException;
import org.citydb.modules.kml.controller.KmlExporter;
import org.citydb.plugin.CliCommand;
import org.citydb.plugin.cli.DatabaseOption;
import org.citydb.plugin.cli.ThreadPoolOption;
import org.citydb.registry.ObjectRegistry;
import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(
        name = "export-vis",
        description = "Exports data in KML/COLLADA/glTF format for visualization.",
        versionProvider = ImpExpCli.class
)
public class ExportVisCommand extends CliCommand {
    @CommandLine.Option(names = {"-o", "--output"}, required = true,
            description = "Name of the master KML output file.")
    private Path file;

    @CommandLine.Option(names = {"-z", "--kmz"},
            description = "Compress KML/COLLADA output and save as KMZ.")
    private boolean exportAsKmz;

    @CommandLine.Option(names = {"-j", "--json-metadata"},
            description = "Write JSON metadata file.")
    private boolean json;

    @CommandLine.ArgGroup
    private ThreadPoolOption threadPoolOption;

    @CommandLine.ArgGroup(exclusive = false, heading = "Display options:%n")
    private DisplayOption displayOption;

    @CommandLine.ArgGroup(exclusive = false, heading = "Query and filter options:%n")
    private QueryOption queryOption;

    @CommandLine.ArgGroup(exclusive = false, heading = "COLLADA/glTF rendering options:%n")
    private final ColladaOption colladaOption = new ColladaOption();

    @CommandLine.ArgGroup(exclusive = false, heading = "glTF export options:%n")
    private GltfOption gltfOption;

    @CommandLine.ArgGroup(exclusive = false, heading = "Elevation options:%n")
    private final ElevationOption elevationOption = new ElevationOption();

    @CommandLine.ArgGroup(exclusive = false, heading = "Database connection options:%n")
    private DatabaseOption databaseOption;

    private final Logger log = Logger.getInstance();

    @Override
    public Integer call() throws Exception {
        Config config = ObjectRegistry.getInstance().getConfig();

        // connect to database
        DatabaseController database = ObjectRegistry.getInstance().getDatabaseController();
        DatabaseConnection connection = databaseOption != null ?
                databaseOption.toDatabaseConnection() :
                config.getDatabaseConfig().getActiveConnection();

        if (!database.connect(connection)) {
            log.warn("Database export aborted.");
            return 1;
        }

        // set general export options
        setExportOptions(config.getKmlExportConfig());

        // set display options
        setDisplayOptions(config.getKmlExportConfig());

        // set user-defined query options
        if (queryOption != null) {
            config.getKmlExportConfig().setQuery(queryOption.toSimpleKmlQuery());
        }

        // set COLLADA/glTF rendering options
        config.getKmlExportConfig().setColladaOptions(colladaOption.toColladaOptions());

        // set glTF options
        if (gltfOption != null) {
            config.getKmlExportConfig().setGltfOptions(gltfOption.toGltfOptions());
        }

        // set elevation options
        setElevationOptions(config);

        try {
            new KmlExporter().doExport(file);
            log.info("Database export successfully finished.");
        } catch (KmlExportException e) {
            log.error(e.getMessage(), e.getCause());
            log.warn("Database export aborted.");
            return 1;
        } finally {
            database.disconnect(true);
        }

        return 0;
    }

    private void setExportOptions(KmlExportConfig kmlExportConfig) {
        if (exportAsKmz) {
            kmlExportConfig.setExportAsKmz(exportAsKmz);
        }

        if (json) {
            kmlExportConfig.setWriteJSONFile(json);
        }

        if (threadPoolOption != null) {
            kmlExportConfig.getResources().setThreadPool(threadPoolOption.toThreadPool());
        }
    }

    private void setDisplayOptions(KmlExportConfig kmlExportConfig) {
        if (displayOption != null) {
            kmlExportConfig.setLodToExportFrom(displayOption.getLod());
            kmlExportConfig.setAppearanceTheme(displayOption.getAppearanceTheme());
            kmlExportConfig.setDisplayForms(displayOption.toDisplayForms());
        }
    }

    private void setElevationOptions(Config config) {
        config.getKmlExportConfig().setElevation(elevationOption.toElevation());
        config.getGlobalConfig().getApiKeys().setGoogleElevation(elevationOption.getGoogleApiKey());
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (gltfOption != null && exportAsKmz) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --gltf and --kmz are mutually exclusive (specify only one)");
        }

        if (gltfOption != null
                && (displayOption == null
                || !displayOption.getModes().contains(DisplayOption.Mode.collada))) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --gltf requires the data to be exported as COLLADA");
        }
    }
}
