package tw.linsy.aelorn.plugins.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import tw.linsy.aelorn.plugins.audit.AuditLog;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.model.JarDescriptor;
import tw.linsy.aelorn.plugins.model.PluginRef;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.nms.InternalsFailure;
import tw.linsy.aelorn.plugins.nms.ServerInternals;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;

/**
 * Enable, disable, reload and load — the four operations the server API supports
 * directly.
 *
 * Unloading is deliberately elsewhere ({@link PluginUnloadService}): it is not a
 * fifth API call but an eight-step teardown that reaches server internals, and
 * keeping it here would have hidden that difference behind a uniform-looking list
 * of methods.
 *
 * <p>Every method takes the actor and writes one audit record, success or failure.
 * That is the whole point of the plugin: the operations are one API call each, and
 * the value is in the guards around them and the trail behind them.
 *
 * <p>All four mutate server state and must run on the global region. The command
 * layer is what puts them there — every subcommand that reaches this service is
 * declared as global-region work, so the requirement is satisfied structurally
 * rather than re-checked here.
 */
public final class PluginLifecycleService {

    private final SettingsStore settings;
    private final MessageCatalog messages;
    private final AuditLog audit;
    private final PluginLookup lookup;
    private final OperationGuards guards;
    private final JarIndex jars;
    private final ServerInternals internals;
    private final Logger logger;

    public PluginLifecycleService(SettingsStore settings, MessageCatalog messages, AuditLog audit,
                                  PluginLookup lookup, OperationGuards guards, JarIndex jars,
                                  ServerInternals internals, Logger logger) {
        this.settings = settings;
        this.messages = messages;
        this.audit = audit;
        this.lookup = lookup;
        this.guards = guards;
        this.jars = jars;
        this.internals = internals;
        this.logger = logger;
    }

    // ── 啟用 ──────────────────────────────────────────────────────────────

    public Reply enable(String actor, String query, boolean confirmed) {
        Reply blocked = guards.confirmation(usage("enable", query, confirmed, false), confirmed);
        if (blocked != null) {
            return blocked;
        }
        PluginRef ref = lookup.resolve(query);
        if (!ref.resolved()) {
            return PluginLookup.unresolved(messages, ref, query);
        }
        Plugin plugin = ref.require();
        if (plugin.isEnabled()) {
            return Reply.fail(messages.raw("lifecycle.already-enabled", "plugin", plugin.getName()));
        }
        // No protection check: enabling a protected plugin is what protection is
        // meant to preserve, not prevent.
        try {
            Bukkit.getPluginManager().enablePlugin(plugin);
            syncCommandTree();
            audit.record(actor, "enable", plugin.getName(), "SUCCESS",
                messages.plain("audit.enable"));
            return Reply.ok(messages.raw("lifecycle.enabled", "plugin", plugin.getName()));
        } catch (Throwable failure) {
            return failed(actor, "enable", plugin.getName(), failure, "lifecycle.enable-failed");
        }
    }

    // ── 停用 ──────────────────────────────────────────────────────────────

    public Reply disable(String actor, String query, boolean confirmed, boolean force) {
        Reply blocked = guards.confirmation(usage("disable", query, confirmed, force), confirmed);
        if (blocked != null) {
            return blocked;
        }
        PluginRef ref = lookup.resolve(query);
        if (!ref.resolved()) {
            return PluginLookup.unresolved(messages, ref, query);
        }
        Plugin plugin = ref.require();
        blocked = guards.protection(plugin, messages.raw("action.disable"));
        if (blocked != null) {
            return blocked;
        }
        if (!plugin.isEnabled()) {
            return Reply.fail(messages.raw("lifecycle.already-disabled", "plugin", plugin.getName()));
        }
        OperationGuards.Dependants dependants = guards.hardDependants(plugin, force, false);
        if (!dependants.allowed()) {
            return dependants.blocked();
        }
        try {
            Bukkit.getPluginManager().disablePlugin(plugin);
            syncCommandTree();
            List<String> notes = new ArrayList<>(dependants.notes());
            notes.addAll(guards.softDependantNotes(plugin));
            audit.record(actor, "disable", plugin.getName(), "SUCCESS", plainNotes(notes));
            return Reply.ok(messages.raw("lifecycle.disabled", "plugin", plugin.getName())).with(notes);
        } catch (Throwable failure) {
            return failed(actor, "disable", plugin.getName(), failure, "lifecycle.disable-failed");
        }
    }

    // ── 重載 ──────────────────────────────────────────────────────────────

