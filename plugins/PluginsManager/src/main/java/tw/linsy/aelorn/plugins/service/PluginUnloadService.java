package tw.linsy.aelorn.plugins.service;

import io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader;
import io.papermc.paper.plugin.provider.classloader.PaperClassLoaderStorage;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import tw.linsy.aelorn.plugins.audit.AuditLog;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.model.PluginRef;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.model.UnloadReport;
import tw.linsy.aelorn.plugins.nms.InternalsFailure;
import tw.linsy.aelorn.plugins.nms.ServerInternals;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;
import tw.linsy.aelorn.plugins.platform.PlatformProfile;

/**
 * Taking a plugin out of a running server.
 *
 * <p>Nine teardown steps, each independent and each recorded. They accumulate
 * rather than abort: abandoning an unload halfway leaves the server holding a
 * half-dead plugin, which is worse than finishing with a warning about the one step
 * that failed.
 *
 * <p><b>Order is the design.</b> Tasks are cancelled before listeners are
 * unregistered, because a task that fires during teardown would run against a
 * partially dismantled plugin. The class loader is closed last, after the server
 * has stopped handing the plugin out, or a lookup mid-teardown resolves classes
 * from a closed jar.
 *
 * <h2>What was wrong before</h2>
 * The previous implementation reflected into {@code SimplePluginManager}'s
 * {@code plugins} and {@code lookupNames} fields. On a modern Paper-family server
 * those collections are vestigial — that class delegates to Paper's own manager and
 * keeps its own empty. So the unload removed the plugin from two unused lists,
 * reported success, and left the server still resolving the plugin from a class
 * loader it had just closed. Deregistration now goes through the internals adapter
 * to the registry the server actually reads.
 *
 * <p>Two other gaps closed here: the class loader was closed but never
 * <em>unregistered</em>, so other plugins could still load classes from it; and the
 * command tree was never resent, so clients kept offering completions for commands
 * that no longer existed.
 */
public final class PluginUnloadService {

    private final SettingsStore settings;
    private final MessageCatalog messages;
    private final AuditLog audit;
    private final PluginLookup lookup;
    private final OperationGuards guards;
    private final ServerInternals internals;
    private final PlatformProfile platform;
    private final Logger logger;

    public PluginUnloadService(SettingsStore settings, MessageCatalog messages, AuditLog audit,
                              PluginLookup lookup, OperationGuards guards, ServerInternals internals,
                              PlatformProfile platform, Logger logger) {
        this.settings = settings;
        this.messages = messages;
        this.audit = audit;
        this.lookup = lookup;
        this.guards = guards;
        this.internals = internals;
        this.platform = platform;
        this.logger = logger;
    }

    public Reply unload(String actor, String query, boolean confirmed, boolean force) {
        if (!settings.manager().guards().allowUnload()) {
            return Reply.fail(messages.raw("unload.disabled"));
        }
        // Refused rather than attempted without the adapter: every other operation
        // degrades to an API call, but an unload that cannot deregister the plugin
        // leaves the server in the exact broken state described above.
        if (!internals.available()) {
            return Reply.fail(messages.raw("unload.no-internals",
                "detail", internals.describe()));
        }
        Reply blocked = guards.confirmation(usage(query, confirmed, force), confirmed);
        if (blocked != null) {
            return blocked;
        }
        PluginRef ref = lookup.resolve(query);
        if (!ref.resolved()) {
            return PluginLookup.unresolved(messages, ref, query);
        }
        Plugin plugin = ref.require();
        blocked = guards.protection(plugin, messages.raw("action.unload"));
        if (blocked != null) {
            return blocked;
        }
        OperationGuards.Dependants dependants = guards.hardDependants(plugin, force, false);
        if (!dependants.allowed()) {
            return dependants.blocked();
        }

        String name = plugin.getName();
        UnloadReport report = teardown(plugin);

        audit.record(actor, "unload", name, report.clean() ? "SUCCESS" : "WARN", auditDetail(report));
        List<String> lines = new ArrayList<>();
        lines.add(messages.raw(report.clean() ? "unload.done" : "unload.done-with-warnings",
            "plugin", name));
        lines.addAll(dependants.notes());
        lines.add(messages.raw("unload.steps", "steps", stepSummary(report)));
        for (UnloadReport.Step failure : report.failedSteps()) {
            lines.add(messages.raw("unload.step-failed",
                "step", messages.raw(failure.messageKey()), "reason", failure.detail()));
        }
        return Reply.ok(lines);
    }

