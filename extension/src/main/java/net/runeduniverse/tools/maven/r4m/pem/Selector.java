package net.runeduniverse.tools.maven.r4m.pem;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.MojoNotFoundException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import net.runeduniverse.tools.maven.r4m.api.pem.ExecutionArchive;
import net.runeduniverse.tools.maven.r4m.api.pem.ExecutionArchiveSelection;
import net.runeduniverse.tools.maven.r4m.api.pem.ExecutionArchiveSelector;
import net.runeduniverse.tools.maven.r4m.api.pem.ExecutionArchiveSelectorConfig;
import net.runeduniverse.tools.maven.r4m.api.pem.ExecutionArchiveSlice;
import net.runeduniverse.tools.maven.r4m.api.pem.model.Execution;
import net.runeduniverse.tools.maven.r4m.api.pem.model.ExecutionSource;
import net.runeduniverse.tools.maven.r4m.api.pem.model.Goal;
import net.runeduniverse.tools.maven.r4m.api.pem.model.Lifecycle;
import net.runeduniverse.tools.maven.r4m.api.pem.model.Phase;
import net.runeduniverse.tools.maven.r4m.api.pem.model.Trigger;
import net.runeduniverse.tools.maven.r4m.api.pem.view.ExecutionView;
import net.runeduniverse.tools.maven.r4m.api.pem.view.GoalView;
import net.runeduniverse.tools.maven.r4m.api.pem.view.LifecycleView;
import net.runeduniverse.tools.maven.r4m.api.pem.view.PhaseView;
import net.runeduniverse.tools.maven.r4m.pem.view.ViewFactory;

@Component(role = ExecutionArchiveSelector.class, hint = "default")
public class Selector implements ExecutionArchiveSelector {

	@Requirement
	private MavenSession mvnSession;
	@Requirement
	private MavenPluginManager pluginManager;
	@Requirement
	private ExecutionArchive archive;

	protected Set<Execution> filterSlice(final ExecutionArchiveSelectorConfig cnf, ExecutionArchiveSlice slice) {
		return slice.getExecutions(execution -> {
			// the use of never-/always-active flags is discouraged and included for
			// debugging purposes
			if (execution.isNeverActive())
				return false;
			if (execution.isAlwaysActive())
				return true;
			// if a trigger for a packaging-procedure is set it must match!
			if (!execution.getPackagingProcedures()
					.isEmpty()) {
				if (!execution.getPackagingProcedures()
						.contains(cnf.getPackagingProcedure()))
					return false;
			}
			// if an active-execution is defined it must match
			// if not the default-active flag is checked
			if (cnf.getActiveExecution() == null) {
				if (execution.isDefaultActive())
					return true;
			} else if (execution.getId()
					.equals(cnf.getActiveExecution()))
				return true;
			// any active trigger activates the execution
			for (Trigger trigger : execution.getTrigger())
				if (trigger.isActive(cnf))
					return true;
			return false;
		});
	}

	protected boolean validateGoal(final ExecutionArchiveSelectorConfig cnf, Goal goal) {
		for (String mode : cnf.getModes())
			if (goal.getModes()
					.contains(mode))
				return true;
		return false;
	}

	protected void acquireMojoDescriptor(final ExecutionArchiveSelectorConfig cnf, GoalView goalView) {
		Plugin plugin = cnf.getActiveProject()
				.getPlugin(goalView.getGroupId() + ":" + goalView.getArtifactId());

		MojoDescriptor descriptor = null;
		try {
			descriptor = pluginManager.getMojoDescriptor(plugin, goalView.getGoalId(), cnf.getActiveProject()
					.getRemotePluginRepositories(), this.mvnSession.getRepositorySession());
		} catch (MojoNotFoundException | PluginResolutionException | PluginDescriptorParsingException
				| InvalidPluginDescriptorException e) {
			System.err.println("Failed to acquire MojoDescriptor!");
			e.printStackTrace();
		}
		goalView.setDescriptor(descriptor);
	}

	protected Map<String, Map<ExecutionSource, ExecutionView>> aggregate(final ExecutionArchiveSelectorConfig cnf,
			Collection<Execution> executions) {
		Map<String, Map<ExecutionSource, ExecutionView>> map = new LinkedHashMap<>();

		for (Execution execution : executions) {
			Map<ExecutionSource, ExecutionView> entry = map.get(execution.getId());
			if (entry == null) {
				entry = new LinkedHashMap<>();
				map.put(execution.getId(), entry);
			}
			ExecutionView executionView = entry.get(execution.getSource());
			if (executionView == null) {
				executionView = ViewFactory.createExecution(execution.getId());
				entry.put(execution.getSource(), executionView);
			}
			for (Lifecycle lifecycle : execution.getLifecycles()
					.values()) {
				LifecycleView lifecycleView = executionView.getLifecycle(lifecycle.getId());
				if (lifecycleView == null) {
					lifecycleView = ViewFactory.createLifecycle(lifecycle.getId());
					executionView.put(lifecycleView);
				}
				for (Phase phase : lifecycle.getPhases()
						.values()) {
					PhaseView phaseView = lifecycleView.getPhase(phase.getId());
					if (phaseView == null) {
						phaseView = ViewFactory.createPhase(phase.getId());
						lifecycleView.put(phaseView);
					}
					for (Goal goal : phase.getGoals())
						if (this.validateGoal(cnf, goal)) {
							GoalView goalView = ViewFactory.createGoal(goal.getGroupId(), goal.getArtifactId(),
									goal.getGoalId());
							goalView.addModes(goal.getModes());
							goalView.setFork(goal.getFork());
							acquireMojoDescriptor(cnf, goalView);
							if (goalView.getDescriptor() != null)
								phaseView.addGoal(goalView);
						}
				}
			}
		}
		return map;
	}

