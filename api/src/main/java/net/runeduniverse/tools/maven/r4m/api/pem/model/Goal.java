package net.runeduniverse.tools.maven.r4m.api.pem.model;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import net.runeduniverse.lib.utils.logging.logs.CompoundTree;
import net.runeduniverse.tools.maven.r4m.api.pem.Recordable;

public class Goal implements Recordable {
	private String groupId;
	private String artifactId;
	private String goalId;
	private Fork fork = null;
	private Set<String> modes = new LinkedHashSet<>();

	public Goal() {
	}

	public Goal(String mvnGoalKey) {
		parseMvnGoalKey(mvnGoalKey);
	}

	public Goal(String groupId, String artifactId, String goalId) {
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.goalId = goalId;
	}

	public String getGroupId() {
		return this.groupId;
	}

	public String getArtifactId() {
		return this.artifactId;
	}

	public String getGoalId() {
		return this.goalId;
	}

	public Set<String> getModes() {
		return this.modes;
	}

	public boolean hasFork() {
		return this.fork != null;
	}

	public Fork getFork() {
		return this.fork;
	}

	public Goal addModes(String... modes) {
		for (int i = 0; i < modes.length; i++)
			this.modes.add(modes[i]);
		return this;
	}

	public Goal addModes(Collection<String> modes) {
		this.modes.addAll(modes);
		return this;
	}

	public Goal setFork(Fork fork) {
		this.fork = fork;
		return this;
	}

	public boolean parseMvnGoalKey(String mvnGoalKey) {
		String[] keyValues = mvnGoalKey.split(":");

		switch (keyValues.length) {
		case 2:
			// prefix:goal
			// prefix
			this.goalId = keyValues[1];
			return true;
		case 3:
			// groupId:artifactId:goal
			this.groupId = keyValues[0];
			this.artifactId = keyValues[1];
			this.goalId = keyValues[2];
			return true;
		case 4:
			// groupId:artifactId:version:goal
			this.groupId = keyValues[0];
			this.artifactId = keyValues[1];
			// version
			this.goalId = keyValues[3];
			return true;
		default:
			return false;
		}

	}

	@Override
	public CompoundTree toRecord() {
		CompoundTree tree = new CompoundTree("Goal");

		tree.append("groupId", this.groupId)
				.append("artifactId", this.artifactId)
				.append("goalId", this.goalId);

		tree.append("modes", '[' + String.join(", ", this.modes) + ']');

		if (this.fork != null)
			tree.append(this.fork.toRecord());

		return tree;
	}
}
