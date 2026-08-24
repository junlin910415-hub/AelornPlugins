package tw.linsy.aelorn.rpgcore.listener;

import tw.linsy.aelorn.rpgcore.gui.IdentificationMenuService;
import tw.linsy.aelorn.rpgcore.monster.MythicMobsBridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class IdentificationNpcListener implements Listener {
   private final MythicMobsBridge mythicMobsBridge;
   private final IdentificationMenuService menuService;
   private final String mythicMobId;

   public IdentificationNpcListener(MythicMobsBridge mythicMobsBridge, IdentificationMenuService menuService, String mythicMobId) {
      this.mythicMobsBridge = mythicMobsBridge;
      this.menuService = menuService;
      this.mythicMobId = mythicMobId;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onInteract(PlayerInteractEntityEvent event) {
      if (event.getHand() == EquipmentSlot.HAND && this.mythicMobsBridge.isMythicMob(event.getRightClicked(), this.mythicMobId)) {
         event.setCancelled(true);
         this.menuService.open(event.getPlayer());
      }
   }
}
