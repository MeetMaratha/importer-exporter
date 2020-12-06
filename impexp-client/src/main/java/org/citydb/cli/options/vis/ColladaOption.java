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

package org.citydb.cli.options.vis;

import org.citydb.config.project.kmlExporter.ColladaOptions;
import org.citydb.plugin.cli.CliOption;
import org.citydb.textureAtlas.TextureAtlasCreator;
import picocli.CommandLine;

public class ColladaOption implements CliOption {
    enum Mode {none, basic, tpim, tpim_wo_rotation}

    @CommandLine.Option(names = {"-s", "--double-sided"},
            description = "Force all surfaces to be double sided.")
    private boolean doubleSided;

    @CommandLine.Option(names = "--no-surface-normals", defaultValue = "true",
            description = "Do not generate surface normals.")
    private boolean surfaceNormals;

    @CommandLine.Option(names = {"-C", "--crop-textures"},
            description = "Crop texture images.")
    private boolean cropTextures;

    @CommandLine.Option(names = {"-x", "--texture-atlas"}, paramLabel = "<mode>", defaultValue = "basic",
            description = "Texture atlas mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Mode textureAtlas;

    @CommandLine.Option(names = "--texture-atlas-pot",
            description = "Texture atlases must be power-of-two sized.")
    private boolean requirePot;

    public ColladaOptions toColladaOptions() {
        ColladaOptions colladaOptions = new ColladaOptions();
        colladaOptions.setIgnoreSurfaceOrientation(doubleSided);
        colladaOptions.setGenerateSurfaceNormals(surfaceNormals);
        colladaOptions.setCropImages(cropTextures);
        colladaOptions.setGenerateTextureAtlases(textureAtlas != Mode.none);
        if (textureAtlas != Mode.none) {
            switch (textureAtlas) {
                case tpim:
                    colladaOptions.setPackingAlgorithm(TextureAtlasCreator.TPIM);
                    break;
                case tpim_wo_rotation:
                    colladaOptions.setPackingAlgorithm(TextureAtlasCreator.TPIM_WO_ROTATION);
                    break;
                default:
                    colladaOptions.setPackingAlgorithm(TextureAtlasCreator.BASIC);
                    break;
            }

            colladaOptions.setTextureAtlasPots(requirePot);
        }

        return colladaOptions;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (requirePot && textureAtlas == Mode.none) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --texture-atlas-pot requires texture atlases to be created");
        }
    }
}
