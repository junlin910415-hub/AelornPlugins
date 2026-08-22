package tw.linsy.aelorn.worlds;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.lib.text.Messages;

/**
 * {@code /worldloaderx} — inspection, live tuning and transfer.
 *
 * Every string sent from here is a key into messages.yml; this class only decides
 * which key and which placeholders. Anything that reads world state does so on the
 * right thread, and anything long-running (census, purge) reports back
 * asynchronously rather than blocking the caller.
 */
final class AelornWorldsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
        "menu", "status", "list", "tp", "spawn", "info", "who", "chunks", "health",
        "rules", "border", "purge", "save", "setspawn", "apply", "reload");

    /** Subcommands whose second argument is a world name. */
    private static final List<String> WORLD_ARG_SUBCOMMANDS =
        List.of("tp", "spawn", "info", "purge", "health", "rules", "border", "save", "setspawn");

    private static final List<String> BORDER_ACTIONS = List.of("size", "center", "reset");

    private final AelornWorldsPlugin plugin;

    /**
     * Last census per sender name, so {@code /aw health} cannot be held down.
     *
     * <p>Even behind {@link Permissions#INSPECT} the command is worth rate limiting:
     * a stuck macro or an impatient operator can otherwise stack one task per loaded
     * chunk per invocation, and the scans queue behind each other on the region
     * threads rather than replacing one another.
     */
    private final Map<String, Long> lastCensusAt = new ConcurrentHashMap<>();

    AelornWorldsCommand(AelornWorldsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        String subcommand = args.length == 0
            ? (sender instanceof Player ? "menu" : "status")
            : args[0].toLowerCase(Locale.ROOT);

        switch (subcommand) {
            case "menu" -> openMenu(sender, label);
            case "status", "list" -> sendStatus(sender);
            case "tp" -> transfer(sender, args, label);
            case "spawn" -> spawn(sender, args);
            case "info" -> sendWorldInfo(sender, args, label);
            case "who" -> sendWho(sender);
            case "chunks" -> sendChunkMetrics(sender);
            case "health" -> sendHealth(sender, args);
            case "rules" -> sendRules(sender, args, label);
            case "border" -> border(sender, args, label);
            case "purge" -> purge(sender, args, label);
            case "save" -> save(sender, args, label);
            case "setspawn" -> setSpawn(sender, args);
            case "apply" -> apply(sender);
            case "reload" -> reload(sender);
            // The list is built from SUBCOMMANDS so the help text cannot drift from
            // what the switch actually handles.
            default -> plugin.messages().send(sender, "command.usage",
                "label", label, "subcommands", String.join("|", SUBCOMMANDS));
        }
        return true;
    }

    private void openMenu(CommandSender sender, String label) {
        if (sender instanceof Player player) {
            plugin.transferMenu().open(player, 0);
        } else {
            plugin.messages().send(sender, "command.player-only", "label", label);
        }
    }

    /** {@code /wx tp <world> [player]} — the third argument needs admin. */
    private void transfer(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            plugin.messages().send(sender, "tp.usage", "label", label);
            return;
        }
        WorldProfile profile = requireProfile(sender, args[1]);
        if (profile == null) {
            return;
        }

        Player target;
        if (args.length >= 3) {
            if (!require(sender, Permissions.TRANSFER_OTHERS)) {
                return;
            }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.messages().send(sender, "command.unknown-player", "player", args[2]);
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            plugin.messages().send(sender, "command.player-only", "label", label);
            return;
        }

        WorldTransferService.Result result = plugin.transferService().transfer(target, profile);
        if (!result.started()) {
            plugin.messages().send(sender, result.messageKey(), "world", profile.alias());
        } else if (!target.equals(sender)) {
            plugin.messages().send(sender, "tp.sent-other",
                "player", target.getName(), "world", profile.alias());
        }
    }

    /** {@code /wx spawn [world]} — the configured landing spot, same world allowed. */
    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player-only", "label", "wx");
            return;
        }
        WorldProfile profile = args.length >= 2
            ? requireProfile(sender, args[1])
            : plugin.registry().byWorld(player.getWorld());
        if (profile == null) {
            if (args.length < 2) {
                plugin.messages().send(sender, "spawn.unmanaged-world");
            }
            return;
        }
        WorldTransferService.Result result = plugin.transferService().sendToSpawn(player, profile);
        if (!result.started()) {
            plugin.messages().send(sender, result.messageKey(), "world", profile.alias());
        }
    }

    private void sendStatus(CommandSender sender) {
        WorldRegistry registry = plugin.registry();
        Messages messages = plugin.messages();
        messages.send(sender, "status.header");
        for (WorldProfile profile : registry.live()) {
            World world = Bukkit.getWorld(profile.name());
            messages.send(sender, "status.line",
                "world", profile.name(),
                "alias", profile.alias(),
                "environment", profile.environment().name(),
                "players", world == null ? 0 : world.getPlayers().size());
        }
        if (registry.live().isEmpty()) {
            messages.send(sender, "status.none");
        }
        if (!registry.pending().isEmpty()) {
            messages.send(sender, "status.pending-header");
            for (WorldProfile profile : registry.pending()) {
                messages.send(sender, "status.pending-line",
                    "world", profile.name(),
                    "alias", profile.alias(),
                    "environment", profile.environment().name());
            }
        }
        messages.send(sender, "status.footer",
            "live", registry.live().size(), "pending", registry.pending().size());
    }

    private void sendWorldInfo(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            plugin.messages().send(sender, "info.usage", "label", label);
            return;
        }
        WorldProfile profile = requireProfile(sender, args[1]);
        if (profile == null) {
            return;
        }
        Messages messages = plugin.messages();
        World world = Bukkit.getWorld(profile.name());
        String unset = messages.raw("menu.value.unset");

        messages.send(sender, "info.header", "world", profile.name(), "alias", profile.alias());
        messages.send(sender, "info.environment",
            "environment", profile.environment().name(),
            "status", messages.raw(world == null ? "menu.value.unloaded" : "menu.value.loaded"));
        if (profile.difficulty() != null) {
            messages.send(sender, "info.difficulty",
                "configured", profile.difficulty().name(),
                "actual", world == null ? unset : world.getDifficulty().name());
        }
        if (profile.pvp() != null) {
            messages.send(sender, "info.pvp",
                "configured", profile.pvp(),
                "actual", world == null ? unset : String.valueOf(livePvp(world)));
        }
        if (profile.spawn() != null) {
            WorldProfile.SpawnPoint spawn = profile.spawn();
            messages.send(sender, "info.spawn",
                "x", Math.round(spawn.x()), "y", Math.round(spawn.y()), "z", Math.round(spawn.z()));
        }
        if (world != null) {
            messages.send(sender, "info.live",
                "players", world.getPlayers().size(), "chunks", world.getLoadedChunks().length);
            messages.send(sender, "info.distances",
                "view", world.getViewDistance(), "simulation", world.getSimulationDistance());
            WorldBorder border = world.getWorldBorder();
            messages.send(sender, "info.border",
                "size", Math.round(border.getSize()),
                "x", Math.round(border.getCenter().getX()),
                "z", Math.round(border.getCenter().getZ()));
        }

        WorldProfile.EntryRules entry = profile.entry();
        if (!entry.isOpen()) {
            messages.send(sender, "info.entry",
                "limit", entry.hasLimit() ? String.valueOf(entry.playerLimit())
                    : messages.raw("menu.value.unlimited"),
                "blocked", entry.blockedOrigins().isEmpty() ? unset : String.join(", ", entry.blockedOrigins()),
                "allowed", entry.allowedOrigins().isEmpty() ? unset : String.join(", ", entry.allowedOrigins()),
                "permission", entry.hasPermission() ? entry.permission() : unset);
        }
        if (!profile.spawnSettings().isEmpty()) {
            messages.send(sender, "info.spawn-control",
                "limits", profile.spawnSettings().limits().size(),
                "rates", profile.spawnSettings().tickRates().size());
        }
        if (profile.purge().autoEnabled()) {
            messages.send(sender, "info.auto-purge",
                "interval", profile.purge().intervalSeconds(),
                "keep", profile.purge().keepPerChunk());
        }
    }

    /** Per-world player rules, so an admin can see what a world enforces without reading YAML. */
    private void sendRules(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            plugin.messages().send(sender, "rules.usage", "label", label);
            return;
        }
        WorldProfile profile = requireProfile(sender, args[1]);
        if (profile == null) {
            return;
        }
        Messages messages = plugin.messages();
        WorldProfile.PlayerRules rules = profile.playerRules();
        String unset = messages.raw("menu.value.unset");

        messages.send(sender, "rules.header", "world", profile.name(), "alias", profile.alias());
        // Portal rules live outside PlayerRules, so both have to be empty for "none".
        if (rules.isEmpty() && profile.portals().isOpen()) {
            messages.send(sender, "rules.none");
            return;
        }
        messages.send(sender, "rules.game-mode",
            "value", WorldRuleService.describeGameMode(rules.gameMode(), unset));
        messages.send(sender, "rules.flight",
            "value", rules.allowFlight() == null ? unset : String.valueOf(rules.allowFlight()));
        WorldProfile.VoidRule voidRule = rules.voidRule();
        messages.send(sender, "rules.void",
            "enabled", voidRule.enabled(),
            "y", voidRule.usesWorldMinimum() ? messages.raw("rules.void-auto") : voidRule.belowY(),
            "action", voidRule.action().name(),
            "target", voidRule.targetWorld().isEmpty() ? unset : voidRule.targetWorld());
        WorldProfile.RespawnRule respawn = rules.respawn();
        messages.send(sender, "rules.respawn",
            "world", respawn.world().isEmpty() ? profile.name() : respawn.world(),
            "configured", respawn.useConfiguredSpawn(),
            "bed", respawn.overrideBed());
        messages.send(sender, "rules.commands",
            "commands", rules.commands().isOpen() ? unset : String.join(", ", rules.commands().blocked()));
        WorldProfile.PortalRules portals = profile.portals();
        messages.send(sender, "rules.portals",
            "disabled", portals.disabled(),
            "targets", portals.targets().isEmpty() ? unset : describeTargets(portals));
    }

    private static String describeTargets(WorldProfile.PortalRules portals) {
        return portals.targets().entrySet().stream()
            .map(entry -> entry.getKey().name() + "→" + entry.getValue())
            .reduce((a, b) -> a + ", " + b)
            .orElse("-");
    }

    private void sendWho(CommandSender sender) {
        Messages messages = plugin.messages();
        messages.send(sender, "who.header");
        int total = 0;
        for (WorldProfile profile : plugin.registry().live()) {
            World world = Bukkit.getWorld(profile.name());
            if (world == null) {
                continue;
            }
            List<String> names = world.getPlayers().stream().map(Player::getName).sorted().toList();
            total += names.size();
            messages.send(sender, "who.line",
                "alias", profile.alias(),
                "count", names.size(),
                "players", names.isEmpty() ? messages.raw("who.empty")
                    : String.join(messages.raw("who.separator"), names));
        }
        messages.send(sender, "who.footer", "total", total);
    }

    private void sendChunkMetrics(CommandSender sender) {
        GlobalSettings globals = plugin.globals();
        Messages messages = plugin.messages();
        if (!globals.chunk().metricsEnabled()) {
            messages.send(sender, "chunks.disabled");
            return;
        }
        ChunkOptimizer optimizer = plugin.chunkOptimizer();
        messages.send(sender, "chunks.header",
            "window", globals.chunk().churnWindowSeconds(),
            "threshold", globals.chunk().churnWarnThreshold());
        for (WorldProfile profile : plugin.registry().live()) {
            World world = Bukkit.getWorld(profile.name());
            if (world == null) {
                continue;
            }
            ChunkOptimizer.Snapshot snapshot = optimizer.snapshot(world);
            messages.send(sender, "chunks.line",
                "world", profile.name(),
                "loads", snapshot.loads(),
                "unloads", snapshot.unloads(),
                "generated", snapshot.generated());
            messages.send(sender, "chunks.line-detail",
                "peak", snapshot.peakRedundantPerWindow(),
                "current", snapshot.currentWindowRedundant(),
                "culled", snapshot.itemsCulled());
        }
        messages.send(sender, "chunks.footer", "tracked", optimizer.trackedChunks());
    }

    /**
     * Entity census. The scan fans out across regions, so the reply arrives after
     * the command returns — one report line per world as each finishes.
     */
    private void sendHealth(CommandSender sender, String[] args) {
        if (!require(sender, Permissions.INSPECT)) {
            return;
        }
        Messages messages = plugin.messages();
        List<WorldProfile> targets = new ArrayList<>();
        if (args.length >= 2) {
            WorldProfile profile = requireProfile(sender, args[1]);
            if (profile == null) {
                return;
            }
            targets.add(profile);
        } else {
            targets.addAll(plugin.registry().live());
        }
        if (!claimCensus(sender)) {
            messages.send(sender, "health.cooldown",
                "seconds", plugin.globals().health().censusCooldownSeconds());
            return;
        }

        messages.send(sender, "health.start", "worlds", targets.size());
        GlobalSettings.HealthOptions thresholds = plugin.globals().health();
        for (WorldProfile profile : targets) {
            World world = Bukkit.getWorld(profile.name());
            if (world == null) {
                messages.send(sender, "world.not-loaded", "world", profile.alias());
                continue;
            }
            plugin.healthService().census(world, report ->
                plugin.schedulers().global(() -> reportHealth(sender, profile, report, thresholds)));
        }
    }

    private void reportHealth(CommandSender sender, WorldProfile profile,
                              WorldHealthService.Report report, GlobalSettings.HealthOptions thresholds) {
        Messages messages = plugin.messages();
        messages.send(sender, report.isOverThreshold(thresholds) ? "health.line-warn" : "health.line",
            "world", profile.name(),
            "alias", profile.alias(),
            "entities", report.entities(),
            "items", report.items(),
            "tiles", report.tileEntities(),
            "chunks", report.chunks(),
            "players", report.players(),
            "density", String.format(Locale.ROOT, "%.1f", report.entitiesPerChunk()));
        for (Map.Entry<SpawnCategory, Long> entry : report.byCategory().entrySet()) {
            messages.send(sender, "health.category",
                "category", entry.getKey().name(), "count", entry.getValue());
        }
    }

    private void purge(CommandSender sender, String[] args, String label) {
        if (!require(sender, Permissions.PURGE)) {
            return;
        }
        Messages messages = plugin.messages();
        if (args.length < 2) {
            messages.send(sender, "purge.usage", "label", label,
                "categories", joinCategories(plugin.globals().purge().defaultCategories()));
            return;
        }
        WorldProfile profile = requireProfile(sender, args[1]);
        if (profile == null) {
            return;
        }
        World world = requireLiveWorld(sender, profile);
        if (world == null) {
            return;
        }

        Set<SpawnCategory> categories = EnumSet.noneOf(SpawnCategory.class);
        for (int index = 2; index < args.length; index++) {
            SpawnCategory category = parseCategory(args[index]);
            if (category == null) {
                messages.send(sender, "purge.unknown-category", "category", args[index]);
                return;
            }
            categories.add(category);
        }

        Set<SpawnCategory> effective =
            categories.isEmpty() ? plugin.globals().purge().defaultCategories() : categories;
        messages.send(sender, "purge.start",
            "world", profile.alias(), "categories", joinCategories(effective));
        // A manual purge is a full sweep; keep-per-chunk is the scheduled cleanup's
        // budget, and an admin typing the command expects everything to go.
        plugin.purgeService().purge(world, categories, 0, removed ->
            plugin.schedulers().global(() ->
                messages.send(sender, "purge.done", "world", profile.alias(), "count", removed)));
    }

    /** {@code /wx save <world|*>} — flushes region files without waiting for autosave. */
    private void save(CommandSender sender, String[] args, String label) {
        if (!require(sender, Permissions.SAVE)) {
            return;
        }
        Messages messages = plugin.messages();
        if (args.length < 2) {
            messages.send(sender, "save.usage", "label", label);
            return;
        }
        if ("*".equals(args[1])) {
            List<WorldProfile> live = plugin.registry().live();
            plugin.schedulers().global(() -> {
                int saved = 0;
                for (WorldProfile profile : live) {
                    World world = Bukkit.getWorld(profile.name());
                    if (world != null) {
                        world.save();
                        saved++;
                    }
                }
                messages.send(sender, "save.all-done", "count", saved);
            });
            return;
        }
        WorldProfile profile = requireProfile(sender, args[1]);
        if (profile == null) {
            return;
        }
        World world = requireLiveWorld(sender, profile);
        if (world == null) {
            return;
        }
        plugin.schedulers().global(() -> {
            world.save();
            messages.send(sender, "save.done", "world", profile.alias());
        });
    }

    /**
     * Writes the sender's current position into config.yml as the world's spawn,
     * then reloads. Keeps admins out of the YAML file for the one value that is
     * genuinely painful to type by hand.
     */
    private void setSpawn(CommandSender sender, String[] args) {
        if (!require(sender, Permissions.EDIT)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player-only", "label", "wx");
            return;
        }
        WorldProfile profile = args.length >= 2
            ? requireProfile(sender, args[1])
            : plugin.registry().byWorld(player.getWorld());
        if (profile == null) {
            if (args.length < 2) {
                plugin.messages().send(sender, "spawn.unmanaged-world");
            }
            return;
        }
        Location location = player.getLocation();
        if (!location.getWorld().getName().equals(profile.name())) {
            plugin.messages().send(sender, "setspawn.wrong-world", "world", profile.alias());
            return;
        }
        plugin.saveSpawnPoint(profile, location, sender);
    }

    private void border(CommandSender sender, String[] args, String label) {
        Messages messages = plugin.messages();
        if (args.length < 2) {
            messages.send(sender, "border.usage", "label", label);
            return;
        }
        WorldProfile profile = requireProfile(sender, args[1]);
        if (profile == null) {
            return;
        }
        World world = requireLiveWorld(sender, profile);
        if (world == null) {
            return;
        }
        if (args.length == 2) {
            showBorder(sender, profile, world);
            return;
        }
        if (!require(sender, Permissions.EDIT)) {
            return;
        }
        applyBorderCommand(sender, profile, world, args, label);
    }

    private void showBorder(CommandSender sender, WorldProfile profile, World world) {
        plugin.schedulers().global(() -> {
            WorldBorder border = world.getWorldBorder();
            plugin.messages().send(sender, "border.info",
                "world", profile.alias(),
                "size", Math.round(border.getSize()),
                "x", Math.round(border.getCenter().getX()),
                "z", Math.round(border.getCenter().getZ()),
                "warning", border.getWarningDistance(),
                "damage", border.getDamageAmount());
        });
    }

    private void applyBorderCommand(CommandSender sender, WorldProfile profile, World world,
                                    String[] args, String label) {
        Messages messages = plugin.messages();
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "reset" -> plugin.schedulers().global(() -> {
                world.getWorldBorder().reset();
                messages.send(sender, "border.reset", "world", profile.alias());
            });
            case "size" -> {
                Double size = parseDouble(sender, args, 3, "border.usage", label);
                if (size == null) {
                    return;
                }
                // A typo here must not silently become an instant resize.
                Double transition = args.length >= 5
                    ? parseDouble(sender, args, 4, "border.usage", label) : Double.valueOf(0.0D);
                if (transition == null) {
                    return;
                }
                long seconds = Math.max(0L, transition.longValue());
                plugin.schedulers().global(() -> {
                    WorldBorder border = world.getWorldBorder();
                    double bounded = Math.min(Math.max(1.0D, size), border.getMaxSize());
                    if (seconds > 0L) {
                        // changeSize takes ticks; setSize(double, long) is deprecated for removal.
                        border.changeSize(bounded, seconds * 20L);
                    } else {
                        border.setSize(bounded);
                    }
                    messages.send(sender, "border.updated",
                        "world", profile.alias(), "size", Math.round(bounded), "seconds", seconds);
                });
            }
            case "center" -> {
                Double x = parseDouble(sender, args, 3, "border.usage", label);
                Double z = parseDouble(sender, args, 4, "border.usage", label);
                if (x == null || z == null) {
                    return;
                }
                plugin.schedulers().global(() -> {
                    world.getWorldBorder().setCenter(x, z);
                    messages.send(sender, "border.centered",
                        "world", profile.alias(), "x", Math.round(x), "z", Math.round(z));
                });
            }
            default -> messages.send(sender, "border.usage", "label", label);
        }
    }

    private void apply(CommandSender sender) {
        if (!require(sender, Permissions.ADMIN)) {
            return;
        }
        plugin.applyWorldSettings();
        plugin.messages().send(sender, "apply.done");
    }

    private void reload(CommandSender sender) {
        if (!require(sender, Permissions.ADMIN)) {
            return;
        }
        plugin.messages().send(sender, "reload.start");
        plugin.reloadAsync(sender);
    }

    // World#getPVP is deprecated with no API replacement; it is still the only
    // way to read the live per-world PVP flag.
    @SuppressWarnings("deprecation")
    private static boolean livePvp(World world) {
        return world.getPVP();
    }

    private @Nullable WorldProfile requireProfile(CommandSender sender, String name) {
        WorldProfile profile = plugin.registry().byName(name).orElse(null);
        if (profile == null) {
            plugin.messages().send(sender, "command.unknown-world", "world", name);
        }
        return profile;
    }

    private @Nullable World requireLiveWorld(CommandSender sender, WorldProfile profile) {
        World world = Bukkit.getWorld(profile.name());
        if (world == null) {
            plugin.messages().send(sender, "world.not-loaded", "world", profile.alias());
        }
        return world;
    }

    /**
     * Checks one node, reporting the refusal to the sender.
     *
     * <p>{@link Permissions#ADMIN} is declared the parent of every node below it in
     * plugin.yml, so a check for the narrow node is also satisfied by an existing
     * blanket admin grant — splitting the nodes takes nothing away from anyone.
     */
    private boolean require(CommandSender sender, String node) {
        if (Permissions.has(sender, node)) {
            return true;
        }
        plugin.messages().send(sender, "command.no-permission");
        return false;
    }

    /** True when this sender may start a census now; records the attempt if so. */
    private boolean claimCensus(CommandSender sender) {
        long cooldownMillis = plugin.globals().health().censusCooldownSeconds() * 1000L;
        if (cooldownMillis <= 0L) {
            return true;
        }
        long now = System.currentTimeMillis();
        // merge() decides and records in one atomic step: two commands arriving
        // together on different threads cannot both pass the check.
        Long kept = lastCensusAt.merge(sender.getName(), now,
            (previous, candidate) -> candidate - previous < cooldownMillis ? previous : candidate);
        return kept != null && kept == now;
    }

    private @Nullable Double parseDouble(CommandSender sender, String[] args, int index,
                                         String usageKey, String label) {
        if (index >= args.length) {
            plugin.messages().send(sender, usageKey, "label", label);
            return null;
        }
        try {
            return Double.parseDouble(args[index]);
        } catch (NumberFormatException invalid) {
            plugin.messages().send(sender, "command.not-a-number", "value", args[index]);
            return null;
        }
    }

    private static @Nullable SpawnCategory parseCategory(String raw) {
        try {
            return SpawnCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String joinCategories(Set<SpawnCategory> categories) {
        return categories.stream().map(Enum::name).sorted().reduce((a, b) -> a + ", " + b).orElse("-");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            // Only suggest what this sender could actually run: a completion list is
            // otherwise a directory of commands that answer "no permission".
            List<String> permitted = new ArrayList<>();
            for (String subcommand : SUBCOMMANDS) {
                if (Permissions.has(sender, permissionFor(subcommand))) {
                    permitted.add(subcommand);
                }
            }
            return filter(permitted, args[0]);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && WORLD_ARG_SUBCOMMANDS.contains(subcommand)) {
            List<String> names = new ArrayList<>(plugin.registry().names());
            if ("save".equals(subcommand)) {
                names.add("*");
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && "border".equals(subcommand)) {
            return filter(BORDER_ACTIONS, args[2]);
        }
        if (args.length == 3 && "tp".equals(subcommand)
            && Permissions.has(sender, Permissions.TRANSFER_OTHERS)) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList(), args[2]);
        }
        if (args.length >= 3 && "purge".equals(subcommand)) {
            List<String> categories = new ArrayList<>();
            for (SpawnCategory category : SpawnCategory.values()) {
                categories.add(category.name());
            }
            return filter(categories, args[args.length - 1]);
        }
        return List.of();
    }

    /**
     * The node a subcommand needs at minimum.
     *
     * <p>Kept beside {@link #SUBCOMMANDS} rather than derived at each call site, so a
     * new subcommand that forgets its check still shows up here as an obvious gap
     * instead of silently completing for everyone.
     */
    private static String permissionFor(String subcommand) {
        return switch (subcommand) {
            case "tp", "spawn" -> Permissions.TRANSFER;
            case "health" -> Permissions.INSPECT;
            case "purge" -> Permissions.PURGE;
            case "save" -> Permissions.SAVE;
            case "setspawn" -> Permissions.EDIT;
            case "apply", "reload" -> Permissions.ADMIN;
            // border reads with USE and only needs EDIT to change something.
            default -> Permissions.USE;
        };
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(candidate);
            }
        }
        return matches;
    }
}
