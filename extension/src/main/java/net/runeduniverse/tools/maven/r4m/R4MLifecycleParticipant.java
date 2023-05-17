package net.runeduniverse.tools.maven.r4m;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.eventspy.internal.EventSpyDispatcher;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.internal.DefaultLifecycleExecutionPlanCalculator;
import org.apache.maven.lifecycle.internal.LifecycleExecutionPlanCalculator;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginContainer;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import net.runeduniverse.tools.maven.r4m.eventspy.api.MavenPluginPatchingEvent;
import net.runeduniverse.tools.maven.r4m.eventspy.api.MessagePatchingEvent;
import net.runeduniverse.tools.maven.r4m.eventspy.api.PatchingEvent;
import net.runeduniverse.tools.maven.r4m.eventspy.api.PatchingEvent.Type;
import net.runeduniverse.tools.maven.r4m.pem.api.ExecutionArchive;
import net.runeduniverse.tools.maven.r4m.pem.api.ExecutionArchiveSlice;
import net.runeduniverse.tools.maven.r4m.pem.api.ProjectExecutionModelConfigParser;
import net.runeduniverse.tools.maven.r4m.pem.api.ProjectExecutionModelPackagingParser;
import net.runeduniverse.tools.maven.r4m.pem.api.ProjectExecutionModelPluginParser;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = Properties.R4M_LIFECYCLE_PARTICIPANT_HINT)
public class R4MLifecycleParticipant extends AbstractMavenLifecycleParticipant {

	public static final String ERR_FAILED_TO_LOAD_MAVEN_EXTENSION_CLASSREALM = //
			"Failed to load maven-extension ClassRealm";
	public static final String ERR_FAILED_TO_LOAD_PEM = //
			"Failed while loading pem.xml from project";

	public static final String PLEXUS_DEFAULT_MAVEN_HINT = "maven-default";

	@Requirement
	private EventSpyDispatcher dispatcher;
	@Requirement
	private ExecutionArchive archive;
	@Requirement(role = ProjectExecutionModelConfigParser.class)
	private Map<String, ProjectExecutionModelConfigParser> pemConfigParser;
	@Requirement(role = ProjectExecutionModelPluginParser.class)
	private Map<String, ProjectExecutionModelPluginParser> pemPluginParser;
	@Requirement(role = ProjectExecutionModelPackagingParser.class)
	private Map<String, ProjectExecutionModelPackagingParser> pemPackagingParser;
	@Requirement
	private PlexusContainer container;
	@Requirement
	private MavenPluginManager mavenPluginManager;
	@Requirement
	private PluginDependenciesResolver pluginDependenciesResolver;

	private final Set<Plugin> unidentifiablePlugins = new LinkedHashSet<>();

	private boolean coreExtension = false;

	/**
	 * Invoked after MavenSession instance has been created.
	 *
	 * This callback is intended to allow extensions to inject execution properties,
	 * activate profiles and perform similar tasks that affect MavenProject instance
	 * construction.
	 */
	public void afterSessionStart(MavenSession mvnSession) throws MavenExecutionException {
		this.coreExtension = true;

		mvnSession.getSettings()
				.addPluginGroup(Properties.GROUP_ID);
	}

