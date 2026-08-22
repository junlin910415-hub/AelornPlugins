package tw.linsy.aelorn.worldevents;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import tw.linsy.aelorn.lib.AelornLib;
import tw.linsy.aelorn.lib.sched.Schedulers;
import tw.linsy.aelorn.lib.text.Messages;
import tw.linsy.aelorn.worldevents.command.WorldEventsCommand;
import tw.linsy.aelorn.worldevents.config.EventCatalog;
import tw.linsy.aelorn.worldevents.listener.ProximityListener;
import tw.linsy.aelorn.worldevents.service.EventCoordinator;
import tw.linsy.aelorn.worldevents.service.EventDispatcher;
import tw.linsy.aelorn.worldevents.service.PlayerTracker;

/**
 * Proximity-triggered world events.
 *
 * Wiring only: build the services, register them, schedule the pruner. Every
 * decision lives in a service, every string in messages.yml, every threshold in
 * config.yml.
 *
 * <p>Rewritten from a decompiled 1.0.0. The behaviour is the same; what changed
 * is that a bad config no longer stops the plugin enabling, the hot movement
 * path writes one map instead of two, and nothing player-facing is compiled in.
 */
public final class AelornWorldEventsPlugin extends JavaPlugin {

    public static final String PERMISSION_ADMIN = "aelorn.worldevents.admin";

    private static final String MESSAGES_FILE = "messages.yml";

    /** How often expired activation windows are swept, in ticks. */
    private static final long PRUNE_INTERVAL_TICKS = 600L;

    private volatile EventCatalog catalog = EventCatalog.empty();
    private volatile Messages messages;

    private Schedulers schedulers;
    private EventCoordinator coordinator;
    private PlayerTracker tracker;
    private EventDispatcher dispatcher;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        AelornLib core = AelornLib.require();
        this.schedulers = core.schedulersFor(this);
        this.messages = Messages.load(this, MESSAGES_FILE, core.textFormat());
        this.catalog = EventCatalog.load(getConfig(), getLogger());

        this.coordinator = new EventCoordinator(catalog.settings());
        this.tracker = new PlayerTracker();
        this.dispatcher = new EventDispatcher(this, coordinator, tracker);
        reportTemplateProblems();

        getServer().getPluginManager().registerEvents(new ProximityListener(this), this);
        registerCommand();
        schedulers.globalRepeating(() -> coordinator.prune(System.currentTimeMillis()),
            PRUNE_INTERVAL_TICKS, PRUNE_INTERVAL_TICKS);

        getLogger().info("AelornWorldEvents v" + getPluginMeta().getVersion() + " 已啟用："
            + catalog.size() + " 個事件節點，接近判定由玩家移動驅動。");
    }

    @Override
    public void onDisable() {
        if (tracker != null) {
            tracker.clear();
        }
        if (coordinator != null) {
            coordinator.reset();
        }
    }

    public EventCatalog catalog() {
        return catalog;
    }

    public Messages messages() {
        return messages;
    }

    public Schedulers schedulers() {
        return schedulers;
    }

    public EventCoordinator coordinator() {
        return coordinator;
    }

    public PlayerTracker tracker() {
        return tracker;
    }

    public EventDispatcher dispatcher() {
        return dispatcher;
    }

    /**
     * Re-reads config.yml and messages.yml off the main thread, then swaps both
     * in together on the global region. Activation state survives — a reload
     * should not hand every node a free re-trigger.
     */
    public void reloadAsync(CommandSender feedback) {
        messages.send(feedback, "reload.start");
        schedulers.async(() -> {
            EventCatalog parsed;
            Messages reloaded;
            try {
                YamlConfiguration yaml =
                    YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
                parsed = EventCatalog.load(yaml, getLogger());
                reloaded = Messages.load(this, MESSAGES_FILE, AelornLib.require().textFormat());
            } catch (RuntimeException failure) {
                getLogger().log(Level.SEVERE, "重新載入失敗，維持原有設定。", failure);
                messages.send(feedback, "reload.failed", "error", failure.getClass().getSimpleName());
                return;
            }
            schedulers.global(() -> {
                reloadConfig();
                this.catalog = parsed;
                this.messages = reloaded;
                coordinator.reconfigure(parsed.settings());
                reportTemplateProblems();
                reloaded.send(feedback, "reload.done", "nodes", parsed.size());
            });
        });
    }

    /**
     * A start command missing a placeholder only shows up when an event fires,
     * which could be hours later and looks like a broken encounter. Say it now.
     */
    private void reportTemplateProblems() {
        List<String> problems = EventDispatcher.validateTemplate(catalog.settings());
        for (String problem : problems) {
            getLogger().warning(problem);
        }
    }

    private void registerCommand() {
        PluginCommand command = getCommand("aelornworldevents");
        if (command == null) {
            getLogger().severe("plugin.yml 缺少 aelornworldevents 指令定義。");
            return;
        }
        WorldEventsCommand executor = new WorldEventsCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
