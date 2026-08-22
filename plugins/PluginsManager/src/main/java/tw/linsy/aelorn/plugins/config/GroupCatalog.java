package tw.linsy.aelorn.plugins.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.model.PluginGroup;

/**
 * {@code groups.yml}, parsed once.
 *
 * Resolution is deliberately the same three-stage match used for plugin names —
 * exact, then unique prefix, then unique substring — so {@code @cont} finds
 * {@code content} and {@code @c} reports the ambiguity instead of guessing.
 */
public record GroupCatalog(List<PluginGroup> groups) {

    public GroupCatalog {
        groups = List.copyOf(groups);
    }

    static GroupCatalog from(FileConfiguration file) {
        ConfigurationSection root = file.getConfigurationSection("groups");
        if (root == null) {
            return new GroupCatalog(List.of());
        }
        List<PluginGroup> parsed = new ArrayList<>();
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            parsed.add(new PluginGroup(
                name,
                section.getString("description", ""),
                // "protected" in YAML, "locked" in Java: the YAML word is the one
                // admins already have in their files, and the Java word cannot be
                // a keyword.
                section.getBoolean("protected", false),
                section.getStringList("plugins")));
        }
        return new GroupCatalog(parsed);
    }

    /**
     * @param query group name, with or without a leading {@code @}
     * @return the single match, or {@code null} when nothing or more than one
     *         matches — the caller reports which, since it has the message keys
     */
    public @Nullable PluginGroup find(@Nullable String query) {
        String needle = normalise(query);
        if (needle.isEmpty()) {
            return null;
        }
        for (PluginGroup group : groups) {
            if (group.name().equalsIgnoreCase(needle)) {
                return group;
            }
        }
        PluginGroup byPrefix = unique(needle, true);
        return byPrefix != null ? byPrefix : unique(needle, false);
    }

    private @Nullable PluginGroup unique(String needle, boolean prefix) {
        PluginGroup found = null;
        for (PluginGroup group : groups) {
            String name = group.name().toLowerCase(Locale.ROOT);
            boolean hit = prefix ? name.startsWith(needle) : name.contains(needle);
            if (hit) {
                if (found != null) {
                    return null;
                }
                found = group;
            }
        }
        return found;
    }

    /** Names matching a partial input, for tab completion, each with its {@code @}. */
    public List<String> suggest(@Nullable String partial) {
        String needle = normalise(partial);
        List<String> names = new ArrayList<>();
        for (PluginGroup group : groups) {
            if (group.name().toLowerCase(Locale.ROOT).startsWith(needle)) {
                names.add("@" + group.name());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Members of every locked group, lower-cased; these are protected implicitly. */
    public Set<String> lockedMembers() {
        Set<String> members = new LinkedHashSet<>();
        for (PluginGroup group : groups) {
            if (!group.locked()) {
                continue;
            }
            for (String plugin : group.plugins()) {
                if (plugin != null && !plugin.isBlank()) {
                    members.add(plugin.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return members;
    }

    private static String normalise(@Nullable String query) {
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return text.startsWith("@") ? text.substring(1) : text;
    }
}
