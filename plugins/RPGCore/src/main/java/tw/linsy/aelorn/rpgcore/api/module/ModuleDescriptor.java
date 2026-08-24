package tw.linsy.aelorn.rpgcore.api.module;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Stable identity and dependency metadata for one independently managed RPGCore extension. */
public record ModuleDescriptor(
        String id,
        String version,
        Set<String> requiredDependencies,
        Set<String> optionalDependencies,
        int priority) {

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,31}");
    private static final Pattern VALUE = Pattern.compile("[a-z0-9][a-z0-9_./-]{0,63}");

    public ModuleDescriptor {
        id = canonicalId(id);
        version = Objects.requireNonNull(version, "version").strip();
        if (version.isEmpty()) {
            throw new IllegalArgumentException("Module version must not be blank");
        }
        TreeSet<String> normalizedDependencies = new TreeSet<>();
        if (requiredDependencies != null) {
            for (String dependency : requiredDependencies) {
                normalizedDependencies.add(canonicalId(dependency));
            }
        }
        if (normalizedDependencies.contains(id)) {
            throw new IllegalArgumentException("Module " + id + " cannot depend on itself");
        }
        requiredDependencies = Collections.unmodifiableSet(new LinkedHashSet<>(normalizedDependencies));

        TreeSet<String> normalizedOptional = new TreeSet<>();
        if (optionalDependencies != null) {
            for (String dependency : optionalDependencies) {
                normalizedOptional.add(canonicalId(dependency));
            }
        }
        if (normalizedOptional.contains(id)) {
            throw new IllegalArgumentException("Module " + id + " cannot optionally depend on itself");
        }
        TreeSet<String> overlap = new TreeSet<>(normalizedOptional);
        overlap.retainAll(normalizedDependencies);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("Required and optional dependencies overlap: "
                    + String.join(",", overlap));
        }
        optionalDependencies = Collections.unmodifiableSet(new LinkedHashSet<>(normalizedOptional));
    }

    public static ModuleDescriptor of(String id, String version) {
        return new ModuleDescriptor(id, version, Set.of(), Set.of(), 0);
    }

    public ModuleDescriptor(String id, String version, Set<String> requiredDependencies, int priority) {
        this(id, version, requiredDependencies, Set.of(), priority);
    }

    /**
     * Returns a stable namespaced id. Legacy simple ids are owned by {@code rpgcore:} so they
     * remain deterministic; external modules should declare an explicit {@code vendor:module} id.
     */
    public static String canonicalId(String value) {
        Objects.requireNonNull(value, "id");
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "rpgcore:" + normalized;
        }
        int separator = normalized.indexOf(':');
        if (separator <= 0 || separator != normalized.lastIndexOf(':')) {
            throw new IllegalArgumentException("Module id must be namespace:value but was " + value);
        }
        String namespace = normalized.substring(0, separator);
        String path = normalized.substring(separator + 1);
        if (!NAMESPACE.matcher(namespace).matches() || !VALUE.matcher(path).matches()) {
            throw new IllegalArgumentException("Module id must match " + NAMESPACE.pattern() + ":"
                    + VALUE.pattern() + " but was " + value);
        }
        return normalized;
    }
}
