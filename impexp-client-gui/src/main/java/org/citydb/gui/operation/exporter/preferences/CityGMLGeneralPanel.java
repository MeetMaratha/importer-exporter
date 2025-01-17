/*
 * 3D City Database - The Open Source CityGML Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2013 - 2024
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
package org.citydb.gui.operation.exporter.preferences;

import org.citydb.config.Config;
import org.citydb.config.i18n.Language;
import org.citydb.gui.components.TitledPanel;
import org.citydb.gui.plugin.internal.InternalPreferencesComponent;
import org.citydb.gui.util.GuiUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

public class CityGMLGeneralPanel extends InternalPreferencesComponent {
    private TitledPanel formatOptionsPanel;
    private JCheckBox prettyPrint;
    private JCheckBox convertGlobalAppearances;
    private JLabel conversionHint;

    public CityGMLGeneralPanel(Config config) {
        super(config);
        initGui();
    }

    @Override
    public boolean isModified() {
        if (prettyPrint.isSelected() != config.getExportConfig().getCityGMLOptions().isPrettyPrint()) return true;
        if (convertGlobalAppearances.isSelected() != config.getExportConfig().getCityGMLOptions().isConvertGlobalAppearances())
            return true;
        return false;
    }

    private void initGui() {
        prettyPrint = new JCheckBox();
        convertGlobalAppearances = new JCheckBox();
        conversionHint = new JLabel();
        conversionHint.setFont(conversionHint.getFont().deriveFont(Font.ITALIC));

        setLayout(new GridBagLayout());
        {
            JPanel content = new JPanel();
            content.setLayout(new GridBagLayout());
            {
                int lmargin = GuiUtil.getTextOffset(convertGlobalAppearances);
                content.add(prettyPrint, GuiUtil.setConstraints(0, 0, 1, 1, GridBagConstraints.BOTH, 0, 0, 5, 0));
                content.add(convertGlobalAppearances, GuiUtil.setConstraints(0, 1, 1, 1, GridBagConstraints.BOTH, 0, 0, 5, 0));
                content.add(conversionHint, GuiUtil.setConstraints(0, 2, 1, 1, GridBagConstraints.BOTH, 0, lmargin, 0, 0));
            }

            formatOptionsPanel = new TitledPanel().build(content);
        }

        add(formatOptionsPanel, GuiUtil.setConstraints(0, 0, 1, 0, GridBagConstraints.BOTH, 0, 0, 0, 0));
    }

    @Override
    public void loadSettings() {
        prettyPrint.setSelected(config.getExportConfig().getCityGMLOptions().isPrettyPrint());
        convertGlobalAppearances.setSelected(config.getExportConfig().getCityGMLOptions().isConvertGlobalAppearances());
    }

    @Override
    public void setSettings() {
        config.getExportConfig().getCityGMLOptions().setPrettyPrint(prettyPrint.isSelected());
        config.getExportConfig().getCityGMLOptions().setConvertGlobalAppearances(convertGlobalAppearances.isSelected());
    }

    @Override
    public void switchLocale(Locale locale) {
        formatOptionsPanel.setTitle(Language.I18N.getString("pref.export.citygml.border.general"));
        prettyPrint.setText(Language.I18N.getString("pref.export.common.label.prettyPrint"));
        convertGlobalAppearances.setText(Language.I18N.getString("pref.export.citygml.label.convertGlobalAppearances"));
        conversionHint.setText(Language.I18N.getString("pref.export.citygml.label.conversionHint"));
    }

    @Override
    public String getLocalizedTitle() {
        return Language.I18N.getString("pref.tree.export.citygml.general");
    }
}
