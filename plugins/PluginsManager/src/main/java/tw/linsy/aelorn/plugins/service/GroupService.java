package tw.linsy.aelorn.plugins.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.audit.AuditLog;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.model.PluginGroup;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;

/**
 * Acting on a named set of plugins from {@code groups.yml}.
 *
 * A thin orchestrator over {@link PluginLifecycleService}: the value is in ordering
 * and in reporting partial success, not in doing anything the single-plugin
 * commands cannot.
 *
 * <p><b>Order matters and is opposite per direction.</b> Enable follows the order
 * the admin wrote, which is the order they listed dependencies in; disable reverses
 * it, so a dependency is never taken down before the plugin needing it. The previous
 * version used the written order for both, so a batch disable produced a cascade of
 * "depends on a disabled plugin" errors that looked like a bug in the group.
 *
 * <p>Members are confirmed once, at the group level. Passing {@code confirmed} down
 * to each member is deliberate: asking an admin to confirm thirty times is how
 * {@code --confirm} stops being read.
 */
public final class GroupService {

    private final SettingsStore settings;
    private final MessageCatalog messages;
    private final AuditLog audit;
    private final PluginLifecycleService lifecycle;
    private final ProtectionService protection;

    public GroupService(SettingsStore settings, MessageCatalog messages, AuditLog audit,
                        PluginLifecycleService lifecycle, ProtectionService protection) {
        this.settings = settings;
        this.messages = messages;
        this.audit = audit;
        this.lifecycle = lifecycle;
        this.protection = protection;
    }

    public Reply list() {
        List<PluginGroup> groups = settings.groups().groups();
        if (groups.isEmpty()) {
            return Reply.fail(messages.raw("group.none"));
        }
        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("group.list-header", "count", groups.size()));
        for (PluginGroup group : groups) {
            lines.add(messages.raw("group.list-row",
                "group", group.name(),
                "count", group.plugins().size(),
                "locked", group.locked() ? messages.raw("group.locked-tag") : "",
                "description", group.description()));
        }
        return Reply.ok(lines);
    }

    public Reply info(@Nullable String query) {
        PluginGroup group = settings.groups().find(query);
        if (group == null) {
            return Reply.fail(messages.raw("group.not-found", "query", display(query)));
        }
        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("group.info-header", "group", group.name()));
        lines.add(messages.raw("group.info-description", "description", display(group.description())));
        lines.add(messages.raw("group.info-locked", "locked", yesNo(group.locked())));
        for (String name : group.plugins()) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            lines.add(messages.raw("group.info-member",
                "plugin", name,
                "state", stateOf(plugin),
                "protected", plugin != null && protection.isProtected(plugin)
                    ? messages.raw("state.protected") : ""));
        }
        return Reply.ok(lines);
    }

    /**
     * Runs one lifecycle action across every member.
     *
     * @param action {@code enable}, {@code disable} or {@code reload}
     */
    public Reply act(String actor, String action, @Nullable String query,
                     boolean confirmed, boolean force) {
        PluginGroup group = settings.groups().find(query);
        if (group == null) {
            return Reply.fail(messages.raw("group.not-found", "query", display(query)));
        }
        String verb = action.toLowerCase(Locale.ROOT);
        if (!List.of("enable", "disable", "reload").contains(verb)) {
            return Reply.fail(messages.raw("group.unsupported-action", "action", action));
        }
        if (group.locked() && !"enable".equals(verb)) {
            return Reply.fail(messages.raw("group.locked", "group", group.name(), "action", verb));
        }
        if (settings.manager().guards().requireConfirmation() && !confirmed) {
            return Reply.fail(messages.raw("common.needs-confirm", "usage",
                messages.raw("command.root") + " group " + verb + " @" + group.name()
                    + (force ? " --force" : "") + " --confirm"));
        }

        // Enable in declared order, take down in reverse: see the class javadoc.
        List<String> members = "enable".equals(verb) ? group.plugins() : group.reversedPlugins();
        int succeeded = 0;
        List<String> failures = new ArrayList<>();
        for (String member : members) {
            Reply result = switch (verb) {
                case "enable" -> lifecycle.enable(actor, member, true);
                case "disable" -> lifecycle.disable(actor, member, true, force);
                default -> lifecycle.reload(actor, member, true, force);
            };
            if (result.success()) {
                succeeded++;
            } else {
                failures.add(member + " → " + messages.plainMarkup(result.first()));
            }
        }

        boolean complete = succeeded == members.size();
        audit.record(actor, "group-" + verb, group.name(), complete ? "SUCCESS" : "WARN",
            succeeded + "/" + members.size()
                + (failures.isEmpty() ? "" : "; " + String.join("; ", failures)));

        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("group.result",
            "group", group.name(), "action", verb,
            "done", succeeded, "total", members.size()));
        if (!failures.isEmpty()) {
            lines.add(messages.raw("group.result-failures",
                "failures", Texts.join(messages, failures)));
        }
        // Any member succeeding is reported as success: a batch that moved thirty of
        // thirty-one plugins did its job, and failing the whole reply would tell the
        // admin to run it again over the twenty-nine that are already done.
        return new Reply(succeeded > 0, lines);
    }

    private String stateOf(@Nullable Plugin plugin) {
        if (plugin == null) {
            return messages.raw("state.not-loaded");
        }
        return messages.raw(plugin.isEnabled() ? "state.enabled" : "state.disabled");
    }

    private String yesNo(boolean value) {
        return messages.raw(value ? "common.yes-label" : "common.no-label");
    }

    private String display(@Nullable String value) {
        return value == null || value.isBlank() ? messages.raw("common.none") : value.trim();
    }
}
