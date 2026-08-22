package tw.linsy.aelorn.plugins.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.audit.AuditLog;
import tw.linsy.aelorn.plugins.config.CommandSettings;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.nms.InternalsFailure;
import tw.linsy.aelorn.plugins.nms.ServerInternals;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;

/**
 * Which plugin owns which command, and removing one that shouldn't be there.
 *
 * The reason this exists: two plugins registering the same command name is silent,
 * and the loser is decided by load order. This makes the conflict visible and lets
 * an admin remove the wrong one without deleting a jar.
 *
 * <h2>No reflection</h2>
 * {@link CommandMap#getKnownCommands()} is public API. The previous version
 * reflected a {@code knownCommands} field for the same map, in two separate classes,
 * each carrying its own superclass-walking field finder — and returned an empty map
 * on failure, so the index silently showed nothing rather than reporting a problem.
 *
 * <p>Unregistering resyncs the command tree, without which clients keep offering the
 * removed command until they reconnect.
 */
public final class CommandIndexService {

    /** One key in the command map, and who owns the command behind it. */
    private record Entry(String label, String owner, Command command) {
    }

    private final SettingsStore settings;
    private final MessageCatalog messages;
    private final AuditLog audit;
    private final ServerInternals internals;

    public CommandIndexService(SettingsStore settings, MessageCatalog messages,
                               AuditLog audit, ServerInternals internals) {
        this.settings = settings;
        this.messages = messages;
        this.audit = audit;
        this.internals = internals;
    }

    public Reply list(@Nullable String filter) {
        CommandSettings config = settings.commands();
        if (!config.enabled()) {
            return Reply.fail(messages.raw("command-index.disabled"));
        }
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<Entry> entries = new ArrayList<>(entries(config.hideDuplicates()));
        if (!needle.isEmpty()) {
            entries.removeIf(entry -> !entry.label().toLowerCase(Locale.ROOT).contains(needle)
                && !entry.owner().toLowerCase(Locale.ROOT).contains(needle)
                && !entry.command().getName().toLowerCase(Locale.ROOT).contains(needle));
        }
        entries.sort(Comparator.comparing(Entry::owner, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER));

        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("command-index.header", "count", entries.size()));
        int shown = Math.min(config.maxLines(), entries.size());
        for (int index = 0; index < shown; index++) {
            Entry entry = entries.get(index);
            String permission = config.showPermission() && entry.command().getPermission() != null
                && !entry.command().getPermission().isBlank()
                ? messages.raw("command-index.permission", "permission", entry.command().getPermission())
                : "";
            String aliases = config.showAliases() && !entry.command().getAliases().isEmpty()
                ? messages.raw("command-index.aliases",
                    "aliases", Texts.join(messages, entry.command().getAliases()))
                : "";
            lines.add(messages.raw("command-index.row",
                "label", entry.label(), "owner", entry.owner(),
                "permission", permission, "aliases", aliases));
        }
        if (entries.size() > shown) {
            lines.add(messages.raw("common.truncated", "remaining", entries.size() - shown));
        }
        return Reply.ok(lines);
    }

    /**
     * Removes one command from the map, under every key it answers to.
     *
     * Removing only the key the admin typed would leave the command reachable by its
     * aliases and by its {@code plugin:command} form, which reads as the removal
     * having silently failed.
     */
    public Reply unregister(String actor, @Nullable String query, boolean confirmed) {
        CommandSettings config = settings.commands();
        if (!config.enabled()) {
            return Reply.fail(messages.raw("command-index.disabled"));
        }
        if (!config.allowUnregister()) {
            return Reply.fail(messages.raw("command-index.unregister-disabled"));
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return Reply.fail(messages.raw("command-index.missing-command"));
        }
        if (config.requireConfirmation() && !confirmed) {
            return Reply.fail(messages.raw("common.needs-confirm", "usage",
                messages.raw("command.root") + " command unregister " + needle + " --confirm"));
        }

        CommandMap commandMap = Bukkit.getCommandMap();
        Map<String, Command> known = commandMap.getKnownCommands();
        Command target = null;
        for (Map.Entry<String, Command> entry : known.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(needle)
                || entry.getValue().getName().equalsIgnoreCase(needle)) {
                target = entry.getValue();
                break;
            }
        }
        if (target == null) {
            return Reply.fail(messages.raw("command-index.not-found", "command", needle));
        }

        // Collected first, then removed by key: Paper's known-commands map is a view
        // over the Brigadier dispatcher whose entry-set iterator refuses removal,
        // even though remove(Object) works. See PluginUnloadService for the same trap.
        Command removing = target;
        Set<String> removedKeys = new LinkedHashSet<>();
        for (Map.Entry<String, Command> entry : known.entrySet()) {
            if (entry.getValue() == removing) {
                removedKeys.add(entry.getKey());
            }
        }
        for (String key : removedKeys) {
            known.remove(key);
        }
        removing.unregister(commandMap);

        String treeNote = syncTree();
        audit.record(actor, "command-unregister", removing.getName(),
            removedKeys.isEmpty() ? "WARN" : "SUCCESS", String.join(", ", removedKeys));

        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("command-index.unregistered",
            "command", removing.getName(), "keys", Texts.join(messages, removedKeys)));
        if (!treeNote.isEmpty()) {
            lines.add(treeNote);
        }
        return Reply.ok(lines);
    }

    /** Command labels for tab completion. */
    public List<String> suggest(@Nullable String partial) {
        String needle = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> labels = new ArrayList<>();
        for (Entry entry : entries(true)) {
            if (entry.label().toLowerCase(Locale.ROOT).startsWith(needle)) {
                labels.add(entry.label());
            }
        }
        labels.sort(String.CASE_INSENSITIVE_ORDER);
        return labels;
    }

    /**
     * @param hideDuplicates keep only the first key per command, so a plugin with
     *                       six aliases does not fill the listing
     */
    private List<Entry> entries(boolean hideDuplicates) {
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        List<Entry> entries = new ArrayList<>(known.size());
        Set<Command> seen = new LinkedHashSet<>();
        for (Map.Entry<String, Command> entry : known.entrySet()) {
            if (hideDuplicates && !seen.add(entry.getValue())) {
                continue;
            }
            entries.add(new Entry(entry.getKey(), ownerOf(entry.getValue()), entry.getValue()));
        }
        return entries;
    }

    private String ownerOf(Command command) {
        if (command instanceof PluginIdentifiableCommand identifiable) {
            Plugin plugin = identifiable.getPlugin();
            return plugin == null ? messages.raw("command-index.owner-server") : plugin.getName();
        }
        // A vanilla or server command has no owning plugin; its class name is the
        // most honest attribution available.
        return command.getClass().getSimpleName();
    }

    /** @return a warning line when the tree could not be resent, or {@code ""} */
    private String syncTree() {
        if (!settings.manager().guards().syncCommandTree()) {
            return "";
        }
        try {
            internals.syncCommandTree();
            return "";
        } catch (InternalsFailure unavailable) {
            return messages.raw("command-index.tree-stale", "reason", unavailable.getMessage());
        }
    }
}
