package tw.linsy.aelorn.rpgcore.listener;

import tw.linsy.aelorn.rpgcore.ability.AbilityTreeService;
import tw.linsy.aelorn.rpgcore.ability.SpawnSafeZoneService;
import tw.linsy.aelorn.rpgcore.config.AbilityTreeRegistry;
import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.domain.ability.AbilityTreeNodeDefinition;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.gui.AbilityTreeHolder;
import tw.linsy.aelorn.rpgcore.gui.AbilityTreeMenuService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public final class AbilityTreeMenuListener implements Listener {
   private final CharacterService characterService;
   private final AbilityTreeRegistry treeRegistry;
   private final AbilityTreeService treeService;
   private final AbilityTreeMenuService menuService;
   private final SpawnSafeZoneService safeZoneService;
   private final MessageBundle messages;

   public AbilityTreeMenuListener(CharacterService characterService, AbilityTreeRegistry treeRegistry, AbilityTreeService treeService, AbilityTreeMenuService menuService, SpawnSafeZoneService safeZoneService, MessageBundle messages) {
      this.characterService = characterService;
      this.treeRegistry = treeRegistry;
      this.treeService = treeService;
      this.menuService = menuService;
      this.safeZoneService = safeZoneService;
      this.messages = messages;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      HumanEntity var4 = event.getWhoClicked();
      if (var4 instanceof Player player) {
         InventoryHolder var7 = event.getInventory().getHolder();
         if (var7 instanceof AbilityTreeHolder holder) {
            event.setCancelled(true);
            if (holder.ownerId().equals(player.getUniqueId()) && event.getClickedInventory() == event.getInventory()) {
               CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
               if (character != null && holder.characterId().equals(character.id())) {
                  if (event.getRawSlot() == 49) {
                     if (!event.isShiftClick()) {
                        player.sendMessage(this.messages.message("ability-tree-reset-confirm"));
                        return;
                     }

                     if (!this.safeZoneService.isSafe(player)) {
                        player.sendMessage(this.messages.message("ability-tree-safe-zone"));
                        return;
                     }

                     this.treeService.reset(player.getUniqueId());
                     player.sendMessage(this.messages.message("ability-tree-reset"));
                     this.menuService.open(player);
                     return;
                  }

                  AbilityTreeNodeDefinition node = (AbilityTreeNodeDefinition)this.treeRegistry.nodeAt(character.classId(), event.getRawSlot()).orElse(null);
                  if (node == null) {
                     return;
                  }

                  if (!this.safeZoneService.isSafe(player)) {
                     player.sendMessage(this.messages.message("ability-tree-safe-zone"));
                     return;
                  }

                  AbilityTreeService.UnlockResult result = this.treeService.unlock(player.getUniqueId(), node);
                  switch (result) {
                     case SUCCESS -> player.sendMessage(this.messages.message("ability-tree-unlocked", MessageBundle.value("node", this.plain(node.displayName()))));
                     case ALREADY_UNLOCKED -> player.sendMessage(this.messages.message("ability-tree-already-unlocked"));
                     case LEVEL_REQUIRED -> player.sendMessage(this.messages.message("ability-tree-requires-level", MessageBundle.value("level", Integer.toString(node.minimumLevel()))));
                     case PREREQUISITE_REQUIRED -> player.sendMessage(this.messages.message("ability-tree-requires-node"));
                     case NOT_ENOUGH_POINTS -> player.sendMessage(this.messages.message("ability-tree-no-points"));
                     case WRONG_CLASS -> player.sendMessage(this.messages.message("ability-tree-unavailable"));
                  }

                  this.menuService.open(player);
                  return;
               }

               player.closeInventory();
               return;
            }

            return;
         }
      }

   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (event.getInventory().getHolder() instanceof AbilityTreeHolder) {
         event.setCancelled(true);
      }

   }

   private String plain(String miniMessage) {
      return miniMessage.replaceAll("<[^>]+>", "");
   }
}
