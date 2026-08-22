package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestDefinition;
import com.xuzhihuanjing.rpgcore.gui.QuestJournalHolder;
import com.xuzhihuanjing.rpgcore.gui.QuestJournalMenuService;
import com.xuzhihuanjing.rpgcore.quest.QuestService;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public final class QuestJournalMenuListener implements Listener {
   private final CharacterService characterService;
   private final QuestJournalMenuService menuService;
   private final QuestService questService;
   private final MessageBundle messages;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();

   public QuestJournalMenuListener(CharacterService characterService, QuestJournalMenuService menuService, QuestService questService, MessageBundle messages) {
      this.characterService = characterService;
      this.menuService = menuService;
      this.questService = questService;
      this.messages = messages;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      HumanEntity var4 = event.getWhoClicked();
      if (var4 instanceof Player player) {
         InventoryHolder var8 = event.getInventory().getHolder();
         if (var8 instanceof QuestJournalHolder holder) {
            event.setCancelled(true);
            if (holder.ownerId().equals(player.getUniqueId()) && event.getClickedInventory() == event.getInventory()) {
               CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
               if (character != null && holder.characterId().equals(character.id())) {
                  if (event.getRawSlot() == 45 && holder.page() > 0) {
                     this.menuService.open(player, holder.page() - 1);
                     return;
                  }

                  if (event.getRawSlot() == 53) {
                     this.menuService.open(player, holder.page() + 1);
                     return;
                  }

                  QuestDefinition quest = this.menuService.questAt(character, holder.page(), event.getRawSlot());
                  if (quest == null) {
                     return;
                  }

                  QuestService.Availability availability = this.questService.availability(character, quest);
                  if (availability == QuestService.Availability.ACTIVE) {
                     this.questService.track(player.getUniqueId(), quest.id());
                     player.sendMessage(this.messages.message(character.trackedQuestId().equals(quest.id()) ? "quest-untracked" : "quest-tracked", MessageBundle.value("quest", this.plain(quest.displayName()))));
                     this.menuService.open(player, holder.page());
                     return;
                  }

                  if (availability == QuestService.Availability.AVAILABLE) {
                     QuestService.AcceptResult result = this.questService.accept(player.getUniqueId(), quest);
                     if (result == QuestService.AcceptResult.ACCEPTED) {
                        player.sendMessage(this.messages.message("quest-accepted", MessageBundle.value("quest", this.plain(quest.displayName()))));
                     }

                     this.menuService.open(player, holder.page());
                     return;
                  }

                  player.sendMessage(this.messages.message(availability == QuestService.Availability.COMPLETED ? "quest-already-completed" : "quest-locked"));
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
      if (event.getInventory().getHolder() instanceof QuestJournalHolder) {
         event.setCancelled(true);
      }

   }

   private String plain(String value) {
      return this.miniMessage.stripTags(value);
   }
}
