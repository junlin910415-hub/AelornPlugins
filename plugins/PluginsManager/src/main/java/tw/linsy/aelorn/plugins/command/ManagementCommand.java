package tw.linsy.aelorn.plugins.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.audit.AuditLog;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.gui.GuiService;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;
import tw.linsy.aelorn.plugins.platform.Sched;
import tw.linsy.aelorn.plugins.service.CommandIndexService;
import tw.linsy.aelorn.plugins.service.ConfigReloadService;
import tw.linsy.aelorn.plugins.service.GroupService;
import tw.linsy.aelorn.plugins.service.PluginLifecycleService;
import tw.linsy.aelorn.plugins.service.PluginLookup;
import tw.linsy.aelorn.plugins.service.PluginUnloadService;
import tw.linsy.aelorn.plugins.service.ProtectionService;
import tw.linsy.aelorn.plugins.service.ReportService;
import tw.linsy.aelorn.plugins.service.VersionArchiveService;

/**
 * The single command, dispatching to services.
 *
 * <p>Contains no logic of its own beyond three decisions, applied uniformly: does the
 * sender have the permission, which thread does the handler need, and which service does
 * the work. Everything else — guards, ordering, messages, audit — belongs to the
 * services, and the dispatcher never sees a plugin name it interprets.
 *
 * <p>Subcommands live in a table rather than a {@code switch}. The previous version's
 * 55-line switch repeated a scheduler wrapper in every arm, and two read-only arms did
 * disk IO on the region thread because the wrapper had been copied from the arm above.
 * Here the thread comes from {@link Subcommand#where()} and the dispatcher applies it, so
 * a new subcommand cannot get it wrong.
 */
public final class ManagementCommand implements CommandExecutor, TabCompleter {

    private final MessageCatalog messages;
    private final SettingsStore settings;
    private final Sched sched;
    private final ReplySender replies;
    private final PluginLookup lookup;
    private final ReportService reports;
    private final PluginLifecycleService lifecycle;
    private final PluginUnloadService unload;
    private final ProtectionService protection;
    private final GroupService groups;
    private final CommandIndexService commandIndex;
    private final VersionArchiveService archives;
    private final ConfigReloadService configReload;
    private final AuditLog audit;
    private final GuiService gui;

    /** Insertion-ordered so help and root completion follow a deliberate order. */
    private final Map<String, Subcommand> table = new LinkedHashMap<>();

    public ManagementCommand(MessageCatalog messages, SettingsStore settings, Sched sched,
                             ReplySender replies, PluginLookup lookup, ReportService reports,
                             PluginLifecycleService lifecycle, PluginUnloadService unload,
                             ProtectionService protection, GroupService groups,
                             CommandIndexService commandIndex, VersionArchiveService archives,
                             ConfigReloadService configReload, AuditLog audit,
                             GuiService gui) {
        this.messages = messages;
        this.settings = settings;
        this.sched = sched;
        this.replies = replies;
        this.lookup = lookup;
        this.reports = reports;
        this.lifecycle = lifecycle;
        this.unload = unload;
        this.protection = protection;
        this.groups = groups;
        this.commandIndex = commandIndex;
        this.archives = archives;
        this.configReload = configReload;
        this.audit = audit;
        this.gui = gui;
        register();
    }

    // ── 指令表 ────────────────────────────────────────────────────────────