    /**
     * Runs the teardown steps in order, recording each.
     *
     * Package-private rather than private so the shutdown path can reuse it without
     * the guards; nothing outside this package calls it.
     */
    private UnloadReport teardown(Plugin plugin) {
        UnloadReport report = new UnloadReport();

        step(report, "unload.step.disable", () -> {
            if (plugin.isEnabled()) {
                Bukkit.getPluginManager().disablePlugin(plugin);
            }
            return "";
        });
        step(report, "unload.step.tasks", () -> String.valueOf(cancelTasks(plugin)));
        step(report, "unload.step.listeners", () -> {
            HandlerList.unregisterAll(plugin);
            return "";
        });
        step(report, "unload.step.services", () -> {
            Bukkit.getServicesManager().unregisterAll(plugin);
            return "";
        });
        step(report, "unload.step.channels", () -> {
            Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin);
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin);
            return "";
        });
        step(report, "unload.step.commands", () -> String.valueOf(unregisterCommands(plugin)));
        step(report, "unload.step.permissions", () -> String.valueOf(removePermissions(plugin)));
        step(report, "unload.step.registry", () -> {
            internals.deregisterPlugin(plugin);
            return "";
        });
        // Last: everything above may still need to resolve one of the plugin's
        // classes, and a closed loader turns that into a NoClassDefFoundError.
        step(report, "unload.step.classloader", () -> closeClassLoader(plugin));
        step(report, "unload.step.command-tree", () -> {
            internals.syncCommandTree();
            return "";
        });
        return report;
    }

    /** One step, with its failure recorded rather than thrown. */
    private void step(UnloadReport report, String key, StepBody body) {
        try {
            String detail = body.run();
            report.succeeded(key, detail);
        } catch (Throwable failure) {
            logger.log(Level.WARNING, "卸載步驟 " + key + " 失敗。", failure);
            report.failed(key, Texts.summarise(failure));
        }
    }

    private interface StepBody {
        String run() throws Exception;
    }

    // ── 步驟 ──────────────────────────────────────────────────────────────

    /**
     * Stops the plugin's scheduled work before it is dismantled, or its tasks keep
     * firing against a dead class loader.
     *
     * The legacy scheduler is only asked on a single-threaded server. On a
     * regionised one the same call throws, and the previous version's
     * {@code catch (Throwable)} around it turned every unload into a silent
     * exception that also hid real dispatch failures.
     *
     * @return how many schedulers accepted the cancellation
     */
    private int cancelTasks(Plugin plugin) {
        int cancelled = 0;
        cancelled += tryCancel(() -> Bukkit.getGlobalRegionScheduler().cancelTasks(plugin));
        cancelled += tryCancel(() -> Bukkit.getAsyncScheduler().cancelTasks(plugin));
        if (platform.hasLegacyScheduler()) {
            cancelled += tryCancel(() -> Bukkit.getScheduler().cancelTasks(plugin));
        }
        return cancelled;
    }

    private int tryCancel(Runnable cancellation) {
        try {
            cancellation.run();
            return 1;
        } catch (RuntimeException refused) {
            // A scheduler that refuses is not fatal: the unload proceeds and the
            // count tells the admin how much was actually stopped.
            logger.fine("排程器拒絕取消：" + Texts.summarise(refused));
            return 0;
        }
    }

    /**
     * Unregisters every command the plugin owns.
     *
     * Through {@link CommandMap#getKnownCommands()}, which is public API — the
     * previous version reflected a {@code knownCommands} field for the same map, in
     * two separate classes, each with its own field-walking helper.
     *
     * @return how many map keys were removed
     */
    private int unregisterCommands(Plugin plugin) {
        CommandMap commandMap = Bukkit.getCommandMap();
        Map<String, Command> known = commandMap.getKnownCommands();

        // Keys are collected before anything is removed, and removal goes through
        // Map.remove rather than the entry-set iterator.
        //
        // The map Paper hands back is not a map: CraftCommandMap is constructed over
        // BukkitBrigForwardingMap, a view that forwards to the Brigadier dispatcher.
        // It implements remove(Object), but its entry-set iterator does not — so the
        // obvious removeIf over the entry set throws UnsupportedOperationException
        // part-way through the teardown, which is exactly how this was found: on a
        // live server, with the plugin already deregistered and its class loader
        // already closed, leaving its commands in the map pointing at dead classes.
        List<String> ownedKeys = new ArrayList<>();
        for (Map.Entry<String, Command> entry : known.entrySet()) {
            if (ownsCommand(entry.getValue(), plugin)) {
                ownedKeys.add(entry.getKey());
            }
        }
        int removed = 0;
        for (String key : ownedKeys) {
            Command command = known.remove(key);
            if (command != null) {
                command.unregister(commandMap);
                removed++;
            }
        }
        return removed;
    }

    private static boolean ownsCommand(Command command, Plugin plugin) {
        if (command instanceof PluginIdentifiableCommand identifiable) {
            return identifiable.getPlugin() == plugin;
        }
        // A command with no declared owner is attributed by class loader, which
        // catches the ones registered directly into the map by the plugin's code.
        return command.getClass().getClassLoader() == plugin.getClass().getClassLoader();
    }

    /**
     * Removes the permissions the plugin declared.
     *
     * Skipped entirely by the previous version, which left every node registered:
     * loading the plugin again then logged a duplicate-permission warning per node,
     * and permission plugins kept offering nodes for a plugin that was gone.
     */
    private int removePermissions(Plugin plugin) {
        int removed = 0;
        for (Permission permission : List.copyOf(plugin.getPluginMeta().getPermissions())) {
            Bukkit.getPluginManager().removePermission(permission);
            removed++;
        }
        return removed;
    }

    /**
     * Unregisters and closes the plugin's class loader.
     *
     * Unregistering is the part that matters and the part that was missing: a loader
     * left in Paper's storage stays reachable from every other plugin's group, so
     * classes from an unloaded plugin still resolve and the jar stays locked on
     * Windows. Both calls are public API.
     */
    private String closeClassLoader(Plugin plugin) throws IOException {
        ClassLoader loader = plugin.getClass().getClassLoader();
        if (loader instanceof ConfiguredPluginClassLoader configured) {
            PaperClassLoaderStorage.instance().unregisterClassloader(configured);
            configured.close();
            return messages.plain("unload.classloader-paper");
        }
        if (loader instanceof Closeable closeable) {
            closeable.close();
            return messages.plain("unload.classloader-legacy");
        }
        throw new IOException("類別載入器無法關閉：" + loader.getClass().getName());
    }

    // ── 共用 ──────────────────────────────────────────────────────────────

    private String stepSummary(UnloadReport report) {
        List<String> parts = new ArrayList<>();
        for (UnloadReport.Step step : report.doneSteps()) {
            String label = messages.raw(step.messageKey());
            parts.add(step.detail().isEmpty() ? label : label + "=" + step.detail());
        }
        return Texts.join(messages, parts);
    }

    private String auditDetail(UnloadReport report) {
        List<String> parts = new ArrayList<>();
        for (UnloadReport.Step step : report.doneSteps()) {
            String label = messages.plain(step.messageKey());
            parts.add(step.detail().isEmpty() ? label : label + "=" + step.detail());
        }
        for (UnloadReport.Step step : report.failedSteps()) {
            parts.add("!" + messages.plain(step.messageKey()) + "=" + step.detail());
        }
        return String.join("; ", parts);
    }

    private String usage(String query, boolean confirmed, boolean force) {
        StringBuilder command = new StringBuilder(messages.raw("command.root")).append(" unload");
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
