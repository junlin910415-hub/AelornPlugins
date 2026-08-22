package tw.linsy.aelorn.worlds;

import java.io.File;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.lib.AelornLib;
import tw.linsy.aelorn.lib.sched.Schedulers;
import tw.linsy.aelorn.lib.text.Messages;

/**
 * Folia-native multi-world manager.
 *
 * Responsibilities are split into services that each own one concern:
 * {@link WorldVisualService} pushes declared settings onto live worlds,
 * {@link ChunkOptimizer} watches chunk pressure, {@link WorldTransferService}
 * moves players between worlds without a synchronous chunk load,
 * {@link WorldRuleService} enforces per-world player rules,
 * {@link EntityPurgeService} and {@link WorldHealthService} handle entity load.
 *
 * <p>Configuration is read into two immutable snapshots ({@link GlobalSettings},
 * {@link WorldRegistry}) plus a {@link Messages} catalog. Nothing outside a reload
 * ever touches YAML, and every reader sees a fully-built snapshot.
 */
public final class AelornWorldsPlugin extends JavaPlugin implements Listener {

    private static final String MESSAGES_FILE = "messages.yml";

    /** Data folder of the plugin this one replaced; its settings are adopted on first run. */
    private static final String LEGACY_DATA_FOLDER = "WorldLoaderX";

    private static volatile AelornWorldsPlugin instance;

    /**
     * Serialises everything that reads or writes config.yml on disk.
     *
     * <p>{@code /aw setspawn} and {@code /aw reload} both run their IO off the region
     * threads, and both touch the same file. Interleaved, one can write a config the
     * other has already replaced in memory — or worse, save a half-reloaded document
     * over an admin's annotated file. Neither is rare on a busy staff team, and the
     * damage is silent until someone notices a world definition missing.
     */
    private final Object configFileLock = new Object();

    private volatile GlobalSettings globals = GlobalSettings.defaults();
    private volatile WorldRegistry registry = WorldRegistry.empty();
    private volatile Messages messages;

    /** Folia scheduling, obtained from the core so every Aelorn plugin hops threads the same way. */
    private Schedulers schedulers;

    private WorldVisualService visualService;
    private ChunkOptimizer chunkOptimizer;
    private WorldTransferService transferService;
    private WorldTransferMenu transferMenu;
    private EntityPurgeService purgeService;
    private WorldHealthService healthService;
    private WorldRuleService ruleService;

    /**
     * The running plugin instance, or {@code null} before enable / after disable.
     * Other plugins should null-check rather than assume availability.
     */
    public static @Nullable AelornWorldsPlugin instance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        adoptLegacyDataFolder();
        saveDefaultConfig();

        this.schedulers = AelornLib.require().schedulersFor(this);
        this.globals = GlobalSettings.from(getConfig(), getLogger());
        this.messages = Messages.load(this, MESSAGES_FILE, globals.textFormat());
        this.registry = WorldRegistry.parse(getConfig().getConfigurationSection("worlds"), globals, getLogger())
            .refreshed();
        warnOnOutdatedConfig();

        this.visualService = new WorldVisualService(this);
        this.chunkOptimizer = new ChunkOptimizer(this);
        this.transferService = new WorldTransferService(this);
        this.transferMenu = new WorldTransferMenu(this);
        this.purgeService = new EntityPurgeService(this);
        this.healthService = new WorldHealthService(this);
        this.ruleService = new WorldRuleService(this);

        chunkOptimizer.reconfigure(registry, globals);
        purgeService.reconfigure(registry, globals);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(chunkOptimizer, this);
        // No listener for the transfer menu: it is built on the core's Menu, whose
        // clicks are routed by the one inventory listener the core registers.
        getServer().getPluginManager().registerEvents(ruleService, this);
        registerCommand();

