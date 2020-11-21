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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlType(name = "ADEKmlExportPreferenceType", propOrder = {
        "target",
        "displayForms",
        "balloon",
        "pointAndCurve"
})
public class ADEPreference {
    @XmlElement(required = true)
    private String target;
    @XmlJavaTypeAdapter(DisplayFormsAdapter.class)
    private DisplayForms displayForms;
    private Balloon balloon;
    private PointAndCurve pointAndCurve;

    public ADEPreference() {
        displayForms = new DisplayForms();
        balloon = new Balloon();
        pointAndCurve = new PointAndCurve();
    }

    public ADEPreference(String target) {
        this();
        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    public boolean isSetTarget() {
        return target != null;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public DisplayForms getDisplayForms() {
        return displayForms;
    }

    public void setDisplayForms(DisplayForms displayForms) {
        if (displayForms != null) {
            this.displayForms = displayForms;
        }
    }

    public Balloon getBalloon() {
        return balloon;
    }

    public void setBalloon(Balloon balloon) {
        this.balloon = balloon;
    }

    public PointAndCurve getPointAndCurve() {
        return pointAndCurve;
    }

    public void setPointAndCurve(PointAndCurve pointAndCurve) {
        this.pointAndCurve = pointAndCurve;
    }

}
