package tw.linsy.aelorn.rpgcore.listener;

import tw.linsy.aelorn.rpgcore.combat.HudNotificationService;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentRequirementResult;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class EquipmentUsageListener implements Listener {
   private static final long WARNING_INTERVAL_NANOS = 750000000L;
   private final CharacterService characterService;
   private final EquipmentService equipmentService;
   private final HudNotificationService notifications;
   private final Map<UUID, Long> warningNanos = new ConcurrentHashMap();

   public EquipmentUsageListener(CharacterService characterService, EquipmentService equipmentService, HudNotificationService notifications) {
      this.characterService = characterService;
      this.equipmentService = equipmentService;
      this.notifications = notifications;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEvent event) {
      if (this.deny(event.getPlayer(), event.getItem(), true)) {
         event.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onAttack(EntityDamageByEntityEvent event) {
      Entity var3 = event.getDamager();
      if (var3 instanceof Player player) {
         if (this.deny(player, player.getInventory().getItemInMainHand(), true)) {
            event.setCancelled(true);
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onShoot(EntityShootBowEvent event) {
      LivingEntity var3 = event.getEntity();
      if (var3 instanceof Player player) {
         if (this.deny(player, event.getBow(), true)) {
            event.setCancelled(true);
         }
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onHeld(PlayerItemHeldEvent event) {
      this.deny(event.getPlayer(), event.getPlayer().getInventory().getItem(event.getNewSlot()), false);
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.warningNanos.remove(event.getPlayer().getUniqueId());
   }

   private boolean deny(Player player, ItemStack item, boolean blocking) {
      if (player.hasPermission("rpgcore.equipment.bypass")) {
         return false;
      } else {
         CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
         EquipmentRequirementResult result = this.equipmentService.requirements(item, character);
         if (result.usable()) {
            return false;
         } else {
            long now = System.nanoTime();
            Long previous = (Long)this.warningNanos.put(player.getUniqueId(), now);
            if (previous == null || now - previous >= 750000000L) {
               this.notifications.show(player.getUniqueId(), Component.text("無法使用：", NamedTextColor.RED).append(Component.text(result.message(), NamedTextColor.WHITE)));
            }

            return blocking;
         }
      }
   }
}