        attemptDynamicLoadIfRequested();
        visualService.applyAll(registry, globals);
        logSummary();
    }

    @Override
    public void onDisable() {
        // Listeners first: a handler that fires while the services below are being
        // torn down would see half-cleared state, and on a plugin-manager reload it
        // would be running against a class loader that is about to go away.
        HandlerList.unregisterAll((Plugin) this);
        if (purgeService != null) {
            purgeService.shutdown();
        }
        if (schedulers != null) {
            // The purge loop cancels its own task; this catches everything else still
            // queued — a reload's async parse, a pending transfer announcement.
            schedulers.cancelAll();
        }
        if (chunkOptimizer != null) {
            chunkOptimizer.reset();
        }
        if (transferService != null) {
            transferService.reset();
        }
        if (ruleService != null) {
            ruleService.reset();
        }
        instance = null;
    }

    public GlobalSettings globals() {
        return globals;
    }

    Messages messages() {
        return messages;
    }

    Schedulers schedulers() {
        return schedulers;
    }

    /**
     * Carries settings over from the WorldLoaderX data folder the first time this
     * runs under the new name.
     *
     * A rename would otherwise silently hand the admin a fresh default config —
     * every world definition, spawn point and entry rule gone, with the old file
     * sitting untouched one directory away. Files are copied, never moved, so the
     * old folder stays as a rollback.
     */
    private void adoptLegacyDataFolder() {
        Path target = getDataFolder().toPath();
        Path legacy = target.resolveSibling(LEGACY_DATA_FOLDER);
        if (Files.exists(target.resolve("config.yml")) || !Files.isDirectory(legacy)) {
            return;
        }
        try {
            Files.createDirectories(target);
            int copied = 0;
            try (var entries = Files.list(legacy)) {
                for (Path source : entries.toList()) {
                    if (!Files.isRegularFile(source) || !source.toString().endsWith(".yml")) {
                        continue;
                    }
                    Files.copy(source, target.resolve(source.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
            if (copied > 0) {
                getLogger().info("已從舊資料夾 plugins/" + LEGACY_DATA_FOLDER + " 沿用 "
                    + copied + " 個設定檔；舊資料夾保留未動,可作為回退。");
            }
        } catch (IOException failure) {
            getLogger().log(Level.WARNING,
                "無法從 plugins/" + LEGACY_DATA_FOLDER + " 沿用設定,將使用預設值。", failure);
        }
    }

    WorldRegistry registry() {
        return registry;
    }

    ChunkOptimizer chunkOptimizer() {
        return chunkOptimizer;
    }

    WorldTransferService transferService() {
        return transferService;
    }

    WorldTransferMenu transferMenu() {
        return transferMenu;
    }

    EntityPurgeService purgeService() {
        return purgeService;
    }

    WorldHealthService healthService() {
        return healthService;
    }

    WorldRuleService ruleService() {
        return ruleService;
    }

    /** Re-applies difficulty, PVP, visual, border and spawn settings to every live world. */
    void applyWorldSettings() {
        visualService.applyAll(registry, globals);
        ruleService.applyToOnlinePlayers();
    }

    /**
     * Reloads config.yml and messages.yml off the main thread, then swaps in the
     * new snapshots on the global region scheduler. Parsing never blocks a region
     * thread, and readers always see a fully-built registry.
     */
    void reloadAsync(CommandSender feedback) {
        schedulers().async(() -> {
            GlobalSettings newGlobals;
            Messages newMessages;
            WorldRegistry parsed;
            try {
                synchronized (configFileLock) {
                    File file = new File(getDataFolder(), "config.yml");
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                    newGlobals = GlobalSettings.from(yaml, getLogger());
                    newMessages = Messages.load(this, MESSAGES_FILE, newGlobals.textFormat());
                    parsed = WorldRegistry.parse(yaml.getConfigurationSection("worlds"),
                        newGlobals, getLogger());
                    // Re-reads the same file from disk, so it belongs here rather than
                    // on the region thread that swaps the snapshots in: it is blocking
                    // IO, and it has to happen under the same lock as the write path or
                    // getConfig() can end up older than what setspawn is about to save.
                    reloadConfig();
                }
            } catch (RuntimeException failure) {
                getLogger().log(Level.SEVERE, "重新載入設定失敗，維持原有設定。", failure);
                messages.send(feedback, "reload.failed", "error", failure.getClass().getSimpleName());
                return;
            }

            schedulers().global(() -> {
                this.globals = newGlobals;
                this.messages = newMessages;
                this.registry = parsed.refreshed();
                warnOnOutdatedConfig();
                chunkOptimizer.reconfigure(registry, globals);
                purgeService.reconfigure(registry, globals);
                visualService.applyAll(registry, globals);
                ruleService.applyToOnlinePlayers();
                newMessages.send(feedback, "reload.done",
                    "live", registry.live().size(), "pending", registry.pending().size());
            });
        });
    }

    /**
     * Persists a world's spawn back into config.yml and reloads.
     *
     * Paper's YAML writer keeps comments on existing keys, so the annotated config
     * survives the round trip. The write itself is IO, so it happens off the region
     * thread.
     */
    void saveSpawnPoint(WorldProfile profile, Location location, CommandSender feedback) {
        String path = "worlds." + profile.name() + ".spawn";
        // Read the coordinates off the caller's thread; the Location belongs to the
        // player's region and must not be dereferenced from the IO thread.
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        float yaw = location.getYaw();
        float pitch = location.getPitch();

        schedulers().async(() -> {
            try {
                synchronized (configFileLock) {
                    // Mutate the live document, not a detached copy: Paper's writer
                    // preserves comments on keys that already exist, which is what
                    // keeps the annotated config readable after a round trip.
                    ConfigurationSection config = getConfig();
                    config.set(path + ".x", x);
                    config.set(path + ".y", y);
                    config.set(path + ".z", z);
                    config.set(path + ".yaw", (double) yaw);
                    config.set(path + ".pitch", (double) pitch);
                    saveConfig();
                }
            } catch (RuntimeException failure) {
                getLogger().log(Level.SEVERE, "寫入 config.yml 失敗: " + path, failure);
                messages.send(feedback, "setspawn.failed", "world", profile.alias());
                return;
            }
            messages.send(feedback, "setspawn.done",
                "world", profile.alias(),
                "x", Math.round(x), "y", Math.round(y), "z", Math.round(z));
            reloadAsync(feedback);
        });
    }

    /** A world appearing later still gets its configured settings. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        WorldProfile profile = registry.byName(world.getName()).orElse(null);
        if (profile == null) {
            return;
        }
        this.registry = registry.refreshed();
        // A freshly loaded world replays its spawn area in one burst. That is a
        // one-off cost, not thrash, so the churn detector restarts its grace period.
        chunkOptimizer.noteWorldLoaded(world);
        chunkOptimizer.reconfigure(registry, globals);
        purgeService.reconfigure(registry, globals);
        visualService.apply(world, profile, globals);
        getLogger().info("世界 " + world.getName() + " 已載入，設定已套用。");
    }

    /**
     * Releases everything keyed by a world that is going away.
     *
     * <p>Small on its own — a handful of counters per world — but these maps are keyed
     * by world UUID and nothing else ever removed from them, so a server that cycles
     * worlds accumulates them for as long as it runs.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        UUID worldId = event.getWorld().getUID();
        chunkOptimizer.forgetWorld(worldId);
        purgeService.forgetWorld(worldId);
        this.registry = registry.refreshed();
        purgeService.reconfigure(registry, globals);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        transferService.forget(event.getPlayer().getUniqueId());
        ruleService.forget(event.getPlayer().getUniqueId());
    }

    private void registerCommand() {
        PluginCommand command = getCommand("aelornworlds");
        if (command == null) {
            getLogger().severe("plugin.yml 缺少 aelornworlds 指令定義。");
            return;
        }
        AelornWorldsCommand executor = new AelornWorldsCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /**
     * A config written for an older schema still loads — every new section has a
     * default — but the admin should know which features are sitting unused.
     */
    private void warnOnOutdatedConfig() {
        if (globals.configVersion() >= GlobalSettings.EXPECTED_CONFIG_VERSION) {
            return;
        }
        getLogger().warning("config.yml 的 config-version 為 " + globals.configVersion()
            + "，目前版本為 " + GlobalSettings.EXPECTED_CONFIG_VERSION
            + "。現有設定仍可運作，但新功能都停在預設值；請參考 plugin-sources 中的 config.yml 補上需要的區段。"
            + "第 5 版新增：chunk.churn 改以「重複載入次數」判定並加上啟動寬限期、"
            + "chunk.item-cap.rescan-cooldown-seconds、health.census-cooldown-seconds、"
            + "rules.enforce-entry-on-join 與 rules.entry-fallback-world、"
            + "每個世界的 arrival.command-cooldown-seconds。");
    }

    /**
     * Honours the legacy {@code attempt-dynamic-load} flag. Folia cannot create
     * worlds at runtime, so this reports the failure clearly instead of silently
     * leaving the worlds missing.
     */
    private void attemptDynamicLoadIfRequested() {
        if (!globals.attemptDynamicLoad() || registry.pending().isEmpty()) {
            return;
        }
        getLogger().info("attempt-dynamic-load 已啟用，嘗試載入 " + registry.pending().size() + " 個世界…");
        for (WorldProfile profile : registry.pending()) {
            try {
                World created = Bukkit.createWorld(new org.bukkit.WorldCreator(profile.name())
                    .environment(profile.environment()));
                if (created == null) {
                    getLogger().warning("世界 " + profile.name() + " 動態載入回傳 null。");
                }
            } catch (Throwable unsupported) {
                getLogger().warning("Folia 不支援執行期動態載入世界（" + profile.name() + "）："
                    + unsupported.getClass().getSimpleName()
                    + "；請改為在伺服器啟動前放置世界資料夾。");
                return;
            }
        }
        this.registry = registry.refreshed();
    }

    private void logSummary() {
        getLogger().info("AelornWorlds v" + getPluginMeta().getVersion() + " 已啟用："
            + registry.live().size() + " 個世界可用，"
            + registry.pending().size() + " 個保留在磁碟等待相容方案。");
    }
}
