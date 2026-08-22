package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.gui.MainMenuService;
import com.xuzhihuanjing.rpgcore.gui.ProfessionMenuHolder;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public final class ProfessionMenuListener implements Listener {
   private final CharacterService characterService;
   private final MainMenuService mainMenuService;

   public ProfessionMenuListener(CharacterService characterService, MainMenuService mainMenuService) {
      this.characterService = characterService;
      this.mainMenuService = mainMenuService;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      HumanEntity var4 = event.getWhoClicked();
      if (var4 instanceof Player player) {
         InventoryHolder var5 = event.getInventory().getHolder();
         if (var5 instanceof ProfessionMenuHolder holder) {
            event.setCancelled(true);
            if (holder.ownerId().equals(player.getUniqueId()) && event.getClickedInventory() == event.getInventory()) {
               CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
               if (character != null && holder.characterId().equals(character.id())) {
                  if (event.getRawSlot() == 49) {
                     this.mainMenuService.open(player);
                  }

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
      if (event.getInventory().getHolder() instanceof ProfessionMenuHolder) {
         event.setCancelled(true);
      }

   }
}
