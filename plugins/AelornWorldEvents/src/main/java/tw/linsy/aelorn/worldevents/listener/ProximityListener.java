package tw.linsy.aelorn.worldevents.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import tw.linsy.aelorn.worldevents.AelornWorldEventsPlugin;
import tw.linsy.aelorn.worldevents.config.EventCatalog;
import tw.linsy.aelorn.worldevents.config.EventSettings;
import tw.linsy.aelorn.worldevents.model.EventNode;
import tw.linsy.aelorn.worldevents.service.EventCoordinator;

/**
 * Drives event activation from player movement.
 *
 * Folia has no server-wide tick to sweep every player against every node, and
 * emulating one would mean touching players from the wrong region thread. Doing
 * the check where the movement already is keeps every read on the owning thread
 * and costs nothing when nobody moves.
 *
 * <p>Ordered cheapest-first, because this runs on the hottest event on the
 * server: a field read, then a block-boundary test, then a throttle, and only
 * then the spatial lookup.
 */
public final class ProximityListener implements Listener {

    private final AelornWorldEventsPlugin plugin;

    public ProximityListener(AelornWorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        EventCatalog catalog = plugin.catalog();
        EventSettings settings = catalog.settings();
        if (!settings.enabled() || catalog.size() == 0) {
            return;
        }
        if (!event.hasChangedBlock()) {
            return;
        }

        Location to = event.getTo();
        if (to.getWorld() == null) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (!plugin.tracker().updateAndShouldCheck(player.getUniqueId(), to, now,
            settings.checkIntervalMillis())) {
            return;
        }
        if (Bukkit.getOnlinePlayers().size() < settings.minimumOnlinePlayers()) {
            return;
        }

        for (EventNode node : catalog.nearby(to.getWorld().getName(), to.getX(), to.getZ())) {
            if (!node.isWithinActivationRange(to.getX(), to.getZ())) {
                continue;
            }
            if (plugin.coordinator().tryActivate(node, now) == EventCoordinator.Result.STARTED) {
                plugin.dispatcher().start(node);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.tracker().forget(event.getPlayer().getUniqueId());
    }
}
