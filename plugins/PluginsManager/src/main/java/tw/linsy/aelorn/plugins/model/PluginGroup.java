package tw.linsy.aelorn.plugins.model;

import java.util.List;

/**
 * A named set of plugins from {@code groups.yml}, so an admin can act on
 * "content" or "chat" rather than typing eight names.
 *
 * @param name        the group key, matched case-insensitively and with an
 *                    optional {@code @} prefix at the command layer
 * @param description shown in listings; may be blank
 * @param locked      when true the group refuses batch disable, reload and
 *                    unload, and every member is treated as protected. Named
 *                    {@code locked} rather than {@code protected} because the
 *                    latter is a Java keyword and the previous version had to
 *                    call its accessor {@code protectedGroup()} to compile
 * @param plugins     member names, in the order the admin wrote them; batch
 *                    enable follows that order and batch disable reverses it
 */
public record PluginGroup(String name, String description, boolean locked, List<String> plugins) {

    public PluginGroup(String name, String description, boolean locked, List<String> plugins) {
        this.name = name;
        this.description = description;
        this.locked = locked;
        this.plugins = List.copyOf(plugins);
    }

    /** Members in the order a shutdown should use: dependants before dependencies. */
    public List<String> reversedPlugins() {
        List<String> reversed = new java.util.ArrayList<>(plugins);
        java.util.Collections.reverse(reversed);
        return reversed;
    }
}
