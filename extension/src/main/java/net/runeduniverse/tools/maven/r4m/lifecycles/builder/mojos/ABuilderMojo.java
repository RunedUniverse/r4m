package net.runeduniverse.tools.maven.r4m.lifecycles.builder.mojos;

import org.apache.maven.plugin.AbstractMojo;
import org.codehaus.plexus.component.annotations.Requirement;

import net.runeduniverse.tools.maven.r4m.lifecycles.builder.BuilderRuntime;
import net.runeduniverse.tools.maven.r4m.lifecycles.builder.ReferenceMap;

public abstract class ABuilderMojo extends AbstractMojo {

	@Requirement(role = BuilderRuntime.class)
	protected BuilderRuntime runtime;
	
	@Requirement(role = ReferenceMap.class)
	protected ReferenceMap references;
}