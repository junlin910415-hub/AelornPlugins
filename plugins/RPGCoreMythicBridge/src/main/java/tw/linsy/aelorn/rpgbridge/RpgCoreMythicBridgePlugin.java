package tw.linsy.aelorn.rpgbridge;

import tw.linsy.aelorn.rpgcore.api.event.RpgAbilityCastEvent;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import tw.linsy.aelorn.mythiccore.api.MythicCoreApi;

public final class RpgCoreMythicBridgePlugin extends JavaPlugin implements Listener {

    /** Full stat inspections on the damage path are sampled at most this often. */
    private static final long FULL_INSPECTION_INTERVAL_MILLIS = 250L;

    private MythicCoreApi mythicCore;
    private MythicItemInspector inspector;
    private volatile int registeredStats;

    private final AtomicLong abilityCasts = new AtomicLong();
    private final AtomicLong damageEventsWithMythicItems = new AtomicLong();
    private final AtomicLong lastFullInspectionAt = new AtomicLong();
    private volatile MythicItemInspector.ItemInspection lastMythicItem = MythicItemInspector.ItemInspection.empty();

    @Override
    public void onEnable() {
        PluginManager pluginManager = getServer().getPluginManager();
        Plugin mythicCorePlugin = pluginManager.getPlugin("MythicCore");
        if (!(mythicCorePlugin instanceof MythicCoreApi api)) {
            getLogger().severe("MythicCore is present but does not expose MythicCoreApi. Bridge disabled.");
            pluginManager.disablePlugin(this);
            return;
        }

        this.mythicCore = api;
        this.inspector = new MythicItemInspector(api, getLogger());
        this.registeredStats = RpgCoreStatRegistry.register(api);
        pluginManager.registerEvents(this, this);

        PluginCommand command = getCommand("rpgbridge");
        if (command != null) {
            BridgeCommand executor = new BridgeCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("Bridge enabled: MythicCore " + versionOf("MythicCore")
            + " -> AelornItems " + versionOf("AelornItems")
            + " -> RPGCore " + versionOf("RPGCore")
            + "; registered " + registeredStats + " shared RPG stats.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRpgAbilityCast(RpgAbilityCastEvent event) {
        abilityCasts.incrementAndGet();
        sampleHeldItem(event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        // Hot path: one id read per hit; the full stat-map inspection is throttled.
        if (inspector.hasMythicData(player.getInventory().getItemInMainHand())) {
            damageEventsWithMythicItems.incrementAndGet();
            sampleHeldItem(player);
        }
    }

    /** Records the held item as the latest observed Mythic item, at most once per interval. */
    private void sampleHeldItem(Player player) {
        long now = System.currentTimeMillis();
        long last = lastFullInspectionAt.get();
        if (now - last < FULL_INSPECTION_INTERVAL_MILLIS || !lastFullInspectionAt.compareAndSet(last, now)) {
            return;
        }
        MythicItemInspector.ItemInspection item = inspector.inspect(player.getInventory().getItemInMainHand());
        if (item.present()) {
            lastMythicItem = item;
        }
    }

    MythicCoreApi mythicCore() {
        return mythicCore;
    }

    MythicItemInspector inspector() {
        return inspector;
    }

    int registeredStats() {
        return registeredStats;
    }

    int reregisterStats() {
        int count = RpgCoreStatRegistry.register(mythicCore);
        this.registeredStats = count;
        return count;
    }

    long abilityCastCount() {
        return abilityCasts.get();
    }

    long mythicDamageCount() {
        return damageEventsWithMythicItems.get();
    }

    MythicItemInspector.ItemInspection lastMythicItem() {
        return lastMythicItem;
    }

    String statusOf(String pluginName) {
        Plugin plugin = getServer().getPluginManager().getPlugin(pluginName);
        if (plugin == null) {
            return "missing";
        }
        return (plugin.isEnabled() ? "enabled " : "disabled ") + plugin.getDescription().getVersion();
    }

    private String versionOf(String pluginName) {
        Plugin plugin = getServer().getPluginManager().getPlugin(pluginName);
        return plugin == null ? "missing" : plugin.getDescription().getVersion();
    }
}
