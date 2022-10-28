package net.runeduniverse.tools.runes4tools.maven.runes4maven.lifecycles.inject.internal.filter;

import org.apache.maven.model.Plugin;

/**
 * Check if the MavenPlugin meets the filter
 * 
 * @author Pl4yingNight
 *
 */
@FunctionalInterface
public interface MvnPluginFilter {
	public boolean apply(org.apache.maven.model.Plugin plugin);

	default MvnPluginFilter and(MvnPluginFilter filter) {
		return new And(this, filter);
	}

	default MvnPluginFilter or(MvnPluginFilter filter) {
		return new Or(this, filter);
	}

	public static class And implements MvnPluginFilter {
		private final MvnPluginFilter a;
		private final MvnPluginFilter b;

		protected And(MvnPluginFilter a, MvnPluginFilter b) {
			this.a = a;
			this.b = b;
		}

		@Override
		public boolean apply(Plugin plugin) {
			return this.a.apply(plugin) && this.b.apply(plugin);
		}

	}

	public static class Or implements MvnPluginFilter {
		private final MvnPluginFilter a;
		private final MvnPluginFilter b;

		protected Or(MvnPluginFilter a, MvnPluginFilter b) {
			this.a = a;
			this.b = b;
		}

		@Override
		public boolean apply(Plugin plugin) {
			return this.a.apply(plugin) || this.b.apply(plugin);
		}

	}
}