    /**
     * Disable then enable, bringing suspended dependants back in reverse order.
     *
     * Reverse order because they were disabled in dependency order, so restoring
     * them the same way would enable a plugin before what it depends on.
     *
     * <p>This does not re-read the jar. A reload here restarts the plugin's own
     * lifecycle, which is what most plugins mean by reload; replacing the jar needs
     * unload, then load.
     */
    public Reply reload(String actor, String query, boolean confirmed, boolean force) {
        Reply blocked = guards.confirmation(usage("reload", query, confirmed, force), confirmed);
        if (blocked != null) {
            return blocked;
        }
        PluginRef ref = lookup.resolve(query);
        if (!ref.resolved()) {
            return PluginLookup.unresolved(messages, ref, query);
        }
        Plugin plugin = ref.require();
        blocked = guards.protection(plugin, messages.raw("action.reload"));
        if (blocked != null) {
            return blocked;
        }
        OperationGuards.Dependants dependants = guards.hardDependants(plugin, force, true);
        if (!dependants.allowed()) {
            return dependants.blocked();
        }
        try {
            if (plugin.isEnabled()) {
                Bukkit.getPluginManager().disablePlugin(plugin);
            }
            Bukkit.getPluginManager().enablePlugin(plugin);

            List<Plugin> suspended = dependants.disabled();
            for (int index = suspended.size() - 1; index >= 0; index--) {
                Bukkit.getPluginManager().enablePlugin(suspended.get(index));
            }
            syncCommandTree();

            List<String> notes = new ArrayList<>(dependants.notes());
            notes.addAll(guards.softDependantNotes(plugin));
            audit.record(actor, "reload", plugin.getName(), "SUCCESS", plainNotes(notes));
            return Reply.ok(messages.raw("lifecycle.reloaded", "plugin", plugin.getName())).with(notes);
        } catch (Throwable failure) {
            return failed(actor, "reload", plugin.getName(), failure, "lifecycle.reload-failed");
        }
    }

    // ── 載入 ──────────────────────────────────────────────────────────────

    /**
     * Loads a jar that is on disk but not registered.
     *
     * The descriptor is read first so a jar declaring a plugin that is already
     * loaded is refused by name rather than by letting the server throw — the
     * server's own error for that case names the class, not the plugin.
     */
    public Reply load(String actor, String query, boolean confirmed) {
        Reply blocked = guards.confirmation(usage("load", query, confirmed, false), confirmed);
        if (blocked != null) {
            return blocked;
        }
        JarIndex.JarRef jar = jars.resolveJar(query);
        if (!jar.found()) {
            return Reply.fail(messages.raw(jar.errorKey(), "value", jar.errorArg()));
        }
        Path path = jar.require();
        try {
            JarDescriptor descriptor = jars.readDescriptor(path);
            if (descriptor.hasName() && Bukkit.getPluginManager().getPlugin(descriptor.name()) != null) {
                return Reply.fail(messages.raw("lifecycle.already-loaded", "plugin", descriptor.name()));
            }
            Plugin loaded = Bukkit.getPluginManager().loadPlugin(path.toFile());
            if (loaded == null) {
                audit.record(actor, "load", path.getFileName().toString(), "FAIL",
                    messages.plain("audit.load-null"));
                return Reply.fail(messages.raw("lifecycle.load-null", "jar", path.getFileName().toString()));
            }
            Bukkit.getPluginManager().enablePlugin(loaded);
            syncCommandTree();
            audit.record(actor, "load", loaded.getName(), "SUCCESS", path.getFileName().toString());
            return Reply.ok(messages.raw("lifecycle.loaded",
                "plugin", loaded.getName(), "jar", path.getFileName().toString()));
        } catch (IOException unreadable) {
            return failed(actor, "load", path.getFileName().toString(), unreadable,
                "lifecycle.load-failed");
        } catch (Throwable failure) {
            return failed(actor, "load", path.getFileName().toString(), failure,
                "lifecycle.load-failed");
        }
    }

    // ── 共用 ──────────────────────────────────────────────────────────────

    /**
     * Rebuilds and resends the command tree after a change in what is registered.
     *
     * Best-effort by design: the lifecycle operation already succeeded, and a
     * server whose internals are unreachable should report a stale client tree, not
     * turn a successful enable into a failure. The condition is configurable
     * because on a server with many players the resend is not free.
     */
    private void syncCommandTree() {
        if (!settings.manager().guards().syncCommandTree()) {
            return;
        }
        try {
            internals.syncCommandTree();
        } catch (InternalsFailure unavailable) {
            logger.fine("未重送指令樹：" + unavailable.getMessage());
        }
    }

    private Reply failed(String actor, String action, String target, Throwable failure, String key) {
        // The one-line summary goes to chat and the audit; the stack goes to the
        // server log, because that is where it is readable and useful.
        logger.log(Level.WARNING, action + " " + target + " 失敗。", failure);
        audit.record(actor, action, target, "FAIL", Texts.summarise(failure));
        return Reply.fail(messages.raw(key, "reason", Texts.summarise(failure)));
    }

    private String plainNotes(List<String> notes) {
        if (notes.isEmpty()) {
            return messages.plain("audit.no-notes");
        }
        List<String> plain = new ArrayList<>(notes.size());
        for (String note : notes) {
            plain.add(messages.plainMarkup(note));
        }
        return String.join("; ", plain);
    }

    /** The command to re-send with confirmation, echoing the flags already given. */
    private String usage(String action, String query, boolean confirmed, boolean force) {
        StringBuilder command = new StringBuilder(messages.raw("command.root"));
        command.append(' ').append(action);
        if (query != null && !query.isBlank()) {
            command.append(' ').append(query.trim());
        }
        if (force) {
            command.append(" --force");
        }
        if (!confirmed) {
            command.append(" --confirm");
        }
        return command.toString();
    }
}
