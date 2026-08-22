package tw.linsy.aelornholograms;

import java.io.File;
import net.kyori.adventure.text.minimessage.MiniMessage;
import tw.linsy.aelorn.lib.AelornLib;
import tw.linsy.aelorn.lib.sched.Schedulers;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AelornHologramsPlugin extends JavaPlugin {

    private static AelornHologramsPlugin instance;
    private volatile DisplaySettings settings;
    private PlaceholderBridge placeholderBridge;
    private TextFormatter textFormatter;
    private HologramStore store;
    private HologramManager hologramManager;
    private MiniMessage miniMessage;
    /** Folia scheduling from the core. */
    private Schedulers schedulers;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        settings = DisplaySettings.from(getConfig());
        miniMessage = MiniMessage.miniMessage();
        schedulers = AelornLib.require().schedulersFor(this);
        placeholderBridge = new PlaceholderBridge(this);
        textFormatter = new TextFormatter(this);
        store = new HologramStore(this);
        hologramManager = new HologramManager(this, store);
        importDecentOnce();
        hologramManager.reload();
        registerCommand();
        getLogger().info("AelornHolograms enabled with " + hologramManager.holograms().size() + " holograms.");
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) {
            hologramManager.shutdown();
        }
    }

    public static AelornHologramsPlugin instance() {
        return instance;
    }

    /** Immutable config snapshot; refreshed by {@link #reloadSettings()}. */
    DisplaySettings settings() {
        return settings;
    }

    /** Called after reloadConfig() so the render path picks up new defaults. */
    void reloadSettings() {
        settings = DisplaySettings.from(getConfig());
    }

    public PlaceholderBridge placeholderBridge() {
        return placeholderBridge;
    }

    public TextFormatter textFormatter() {
        return textFormatter;
    }

    public HologramManager hologramManager() {
        return hologramManager;
    }

    public HologramStore store() {
        return store;
    }

    public Schedulers schedulers() {
        return schedulers;
    }

    public MiniMessage miniMessage() {
        return miniMessage;
    }

    public int defaultDisplayRange() {
        return settings.displayRange();
    }

    public int defaultUpdateRange() {
        return settings.updateRange();
    }

    public int defaultUpdateInterval() {
        return settings.updateInterval();
    }

    public int minUpdateInterval() {
        return settings.minUpdateInterval();
    }

    public double defaultLineHeight() {
        return settings.lineHeight();
    }

    private void registerCommand() {
        PluginCommand command = getCommand("aelornholograms");
        if (command == null) {
            getLogger().severe("Command aelornholograms is missing from plugin.yml.");
            return;
        }
        AelornHologramsCommand executor = new AelornHologramsCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void importDecentOnce() {
        if (!getConfig().getBoolean("compatibility.import-decentholograms-on-first-start", true)) {
            return;
        }
        File marker = new File(getDataFolder(), ".decent-imported");
        if (marker.exists()) {
            return;
        }
        int imported = store.importDecentHolograms(false);
        try {
            getDataFolder().mkdirs();
            marker.createNewFile();
        } catch (Exception markerFailure) {
            getLogger().warning("Could not write DecentHolograms import marker: " + markerFailure.getMessage());
        }
        if (imported > 0) {
            getLogger().info("Imported " + imported + " DecentHolograms hologram files.");
        }
    }
}
