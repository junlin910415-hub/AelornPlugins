package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.gui.MainMenuService;
import com.xuzhihuanjing.rpgcore.gui.WayfinderCodexService;
import com.xuzhihuanjing.rpgcore.integration.mmoitems.MmoItemsBridge;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class WayfinderCodexListener implements Listener {
   private final WayfinderCodexService codexService;
   private final MainMenuService menuService;
   private final MmoItemsBridge mmoItems;

   public WayfinderCodexListener(WayfinderCodexService codexService, MainMenuService menuService, MmoItemsBridge mmoItems) {
      this.codexService = codexService;
      this.menuService = menuService;
      this.mmoItems = mmoItems;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onInteract(PlayerInteractEvent event) {
      if (event.getHand() == EquipmentSlot.HAND && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && this.codexService.isCodex(event.getItem())) {
         event.setCancelled(true);
         this.menuService.open(event.getPlayer());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onDrop(PlayerDropItemEvent event) {
      if (this.codexService.isCodex(event.getItemDrop().getItemStack())) {
         event.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onDeath(PlayerDeathEvent event) {
      var var10000 = event.getDrops();
      WayfinderCodexService var10001 = this.codexService;
      Objects.requireNonNull(var10001);
      var10000.removeIf(var10001::isCodex);
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPickup(EntityPickupItemEvent event) {
      if (event.getEntity() instanceof Player && this.codexService.isCodex(event.getItem().getItemStack())) {
         event.setCancelled(true);
         event.getItem().remove();
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onSwapHands(PlayerSwapHandItemsEvent event) {
      Player player = event.getPlayer();
      if (this.codexService.hasCodex(player) && ownsSwapHotkey(event.isCancelled(), player.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING, this.mmoItems.inspect(player.getInventory().getItemInMainHand()).isPresent())) {
         event.setCancelled(true);
         this.menuService.open(player);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onInventoryClick(InventoryClickEvent event) {
      HumanEntity var3 = event.getWhoClicked();
      if (var3 instanceof Player player) {
         boolean hotbarCodex = event.getHotbarButton() >= 0 && this.codexService.isCodex(player.getInventory().getItem(event.getHotbarButton()));
         if (this.codexService.isCodex(event.getCurrentItem()) || this.codexService.isCodex(event.getCursor()) || hotbarCodex) {
            event.setCancelled(true);
         }

      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onInventoryDrag(InventoryDragEvent event) {
      if (this.codexService.isCodex(event.getOldCursor())) {
         event.setCancelled(true);
      }

   }

   static boolean ownsSwapHotkey(boolean alreadyHandled, boolean normalPlayerView, boolean holdingManagedMmoItem) {
      return !alreadyHandled && normalPlayerView && !holdingManagedMmoItem;
   }
}
