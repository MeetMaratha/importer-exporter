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
package org.citydb.config;

import org.citydb.config.gui.Gui;
import org.citydb.config.internal.Internal;
import org.citydb.config.project.Project;

public class Config {
	private Project project;
	private Gui gui;
	private Internal internal;

	public Config(Project project, Gui gui, Internal internal) {
		this.project = project;
		this.gui = gui;
		this.internal = internal;
	}
	
	public Config() {
		this(new Project(), new Gui(), new Internal());
	}

	public Project getProject() {
		return project;
	}

	public void setProject(Project project) {
		if (project != null) {
			this.project = project;
			
			// add things to be done after changing the project settings
			// (e.g., after unmarshalling the config file) here 
			project.getDatabaseConfig().addDefaultReferenceSystems();
		}
	}

	public Gui getGui() {
		return gui;
	}

	public void setGui(Gui gui) {
		if (gui != null)
			this.gui = gui;
	}

	public Internal getInternal() {
		return internal;
	}

}