    private void register() {
        // Read-only, global region: reads plugin state, no disk.
        add(Subcommand.of("status", "zpm.view", Subcommand.Where.GLOBAL,
            (actor, args) -> reports.status()));
        // Opening the menu is read-only; each action inside it re-checks its own
        // permission and runs through the same guards as the equivalent command.
        add(Subcommand.of("gui", "zpm.view", Subcommand.Where.GLOBAL, this::runGui));
        add(Subcommand.reading("list", "zpm.view", Subcommand.Where.GLOBAL,
            (actor, args) -> reports.list(args.filterFrom(1), args.trailingInt(1)),
            args -> lookup.suggest(args.at(1))));
        add(Subcommand.reading("find", "zpm.view", Subcommand.Where.GLOBAL,
            (actor, args) -> reports.list(args.joinedFrom(1), 1),
            args -> lookup.suggest(args.at(1))));
        add(Subcommand.reading("info", "zpm.view", Subcommand.Where.GLOBAL,
            (actor, args) -> reports.info(args.joinedFrom(1)),
            args -> lookup.suggest(args.at(1))));

        // Read-only, async: opens and hashes files.
        add(Subcommand.of("scan", "zpm.view", Subcommand.Where.ASYNC,
            (actor, args) -> reports.scan(reports.runScan(), args.joinedFrom(1))));
        add(Subcommand.of("audit", "zpm.view", Subcommand.Where.ASYNC,
            (actor, args) -> reports.auditTail(args.trailingInt(
                settings.manager().audit().maxTailLines()))));

        // State changes, global region.
        add(Subcommand.changing("enable", "zpm.manage.state", Subcommand.Where.GLOBAL,
            (actor, args) -> lifecycle.enable(actor, args.joinedFrom(1), args.confirmed()),
            args -> completeTarget(args)));
        add(Subcommand.changing("disable", "zpm.manage.state", Subcommand.Where.GLOBAL,
            (actor, args) -> lifecycle.disable(actor, args.joinedFrom(1),
                args.confirmed(), args.forced()),
            args -> completeTarget(args)));
        add(Subcommand.changing("reload", "zpm.manage.state", Subcommand.Where.GLOBAL,
            (actor, args) -> lifecycle.reload(actor, args.joinedFrom(1),
                args.confirmed(), args.forced()),
            args -> completeTarget(args)));
        add(Subcommand.changing("load", "zpm.manage.state", Subcommand.Where.GLOBAL,
            (actor, args) -> lifecycle.load(actor, args.joinedFrom(1), args.confirmed()),
            args -> completeTarget(args)));
        add(Subcommand.changing("unload", "zpm.manage.unload", Subcommand.Where.GLOBAL,
            (actor, args) -> unload.unload(actor, args.joinedFrom(1),
                args.confirmed(), args.forced()),
            args -> completeTarget(args)));

        add(Subcommand.changing("protect", "zpm.protect", Subcommand.Where.GLOBAL,
            this::runProtect,
            args -> args.size() <= 2
                ? Args.filter(List.of("list", "add", "remove"), args.at(1))
                : lookup.suggest(args.at(2))));
        add(Subcommand.changing("group", "zpm.group", Subcommand.Where.GLOBAL,
            this::runGroup,
            args -> args.size() <= 2
                ? Args.filter(List.of("list", "info", "enable", "disable", "reload"), args.at(1))
                : settings.groups().suggest(args.at(2))));

        add(Subcommand.reading("commands", "zpm.command.view", Subcommand.Where.GLOBAL,
            (actor, args) -> commandIndex.list(args.joinedFrom(1)),
            args -> commandIndex.suggest(args.at(1))));
        add(Subcommand.changing("command", "zpm.command.unregister", Subcommand.Where.GLOBAL,
            this::runCommandControl,
            args -> args.size() <= 2
                ? Args.filter(List.of("unregister"), args.at(1))
                : commandIndex.suggest(args.at(2))));

        // Archive operations copy files; async.
        add(Subcommand.changing("archive", "zpm.version.archive", Subcommand.Where.ASYNC,
            (actor, args) -> archives.archive(actor, args.at(1), args.joinedFrom(2)),
            args -> lookup.suggest(args.at(1))));
        add(Subcommand.reading("versions", "zpm.version.view", Subcommand.Where.ASYNC,
            (actor, args) -> archives.versions(args.at(1)),
            args -> archives.suggestArchives(args.at(1))));
        add(Subcommand.changing("restore", "zpm.version.restore", Subcommand.Where.ASYNC,
            (actor, args) -> archives.restore(actor, args.at(1), args.at(2), args.confirmed()),
            args -> args.size() <= 2
                ? archives.suggestArchives(args.at(1))
                : archives.suggestVersions(args.at(1), args.at(2))));

        add(Subcommand.changing("config", "zpm.admin", Subcommand.Where.GLOBAL,
            this::runConfig,
            args -> Args.filter(List.of("reload", "check"), args.at(1))));
    }

    private void add(Subcommand subcommand) {
        table.put(subcommand.name(), subcommand);
    }

