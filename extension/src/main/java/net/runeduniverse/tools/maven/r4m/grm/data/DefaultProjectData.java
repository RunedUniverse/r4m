/*
 * Copyright © 2024 VenaNocta (venanocta@gmail.com)
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
package net.runeduniverse.tools.maven.r4m.grm.data;

import net.runeduniverse.tools.maven.r4m.grm.view.api.ProjectView;

public class DefaultProjectData implements ProjectView {

	protected String groupId;
	protected String artifactId;
	protected String packaging;

	protected DefaultProjectData() {
	}

	public DefaultProjectData(final String groupId, final String artifactId, final String packaging) {
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.packaging = packaging;
	}

	@Override
	public String getGroupId() {
		return this.groupId;
	}

	@Override
	public String getArtifactId() {
		return this.artifactId;
	}

	@Override
	public String getPackaging() {
		return this.packaging;
	}
}