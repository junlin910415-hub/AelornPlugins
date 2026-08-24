package tw.linsy.aelorn.rpgcore.listener;

import tw.linsy.aelorn.rpgcore.combat.CharacterActivationService;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.platform.RpgScheduler;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class EquipmentRefreshListener implements Listener {
   private final CharacterService characterService;
   private final CharacterActivationService activationService;
   private final RpgScheduler scheduler;

   public EquipmentRefreshListener(CharacterService characterService, CharacterActivationService activationService, RpgScheduler scheduler) {
      this.characterService = characterService;
      this.activationService = activationService;
      this.scheduler = scheduler;
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onClick(InventoryClickEvent event) {
      HumanEntity var3 = event.getWhoClicked();
      if (var3 instanceof Player player) {
         this.scheduleRefresh(player);
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onDrag(InventoryDragEvent event) {
      HumanEntity var3 = event.getWhoClicked();
      if (var3 instanceof Player player) {
         this.scheduleRefresh(player);
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onHeld(PlayerItemHeldEvent event) {
      this.scheduleRefresh(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onSwap(PlayerSwapHandItemsEvent event) {
      this.scheduleRefresh(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onDrop(PlayerDropItemEvent event) {
      this.scheduleRefresh(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onPickup(EntityPickupItemEvent event) {
      LivingEntity var3 = event.getEntity();
      if (var3 instanceof Player player) {
         this.scheduleRefresh(player);
      }

   }

   private void scheduleRefresh(Player player) {
      this.scheduler.runEntityLater(player, () -> {
         CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
         if (character != null) {
            this.activationService.refreshAttributes(player, character);
         }

      }, () -> {
      }, 1L);
   }
}
