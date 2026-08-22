package tw.linsy.aelorn.plugins.model;

import java.util.List;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * The outcome of turning an admin's typed name into one plugin.
 *
 * Three outcomes, not two: an ambiguous prefix is a different problem from a
 * missing plugin and needs a different message, and collapsing them into
 * {@code null} was how the previous version ended up saying "not found" for a
 * name that matched four plugins.
 *
 * @param plugin     the single match, or {@code null} when there is not exactly one
 * @param candidates the names that matched, for the ambiguous message; empty
 *                   when nothing matched at all
 */
public record PluginRef(@Nullable Plugin plugin, List<String> candidates) {

    public PluginRef(@Nullable Plugin plugin, List<String> candidates) {
        this.plugin = plugin;
        this.candidates = List.copyOf(candidates);
    }

    public static PluginRef found(Plugin plugin) {
        return new PluginRef(plugin, List.of());
    }

    public static PluginRef ambiguous(List<String> candidates) {
        return new PluginRef(null, candidates);
    }

    public static PluginRef missing() {
        return new PluginRef(null, List.of());
    }

    public boolean resolved() {
        return plugin != null;
    }

    public boolean ambiguous() {
        return plugin == null && !candidates.isEmpty();
    }

    /** The resolved plugin; only call after {@link #resolved()}. */
    public Plugin require() {
        if (plugin == null) {
            throw new IllegalStateException("PluginRef 未解析成功，呼叫端應先檢查 resolved()。");
        }
        return plugin;
    }
}