	/**
	 * Invoked after all MavenProject instances have been created.
	 *
	 * This callback is intended to allow extensions to manipulate MavenProjects
	 * before they are sorted and actual build execution starts.
	 */
	public void afterProjectsRead(MavenSession mvnSession) throws MavenExecutionException {
		this.dispatcher.onEvent(PatchingEvent.createInfoEvent(Type.INFO_PATCHING_STARTED));

		ClassRealm currentRealm = (ClassRealm) Thread.currentThread()
				.getContextClassLoader();
		ClassWorld world = currentRealm.getWorld();

		try {
			ClassRealm realm;
			if (this.coreExtension) {
				this.dispatcher.onEvent(PatchingEvent.createInfoEvent(Type.INFO_ELEVATING_TO_CORE_REALM));
				realm = world.getRealm("maven.ext");
			} else {
				this.dispatcher.onEvent(PatchingEvent.createInfoEvent(Type.INFO_ELEVATING_TO_BUILD_REALM));
				// we need to reinitiate the r4m-maven-extension realm because maven injects an
				// outdated version of the plexus-utils
				realm = world.getRealm("plexus.core")
						.createChildRealm("extension>net.runeduniverse.tools.maven.r4m:r4m-maven-extension");
				realm.importFrom(currentRealm, "net.runeduniverse.tools.maven.r4m.pem.api");
				realm.importFrom(currentRealm, "net.runeduniverse.tools.maven.r4m.eventspy.api");
				realm.importFrom(currentRealm, "net.runeduniverse.tools.maven.r4m.pem.model");
				realm.importFrom(currentRealm, "net.runeduniverse.tools.maven.r4m.pem.parser");
				realm.importFrom(currentRealm, "net.runeduniverse.tools.maven.r4m.pem.parser.trigger");
				realm.importFrom(currentRealm, "net.runeduniverse.tools.maven.r4m.pem.parser.restrictions");
				realm.importFrom(currentRealm, "net.runeduniverse.tools.maven.r4m.pem.writer");
				realm.importFrom(currentRealm, "net.runeduniverse.tools.maven.r4m.pem.writer.trigger");
				realm.importFrom(currentRealm, "net.runeduniverse.tools.maven.r4m.pem.writer.restrictions");
				realm.importFrom(currentRealm, "net.runeduniverse.lib.utils.logging.logs");
			}

			Thread.currentThread()
					.setContextClassLoader(realm);

			Collection<Plugin> extPlugins = scanCoreExtensions(world.getRealms());
			for (MavenProject mvnProject : mvnSession.getAllProjects())
				scanProject(mvnSession, extPlugins, mvnProject);

			// collect indirectly referenced build-plugins after seeding the archive
			for (MavenProject mvnProject : mvnSession.getAllProjects())
				// enable dependent on property?
				loadReferencedPlugins(mvnSession, mvnProject);

			modifyLifecycleExecutionPlanCalculator();

		} catch (DuplicateRealmException | NoSuchRealmException e) {
			MavenExecutionException ex = new MavenExecutionException(ERR_FAILED_TO_LOAD_MAVEN_EXTENSION_CLASSREALM, e);
			this.dispatcher.onEvent(PatchingEvent.createErrorEvent(Type.INFO_PATCHING_ABORTED, ex));
			throw ex;
		} catch (Exception e) {
			MavenExecutionException ex = new MavenExecutionException(ERR_FAILED_TO_LOAD_PEM, e);
			this.dispatcher.onEvent(PatchingEvent.createErrorEvent(Type.INFO_PATCHING_ABORTED, ex));
			throw ex;
		} finally {
			this.dispatcher.onEvent(PatchingEvent.createInfoEvent(Type.INFO_RETURNING_TO_EXTENSION_REALM));
			Thread.currentThread()
					.setContextClassLoader(currentRealm);
		}

		if (!this.unidentifiablePlugins.isEmpty())
			this.dispatcher.onEvent(MavenPluginPatchingEvent.createInfoEvent(Type.WARN_UNIDENTIFIABLE_PLUGIN_DETECTED,
					this.unidentifiablePlugins));

		this.dispatcher.onEvent(PatchingEvent.createInfoEvent(Type.INFO_PATCHING_STOPPED));
	}