    // ── 分派 ──────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] raw) {
        Args args = Args.parse(label, raw);
        if (args.size() == 0 || "help".equals(args.subcommand())) {
            replies.send(sender, messages.rawList("help.lines", "command", label));
            return true;
        }
        Subcommand subcommand = table.get(args.subcommand());
        if (subcommand == null) {
            replies.sendKey(sender, "common.unknown-subcommand", "command", label);
            return true;
        }
        // Checked before the hop, so a refusal is immediate and schedules nothing.
        String actor = sender.getName();
        if (!sender.hasPermission(subcommand.permission())) {
            // Always recorded, for every subcommand. A denied attempt is the one event
            // an audit trail exists to capture, and it costs nothing when nobody is
            // probing — unlike a mistyped plugin name, which is why only sensitive
            // subcommands record their other refusals.
            audit.record(actor, subcommand.name(), args.joinedFrom(1), "DENIED",
                "permission=" + subcommand.permission());
            replies.sendKey(sender, "common.no-permission", "permission", subcommand.permission());
            return true;
        }
        Runnable work = () -> {
            Reply reply = subcommand.handler().run(actor, args);
            if (!reply.success() && subcommand.sensitive()) {
                audit.record(actor, subcommand.name(), args.joinedFrom(1), "REFUSED",
                    messages.plainMarkup(reply.first()));
            }
            replies.send(sender, reply);
        };
        switch (subcommand.where()) {
            case GLOBAL -> sched.global(work);
            case ASYNC -> sched.async(work);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(CommandSender sender, Command command,
                                                String label, String[] raw) {
        Args args = Args.parse(label, raw);
        String last = args.lastToken();

        if (raw.length <= 1) {
            return Args.filter(permittedRoots(sender), raw.length == 0 ? "" : raw[0]);
        }
        Subcommand subcommand = table.get(args.subcommand());
        if (subcommand == null || !sender.hasPermission(subcommand.permission())) {
            return List.of();
        }
        // A token already starting with a dash is being typed as a flag; offering
        // plugin names there is noise.
        if (last.startsWith("-")) {
            return Args.filter(Args.FLAG_SUGGESTIONS, last);
        }
        List<String> suggestions = new ArrayList<>(subcommand.completer().complete(args));
        suggestions.removeIf(entry -> !entry.toLowerCase(Locale.ROOT)
            .startsWith(last.toLowerCase(Locale.ROOT)));
        return suggestions;
    }

    /** Only the subcommands this sender may actually run, so completion never lies. */
    private List<String> permittedRoots(CommandSender sender) {
        List<String> roots = new ArrayList<>(table.size() + 1);
        roots.add("help");
        for (Subcommand subcommand : table.values()) {
            if (sender.hasPermission(subcommand.permission())) {
                roots.add(subcommand.name());
            }
        }
        return roots;
    }

    /**
     * Opens the menu for a player.
     *
     * <p>Console cannot open an inventory, so this refuses rather than pretending. The
     * viewer is looked up by name because {@link Subcommand.Handler} deliberately
     * carries only the actor string — widening it so one subcommand can reach the
     * sender would give every other subcommand a sender it has no business touching.
     */
    private Reply runGui(String actor, Args args) {
        if (!gui.available()) {
            return Reply.fail(messages.raw(gui.unavailableReason()));
        }
        org.bukkit.entity.Player viewer = org.bukkit.Bukkit.getPlayerExact(actor);
        if (viewer == null) {
            return Reply.fail(messages.raw("gui.players-only"));
        }
        gui.openOverview(viewer, actor);
        return Reply.ok(messages.raw("gui.opening"));
    }

    // ── 補全輔助 ──────────────────────────────────────────────────────────

    /**
     * Plugin names at the target position, flags after it.
     *
     * Shared by the five single-target state changes, which all take exactly one
     * target and then only flags.
     */
    private List<String> completeTarget(Args args) {
        return args.size() <= 2 ? lookup.suggest(args.at(1)) : Args.FLAG_SUGGESTIONS;
    }

    // ── 有子動作的指令 ────────────────────────────────────────────────────

    private Reply runProtect(String actor, Args args) {
        String action = args.at(1).toLowerCase(Locale.ROOT);
        return switch (action) {
            case "", "list" -> protection.list();
            case "add" -> protection.add(actor, args.joinedFrom(2));
            case "remove" -> protection.remove(actor, args.joinedFrom(2));
            default -> Reply.fail(messages.raw("protect.usage", "command", args.label()));
        };
    }

    private Reply runGroup(String actor, Args args) {
        String action = args.at(1).toLowerCase(Locale.ROOT);
        return switch (action) {
            case "", "list" -> groups.list();
            case "info" -> groups.info(args.at(2));
            case "enable", "disable", "reload" ->
                groups.act(actor, action, args.at(2), args.confirmed(), args.forced());
            default -> Reply.fail(messages.raw("group.usage", "command", args.label()));
        };
    }

    private Reply runCommandControl(String actor, Args args) {
        if (!"unregister".equalsIgnoreCase(args.at(1))) {
            return Reply.fail(messages.raw("command-index.usage", "command", args.label()));
        }
        return commandIndex.unregister(actor, args.at(2), args.confirmed());
    }

    private Reply runConfig(String actor, Args args) {
        String action = args.at(1).toLowerCase(Locale.ROOT);
        return switch (action) {
            case "reload" -> configReload.reload(actor);
            case "check" -> reports.configCheck();
            default -> Reply.fail(messages.raw("config.usage", "command", args.label()));
        };
    }

}
