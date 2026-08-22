package tw.linsy.aelorn.plugins.model;

import org.jetbrains.annotations.Nullable;

/**
 * What a jar claims about itself, read straight from its descriptor.
 *
 * Both descriptor shapes are represented by one record because every consumer
 * asks the same four questions regardless of which file answered them. Which
 * file it was survives in {@link #source} for the scan report, since a plugin
 * shipping {@code paper-plugin.yml} cannot be loaded by the legacy path and an
 * admin looking at a failure needs to know that.
 *
 * @param name           declared plugin name, or {@code null} when the jar omits it
 * @param version        declared version, or {@code null}
 * @param main           main class, or {@code null}
 * @param apiVersion     declared {@code api-version}, or {@code null}
 * @param foliaSupported whether the jar declares {@code folia-supported: true}
 * @param source         which descriptor file this came from
 */
public record JarDescriptor(@Nullable String name,
                            @Nullable String version,
                            @Nullable String main,
                            @Nullable String apiVersion,
                            boolean foliaSupported,
                            Source source) {

    /** Which descriptor file inside the jar was read. */
    public enum Source {
        /** {@code plugin.yml} — parsed by the server's own descriptor reader. */
        BUKKIT("plugin.yml"),
        /** {@code paper-plugin.yml} — no public parser exists, so top-level keys only. */
        PAPER("paper-plugin.yml");

        private final String fileName;

        Source(String fileName) {
            this.fileName = fileName;
        }

        public String fileName() {
            return fileName;
        }
    }

    /** True when the jar named itself, which is the minimum for loading it. */
    public boolean hasName() {
        return name != null && !name.isBlank();
    }
}