	private void scanProject(final MavenSession mvnSession, final Collection<Plugin> extPlugins,
			final MavenProject mvnProject) throws Exception {

		ExecutionArchiveSlice projectSlice = this.archive.getSlice(mvnProject);
		if (projectSlice == null)
			projectSlice = this.archive.createSlice(mvnProject);
		else
			return;

		mvnProject.getBuild()
				.getPlugins()
				.addAll(extPlugins);

		for (ProjectExecutionModelConfigParser parser : this.pemConfigParser.values())
			projectSlice.register(parser.parse(mvnProject));

		for (ProjectExecutionModelPluginParser parser : this.pemPluginParser.values())
			for (Plugin mvnPlugin : mvnProject.getBuildPlugins())
				if (isIdentifiable(mvnPlugin))
					try {
						projectSlice.register(parser.parse(mvnProject.getRemotePluginRepositories(),
								mvnSession.getRepositorySession(), mvnPlugin));
					} catch (PluginResolutionException e) {
						this.unidentifiablePlugins.add(mvnPlugin);
					}

		for (ProjectExecutionModelPackagingParser parser : this.pemPackagingParser.values())
			projectSlice.register(parser.parse());
	}

	private boolean isIdentifiable(Plugin mvnPlugin) {
		if (this.unidentifiablePlugins.contains(mvnPlugin))
			return false;

		if (mvnPlugin.getVersion() == null) {
			this.unidentifiablePlugins.add(mvnPlugin);
			return false;
		}

		return true;
	}

	private void loadReferencedPlugins(final MavenSession mvnSession, final MavenProject mvnProject) {
		PluginContainer plugins = mvnProject.getBuild();
		List<Artifact> knownArtifacts = new LinkedList<>();
		List<Plugin> knownPlugins = new LinkedList<>(plugins.getPlugins());
		List<Plugin> remainingPlugins = plugins.getPlugins();
		Map<String, Plugin> managedPlugins = mvnProject.getBuild()
				.getPluginManagement()
				.getPluginsAsMap();
		while (!remainingPlugins.isEmpty()) {
			List<Plugin> cache = new LinkedList<>();
			for (Plugin plugin : remainingPlugins)
				cache.addAll(discoverReferencedPlugins(mvnSession.getRepositorySession(), mvnProject, knownArtifacts,
						knownPlugins, managedPlugins, plugin));
			remainingPlugins = cache;
			mvnProject.getBuild()
					.getPlugins()
					.addAll(cache);
		}
	}

	private List<Plugin> discoverReferencedPlugins(final RepositorySystemSession repoSession,
			final MavenProject mvnProject, final List<Artifact> knownArtifacts, final List<Plugin> knownPlugins,
			final Map<String, Plugin> managedPlugins, final Plugin parentPlugin) {
		List<Plugin> referencedPlugins = new LinkedList<>();
		List<Artifact> artifacts = null;
		try {
			artifacts = resolvePluginArtifacts(parentPlugin, mvnProject.getRemotePluginRepositories(), repoSession);
		} catch (PluginResolutionException e) {
		}
		if (artifacts == null)
			return referencedPlugins;

		for (Artifact artifact : artifacts) {
			if (knownArtifacts.contains(artifact))
				continue;
			knownArtifacts.add(artifact);

			Plugin plugin = new Plugin();
			plugin.setGroupId(artifact.getGroupId());
			plugin.setArtifactId(artifact.getArtifactId());
			plugin.setVersion(artifact.getVersion());

			Plugin pluginInPom = managedPlugins.get(plugin.getKey());
			if (pluginInPom != null) {
				if (plugin.getVersion() == null)
					plugin.setVersion(pluginInPom.getVersion());
				plugin.setDependencies(new ArrayList<>(pluginInPom.getDependencies()));
			}

			if (knownPlugins.contains(plugin))
				continue;

			try {
				this.mavenPluginManager.getPluginDescriptor(plugin, mvnProject.getRemotePluginRepositories(),
						repoSession);
			} catch (PluginDescriptorParsingException | InvalidPluginDescriptorException
					| PluginResolutionException e) {
				// probably not a plugin ...
				continue;
			}

			knownPlugins.add(plugin);
			referencedPlugins.add(plugin);
		}
		return referencedPlugins;
	}

