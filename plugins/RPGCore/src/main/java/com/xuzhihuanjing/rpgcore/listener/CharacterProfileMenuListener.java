package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.gui.AbilityTreeMenuService;
import com.xuzhihuanjing.rpgcore.gui.CharacterMenuService;
import com.xuzhihuanjing.rpgcore.gui.CharacterProfileMenuHolder;
import com.xuzhihuanjing.rpgcore.gui.CharacterProfileMenuService;
import com.xuzhihuanjing.rpgcore.gui.ContentBookMenuService;
import com.xuzhihuanjing.rpgcore.gui.MainMenuService;
import com.xuzhihuanjing.rpgcore.gui.PartyMenuService;
import com.xuzhihuanjing.rpgcore.gui.ProfessionMenuService;
import com.xuzhihuanjing.rpgcore.gui.QuestJournalMenuService;
import com.xuzhihuanjing.rpgcore.gui.SkillCrystalMenuService;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public final class CharacterProfileMenuListener implements Listener {
   private final CharacterService characterService;
   private final CharacterProfileMenuService profileMenuService;
   private final MainMenuService mainMenuService;
   private final CharacterMenuService characterMenuService;
   private final SkillCrystalMenuService skillCrystalMenuService;
   private final AbilityTreeMenuService abilityTreeMenuService;
   private final ProfessionMenuService professionMenuService;
   private final QuestJournalMenuService questJournalMenuService;
   private final ContentBookMenuService contentBookMenuService;
   private final PartyMenuService partyMenuService;

   public CharacterProfileMenuListener(CharacterService characterService, CharacterProfileMenuService profileMenuService, MainMenuService mainMenuService, CharacterMenuService characterMenuService, SkillCrystalMenuService skillCrystalMenuService, AbilityTreeMenuService abilityTreeMenuService, ProfessionMenuService professionMenuService, QuestJournalMenuService questJournalMenuService, ContentBookMenuService contentBookMenuService, PartyMenuService partyMenuService) {
      this.characterService = characterService;
      this.profileMenuService = profileMenuService;
      this.mainMenuService = mainMenuService;
      this.characterMenuService = characterMenuService;
      this.skillCrystalMenuService = skillCrystalMenuService;
      this.abilityTreeMenuService = abilityTreeMenuService;
      this.professionMenuService = professionMenuService;
      this.questJournalMenuService = questJournalMenuService;
      this.contentBookMenuService = contentBookMenuService;
      this.partyMenuService = partyMenuService;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      HumanEntity var4 = event.getWhoClicked();
      if (var4 instanceof Player player) {
         InventoryHolder var5 = event.getView().getTopInventory().getHolder();
         if (var5 instanceof CharacterProfileMenuHolder holder) {
            event.setCancelled(true);
            if (holder.ownerId().equals(player.getUniqueId()) && event.getClickedInventory() == event.getView().getTopInventory()) {
               CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
               if (character != null && holder.characterId().equals(character.id())) {
                  switch (event.getRawSlot()) {
                     case 20:
                        this.questJournalMenuService.open(player);
                     case 21:
                     case 23:
                     case 25:
                     case 27:
                     case 28:
                     case 29:
                     case 31:
                     case 33:
                     case 34:
                     case 35:
                     case 36:
                     case 37:
                     case 38:
                     case 39:
                     case 40:
                     case 41:
                     case 42:
                     case 43:
                     case 44:
                     case 46:
                     case 47:
                     case 48:
                     case 50:
                     case 51:
                     case 52:
                     default:
                        break;
                     case 22:
                        this.abilityTreeMenuService.open(player);
                        break;
                     case 24:
                        this.skillCrystalMenuService.open(player);
                        break;
                     case 26:
                        this.professionMenuService.open(player);
                        break;
                     case 30:
                        this.contentBookMenuService.open(player);
                        break;
                     case 32:
                        this.partyMenuService.open(player);
                        break;
                     case 45:
                        this.profileMenuService.open(player);
                        break;
                     case 49:
                        this.mainMenuService.open(player);
                        break;
                     case 53:
                        this.characterMenuService.openCharacterSelector(player);
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
      if (event.getView().getTopInventory().getHolder() instanceof CharacterProfileMenuHolder) {
         event.setCancelled(true);
      }

   }
}
