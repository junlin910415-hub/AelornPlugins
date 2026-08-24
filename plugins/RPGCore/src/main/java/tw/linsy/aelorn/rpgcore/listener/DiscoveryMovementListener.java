package tw.linsy.aelorn.rpgcore.listener;

import tw.linsy.aelorn.rpgcore.discovery.DiscoveryService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class DiscoveryMovementListener implements Listener {
   private static final long CHECK_INTERVAL_MILLIS = 400L;
   private final DiscoveryService discoveryService;
   private final Map<UUID, Long> lastChecks = new ConcurrentHashMap();

   public DiscoveryMovementListener(DiscoveryService discoveryService) {
      this.discoveryService = discoveryService;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      Location from = event.getFrom();
      Location to = event.getTo();
      if (to != null && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())) {
         long now = System.currentTimeMillis();
         long previous = (Long)this.lastChecks.getOrDefault(event.getPlayer().getUniqueId(), 0L);
         if (now - previous >= 400L) {
            this.lastChecks.put(event.getPlayer().getUniqueId(), now);
            this.discoveryService.inspectLocation(event.getPlayer());
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.lastChecks.remove(event.getPlayer().getUniqueId());
   }
}
