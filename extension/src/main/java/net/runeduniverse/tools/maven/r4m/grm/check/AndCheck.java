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
package net.runeduniverse.tools.maven.r4m.grm.check;

import java.util.Collection;
import java.util.LinkedHashSet;

import net.runeduniverse.lib.utils.conditions.AndCondition;
import net.runeduniverse.lib.utils.conditions.api.Condition;
import net.runeduniverse.tools.maven.r4m.grm.view.api.EntityView;

public class AndCheck extends AndCondition<EntityView> {

	public AndCheck() {
		super(new LinkedHashSet<>());
	}

	public AndCheck(final Collection<Condition<EntityView>> conditions) {
		super(conditions);
	}
}