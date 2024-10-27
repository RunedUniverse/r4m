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
package net.runeduniverse.tools.maven.r4m.grm.converter.api;

import org.codehaus.plexus.configuration.PlexusConfiguration;

import net.runeduniverse.tools.maven.r4m.grm.model.DataEntry;
import net.runeduniverse.tools.maven.r4m.grm.model.ExecutionSource;
import net.runeduniverse.tools.maven.r4m.grm.model.GoalContainer;

public interface CheckDataConverter {

	public GoalContainer convertContainer(final PlexusConfiguration cnf, final String defaultGroupId,
			final String defaultArtifactId, final ExecutionSource defaultSource);

	public DataEntry convertEntry(final PlexusConfiguration cnf);

	public PlexusConfiguration convertContainer(final ConfigurationFactory<PlexusConfiguration> factory,
			final GoalContainer container);

	public PlexusConfiguration convertEntry(final ConfigurationFactory<PlexusConfiguration> factory,
			final DataEntry entry);
}