	protected ExecutionView merge(final ExecutionArchiveSelectorConfig cnf, final ExecutionView base,
			final ExecutionView dominant, boolean lifecycleOverride, boolean goalOverride) {
		for (LifecycleView domLifecycle : dominant.getLifecycles()
				.values()) {
			LifecycleView baseLifecycle = base.getLifecycles()
					.get(domLifecycle.getId());
			if (lifecycleOverride || baseLifecycle == null) {
				base.put(domLifecycle);
				continue;
			}

			// remove all dominant goals from base phases, which can only be executed once
			Map<GoalView, PhaseView> basePhaseIndex = new HashMap<>();
			for (PhaseView basePhase : baseLifecycle.getPhases()
					.values())
				for (GoalView goal : basePhase.getGoals())
					basePhaseIndex.put(goal, basePhase);

			for (PhaseView domPhase : domLifecycle.getPhases()
					.values())
				for (GoalView domGoal : domPhase.getGoals())
					if (goalOverride || !domGoal.getDescriptor()
							.alwaysExecute())
						for (GoalView baseGoal : basePhaseIndex.keySet()) {
							if (domGoal.equals(baseGoal))
								basePhaseIndex.get(baseGoal)
										.removeGoal(baseGoal);
						}

			// push all dominant phases and goals into base lifecycles
			for (PhaseView domPhase : domLifecycle.getPhases()
					.values()) {
				PhaseView basePhase = baseLifecycle.getPhases()
						.get(domPhase.getId());
				if (basePhase == null) {
					baseLifecycle.put(domPhase);
					continue;
				}
				basePhase.addAllGoals(domPhase.getGoals());
			}
		}
		return base;
	}

	@SuppressWarnings("deprecation")
	protected Map<String, Map<ExecutionSource, ExecutionView>> getExecutions(final ExecutionArchiveSelectorConfig cnf,
			ExecutionArchiveSlice slice) {
		Map<String, Map<ExecutionSource, ExecutionView>> baseViews;
		if (slice.getParent() == null)
			baseViews = new LinkedHashMap<>();
		else
			baseViews = getExecutions(cnf, slice.getParent());

		Set<Execution> applyableExecutions = filterSlice(cnf, slice);
		Map<String, Map<ExecutionSource, ExecutionView>> dominantViews = aggregate(cnf, applyableExecutions);

		for (String executionId : dominantViews.keySet()) {
			Map<ExecutionSource, ExecutionView> baseEntries = baseViews.get(executionId);
			Map<ExecutionSource, ExecutionView> domEntries = dominantViews.get(executionId);
			if (baseEntries == null) {
				baseViews.put(executionId, domEntries);
				continue;
			}
			baseEntries.remove(ExecutionSource.EFFECTIVE);
			for (ExecutionSource source : domEntries.keySet()) {
				ExecutionView baseExecution = baseEntries.get(source);
				ExecutionView domExecution = domEntries.get(source);
				if (baseExecution == null || ExecutionSource.EFFECTIVE.equals(source)) {
					baseEntries.put(source, domExecution);
					continue;
				}
				baseEntries.put(source,
						merge(cnf, baseExecution, domExecution, ExecutionSource.OVERRIDE.equals(source), false));
			}
		}

		return baseViews;
	}

	@SuppressWarnings("deprecation")
	protected ExecutionView reduce(final ExecutionArchiveSelectorConfig cnf, String id,
			Map<ExecutionSource, ExecutionView> views) {
		ExecutionView execution = views.get(ExecutionSource.EFFECTIVE);
		if (execution != null)
			return execution;
		ExecutionView pluginView = views.getOrDefault(ExecutionSource.PLUGIN, ViewFactory.createExecution(id));
		ExecutionView packagingView = views.getOrDefault(ExecutionSource.PACKAGING, ViewFactory.createExecution(id));
		ExecutionView overrideView = views.getOrDefault(ExecutionSource.OVERRIDE, ViewFactory.createExecution(id));

		execution = merge(cnf, pluginView, packagingView, false, true);
		execution = merge(cnf, execution, overrideView, true, false);

		return execution;
	}

	@Override
	public ExecutionArchiveSelection compileSelection(final ExecutionArchiveSelectorConfig cnf) {
		Set<ExecutionView> views = new LinkedHashSet<>();
		if (cnf.getActiveProject() == null)
			return new Selection(views);

		ExecutionArchiveSlice slice = this.archive.getSlice(cnf.getActiveProject());
		if (slice == null)
			return new Selection(views);

		cnf.compile(this.mvnSession);
		for (Entry<String, Map<ExecutionSource, ExecutionView>> entry : getExecutions(cnf, slice).entrySet())
			views.add(reduce(cnf, entry.getKey(), entry.getValue()));
		return new Selection(views);
	}

	@Override
	public ExecutionArchiveSelectorConfig createConfig() {
		return new SelectorConfig();
	}
}