	private List<Artifact> resolvePluginArtifacts(Plugin extensionPlugin, List<RemoteRepository> repositories,
			RepositorySystemSession session) throws PluginResolutionException {
		DependencyNode root = pluginDependenciesResolver.resolve(extensionPlugin, null, null, repositories, session);
		PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
		root.accept(nlg);

		List<Artifact> artifacts = new ArrayList<>(nlg.getNodes()
				.size());
		RepositoryUtils.toArtifacts(artifacts, Collections.singleton(root), Collections.<String>emptyList(), null);
		for (Iterator<Artifact> it = artifacts.iterator(); it.hasNext();) {
			Artifact artifact = it.next();
			if (artifact.getFile() == null) {
				it.remove();
			}
		}
		return Collections.unmodifiableList(artifacts);
	}

	protected void modifyLifecycleExecutionPlanCalculator() {
		String defaultExecPlanCalcName = DefaultLifecycleExecutionPlanCalculator.class.getCanonicalName();
		DefaultLifecycleExecutionPlanCalculator defaultExecPlanCalc = null;

		Map<String, CharSequence> eventData = new LinkedHashMap<>();
		eventData.put("component", defaultExecPlanCalcName);
		eventData.put("role", LifecycleExecutionPlanCalculator.class.getCanonicalName());

		this.dispatcher.onEvent(PatchingEvent.createInfoEvent(Type.INFO_LIFECYCLE_EXEC_PLAN_CALC_STARTED));
		try {
			for (LifecycleExecutionPlanCalculator item : this.container
					.lookupList(LifecycleExecutionPlanCalculator.class))
				if (item instanceof DefaultLifecycleExecutionPlanCalculator) {
					defaultExecPlanCalc = (DefaultLifecycleExecutionPlanCalculator) item;
					break;
				}
		} catch (ComponentLookupException e) {
			this.dispatcher.onEvent(MessagePatchingEvent
					.createInfoEvent(Type.WARN_LIFECYCLE_EXEC_PLAN_CALC_FAILED_TO_LOCATE_PLEXUS_COMPONENT,
							eventData)
					.readonly());
		}
		if (defaultExecPlanCalc != null)
			try {
				this.container.release(defaultExecPlanCalc);
				this.container.addComponent(defaultExecPlanCalc, DefaultLifecycleExecutionPlanCalculator.class,
						PLEXUS_DEFAULT_MAVEN_HINT);
				eventData.put("role", defaultExecPlanCalcName);
				eventData.put("hint", PLEXUS_DEFAULT_MAVEN_HINT);
				this.dispatcher.onEvent(MessagePatchingEvent
						.createInfoEvent(
								Type.DEBUG_LIFECYCLE_EXEC_PLAN_CALC_UPDATING_PLEXUS_COMPONENT_DESCRIPTOR, eventData)
						.readonly());
			} catch (ComponentLifecycleException e) {
				this.dispatcher.onEvent(MessagePatchingEvent
						.createInfoEvent(Type.WARN_LIFECYCLE_EXEC_PLAN_CALC_FAILED_TO_RELEASE_PLEXUS_COMPONENT,
								eventData)
						.readonly());
			}
		this.dispatcher.onEvent(PatchingEvent.createInfoEvent(Type.INFO_LIFECYCLE_EXEC_PLAN_CALC_FINISHED));
	}

	private static Collection<Plugin> scanCoreExtensions(final Collection<ClassRealm> realms) {
		Collection<Plugin> extPlugins = new LinkedHashSet<Plugin>();
		for (ClassRealm realm : realms) {
			Plugin plugin = fromExtRealm(realm);
			if (plugin == null)
				continue;
			extPlugins.add(plugin);
		}
		return extPlugins;
	}

	private static Plugin fromExtRealm(ClassRealm realm) {
		String id = realm.getId();
		if (!id.startsWith("coreExtension>"))
			return null;
		Plugin plugin = new Plugin();
		plugin.setExtensions(true);
		id = id.substring(14);
		int idx = id.indexOf(':');
		plugin.setGroupId(id.substring(0, idx));
		id = id.substring(idx + 1);
		idx = id.indexOf(':');
		plugin.setArtifactId(id.substring(0, idx));
		plugin.setVersion(id.substring(idx + 1));
		return plugin;
	}

}
