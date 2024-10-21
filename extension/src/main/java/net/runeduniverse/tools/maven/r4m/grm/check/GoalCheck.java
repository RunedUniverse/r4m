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

import java.util.LinkedList;
import java.util.List;

import net.runeduniverse.lib.utils.conditions.DefaultConditionInfo;
import net.runeduniverse.tools.maven.r4m.grm.view.api.EntityView;
import net.runeduniverse.tools.maven.r4m.grm.view.api.GoalView;

public class GoalCheck extends DefaultCheck {

	protected String goal = null;

	public String getGoal() {
		return this.goal;
	}

	public void setGoal(String goalId) {
		this.goal = goalId;
	}

	@Override
	public boolean isValid() {
		return this.goal != null;
	}

	@Override
	protected DataCheck<EntityView> check() {
		return and(nonNull(), goal(and(nonNull(), this::eval)));
	}

	protected boolean eval(GoalView data) {
		return this.goal.equals(data.getGoalId());
	}

	@Override
	public List<ConditionInfo> getInfo() {
		final List<ConditionInfo> lst = new LinkedList<>();
		lst.add(new DefaultConditionInfo("goalId", this.goal));
		return lst;
	}
}