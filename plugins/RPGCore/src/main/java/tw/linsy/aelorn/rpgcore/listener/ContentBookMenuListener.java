package tw.linsy.aelorn.rpgcore.listener;

import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestDefinition;
import tw.linsy.aelorn.rpgcore.gui.ContentBookEntry;
import tw.linsy.aelorn.rpgcore.gui.ContentBookHolder;
import tw.linsy.aelorn.rpgcore.gui.ContentBookMenuService;
import tw.linsy.aelorn.rpgcore.quest.QuestService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

public final class ContentBookMenuListener implements Listener {
   private final CharacterService characterService;
   private final ContentBookMenuService menuService;
   private final QuestService questService;
   private final MessageBundle messages;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();

   public ContentBookMenuListener(CharacterService characterService, ContentBookMenuService menuService, QuestService questService, MessageBundle messages) {
      this.characterService = characterService;
      this.menuService = menuService;
      this.questService = questService;
      this.messages = messages;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      HumanEntity var4 = event.getWhoClicked();
      if (var4 instanceof Player player) {
         InventoryHolder var6 = event.getInventory().getHolder();
         if (var6 instanceof ContentBookHolder holder) {
            event.setCancelled(true);
            if (holder.ownerId().equals(player.getUniqueId()) && event.getClickedInventory() == event.getInventory()) {
               CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
               if (character != null && holder.characterId().equals(character.id())) {
                  if (this.handleNavigation(event, player, holder)) {
                     return;
                  }

                  ContentBookEntry entry = this.menuService.entryAt(character, holder, event.getRawSlot());
                  if (entry == null) {
                     return;
                  }

                  if (entry.type() == ContentBookEntry.Type.DISCOVERY) {
                     this.menuService.trackDiscovery(player, character, entry.discovery());
                     return;
                  }

                  this.handleQuest(player, character, holder, entry.quest());
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
      if (event.getInventory().getHolder() instanceof ContentBookHolder) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.menuService.clearPreferences(event.getPlayer().getUniqueId());
   }

   private boolean handleNavigation(InventoryClickEvent event, Player player, ContentBookHolder holder) {
      switch (event.getRawSlot()) {
         case 45:
            if (holder.page() > 0) {
               this.menuService.open(player, holder.page() - 1);
            }

            return true;
         case 46:
         case 48:
         case 49:
         case 50:
         case 52:
         default:
            return false;
         case 47:
            this.menuService.cycleFilter(player, event.isRightClick() ? -1 : 1);
            return true;
         case 51:
            this.menuService.cycleSort(player, event.isRightClick() ? -1 : 1);
            return true;
         case 53:
            this.menuService.open(player, holder.page() + 1);
            return true;
      }
   }

   private void handleQuest(Player player, CharacterProfile character, ContentBookHolder holder, QuestDefinition quest) {
      QuestService.Availability availability = this.questService.availability(character, quest);
      if (availability == QuestService.Availability.ACTIVE) {
         boolean wasTracked = character.trackedQuestId().equals(quest.id());
         this.questService.track(player.getUniqueId(), quest.id());
         player.sendMessage(this.messages.message(wasTracked ? "quest-untracked" : "quest-tracked", MessageBundle.value("quest", this.plain(quest.displayName()))));
         this.menuService.open(player, holder.page());
      } else if (availability == QuestService.Availability.AVAILABLE) {
         QuestService.AcceptResult result = this.questService.accept(player, quest);
         if (result == QuestService.AcceptResult.ACCEPTED) {
            player.sendMessage(this.messages.message("quest-accepted", MessageBundle.value("quest", this.plain(quest.displayName()))));
         }

         this.menuService.open(player, holder.page());
      } else {
         player.sendMessage(this.messages.message(availability == QuestService.Availability.COMPLETED ? "quest-already-completed" : "quest-locked"));
      }
   }

   private String plain(String value) {
      return this.miniMessage.stripTags(value);
   }
}
