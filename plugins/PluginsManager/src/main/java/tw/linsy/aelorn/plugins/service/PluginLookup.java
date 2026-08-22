package tw.linsy.aelorn.plugins.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.model.PluginRef;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;

/**
 * Turning what an admin typed into one plugin.
 *
 * Its own service because three commands need the same three-stage match and the
 * same set of names per plugin, and because the answer "that matched four plugins"
 * has to survive back to the caller rather than becoming "not found".
 *
 * <p>Matching considers the plugin's declared name <em>and</em> everything it
 * {@code provides}, so a plugin registered under an alias by another plugin is
 * still reachable by the name people actually use for it.
 */
public final class PluginLookup {

    /** Order matters: a name that matches exactly must never lose to a prefix. */
    private enum Match {
        EXACT,
        PREFIX,
        CONTAINS
    }

    /**
     * Resolves a typed name.
     *
     * @return a {@link PluginRef} that is resolved, ambiguous with candidates, or
     *         missing
     */
    public PluginRef resolve(@Nullable String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return PluginRef.missing();
        }
        List<Plugin> loaded = List.of(Bukkit.getPluginManager().getPlugins());

        for (Plugin plugin : loaded) {
            if (matches(plugin, needle, Match.EXACT)) {
                return PluginRef.found(plugin);
            }
        }
        for (Match stage : List.of(Match.PREFIX, Match.CONTAINS)) {
            List<Plugin> hits = new ArrayList<>();
            for (Plugin plugin : loaded) {
                if (matches(plugin, needle, stage)) {
                    hits.add(plugin);
                }
            }
            if (hits.size() == 1) {
                return PluginRef.found(hits.get(0));
            }
            if (hits.size() > 1) {
                return PluginRef.ambiguous(namesOf(hits));
            }
        }
        return PluginRef.missing();
    }

    /**
     * Turns a failed resolution into the reply that explains it.
     *
     * Lives here rather than in each caller because the three outcomes have to stay
     * distinguishable: three services previously carried their own copy of this, and
     * a copy that collapses "matched four plugins" into "not found" sends an admin
     * looking for a typo that is not there.
     */
    public static Reply unresolved(MessageCatalog messages, PluginRef ref, @Nullable String query) {
        if (ref.ambiguous()) {
            return Reply.fail(messages.raw("lookup.ambiguous",
                "candidates", Texts.join(messages, ref.candidates())));
        }
        String typed = query == null ? "" : query.trim();
        return Reply.fail(typed.isEmpty()
            ? messages.raw("common.missing-plugin")
            : messages.raw("lookup.not-found", "query", typed));
    }

    /** Loaded plugin names starting with {@code partial}, for tab completion. */
    public List<String> suggest(@Nullable String partial) {
        String needle = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.getName().toLowerCase(Locale.ROOT).startsWith(needle)) {
                names.add(plugin.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Every loaded plugin's name, lower-cased; used to mark scan entries loaded. */
    public Set<String> loadedNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            names.add(plugin.getName().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    /** All plugins, sorted by name, for listings. */
    public List<Plugin> allSorted() {
        List<Plugin> plugins = new ArrayList<>(List.of(Bukkit.getPluginManager().getPlugins()));
        plugins.sort(Comparator.comparing(Plugin::getName, String.CASE_INSENSITIVE_ORDER));
        return plugins;
    }

    /** The plugin's declared name plus every name it provides. */
    public static Set<String> namesFor(Plugin plugin) {
        Set<String> names = new LinkedHashSet<>();
        names.add(plugin.getName());
        names.addAll(plugin.getPluginMeta().getProvidedPlugins());
        return names;
    }

    public static List<String> namesOf(List<Plugin> plugins) {
        List<String> names = new ArrayList<>(plugins.size());
        for (Plugin plugin : plugins) {
            names.add(plugin.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * The jar a loaded plugin came from, derived from its class loader.
     *
     * The previous version reflected {@code JavaPlugin.getFile()}, a protected
     * method it had no access to. Asking the class loader for the descriptor it
     * loaded and reading the jar out of the resulting {@code jar:file:...!/...}
     * URL needs no reflection and works for both descriptor kinds — including
     * {@code paper-plugin.yml} plugins, which the reflective path silently failed
     * on.
     *
     * @return the jar path, or {@code null} for a plugin with no jar (a provided
     *         plugin, or one loaded from a directory)
     */
    public static @Nullable Path jarOf(Plugin plugin) {
        ClassLoader loader = plugin.getClass().getClassLoader();
        if (loader == null) {
            return null;
        }
        for (String descriptor : List.of("paper-plugin.yml", "plugin.yml")) {
            URL resource = loader.getResource(descriptor);
            Path jar = jarFromUrl(resource);
            if (jar != null) {
                return jar;
            }
        }
        return null;
    }

    /**
     * {@code jar:file:/path/to/Foo.jar!/plugin.yml} to {@code /path/to/Foo.jar}.
     *
     * Goes through {@link java.net.URI} rather than {@code new URL(...)}: the URL
     * constructors are deprecated for removal, and {@code Paths.get(URI)} is also
     * what decodes the percent-escapes a server directory with spaces or non-ASCII
     * characters in its name produces.
     */
    private static @Nullable Path jarFromUrl(@Nullable URL resource) {
        if (resource == null || !"jar".equals(resource.getProtocol())) {
            return null;
        }
        String spec = resource.getPath();
        int separator = spec.indexOf("!/");
        if (separator < 0) {
            return null;
        }
        try {
            return Paths.get(new URI(spec.substring(0, separator)));
        } catch (URISyntaxException | IllegalArgumentException | java.nio.file.FileSystemNotFoundException unusable) {
            return null;
        }
    }

    private boolean matches(Plugin plugin, String needle, Match stage) {
        for (String name : namesFor(plugin)) {
            String candidate = name.toLowerCase(Locale.ROOT);
            boolean hit = switch (stage) {
                case EXACT -> candidate.equals(needle);
                case PREFIX -> candidate.startsWith(needle);
                case CONTAINS -> candidate.contains(needle);
            };
            if (hit) {
                return true;
            }
        }
        return false;
    }
}
