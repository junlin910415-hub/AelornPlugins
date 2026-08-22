package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.combat.TrainingWeaponService;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.gui.AbilityTreeMenuService;
import com.xuzhihuanjing.rpgcore.gui.CharacterMenuService;
import com.xuzhihuanjing.rpgcore.gui.CharacterProfileMenuService;
import com.xuzhihuanjing.rpgcore.gui.ContentBookMenuService;
import com.xuzhihuanjing.rpgcore.gui.MainMenuHolder;
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

public final class MainMenuListener implements Listener {
   private final CharacterService characterService;
   private final CharacterMenuService characterMenuService;
   private final CharacterProfileMenuService characterProfileMenuService;
   private final ContentBookMenuService contentBookMenuService;
   private final SkillCrystalMenuService skillCrystalMenuService;
   private final AbilityTreeMenuService abilityTreeMenuService;
   private final ProfessionMenuService professionMenuService;
   private final QuestJournalMenuService questJournalMenuService;
   private final PartyMenuService partyMenuService;
   private final TrainingWeaponService trainingWeaponService;
   private final MessageBundle messages;

   public MainMenuListener(CharacterService characterService, CharacterMenuService characterMenuService, CharacterProfileMenuService characterProfileMenuService, ContentBookMenuService contentBookMenuService, SkillCrystalMenuService skillCrystalMenuService, AbilityTreeMenuService abilityTreeMenuService, ProfessionMenuService professionMenuService, QuestJournalMenuService questJournalMenuService, PartyMenuService partyMenuService, TrainingWeaponService trainingWeaponService, MessageBundle messages) {
      this.characterService = characterService;
      this.characterMenuService = characterMenuService;
      this.characterProfileMenuService = characterProfileMenuService;
      this.contentBookMenuService = contentBookMenuService;
      this.skillCrystalMenuService = skillCrystalMenuService;
      this.abilityTreeMenuService = abilityTreeMenuService;
      this.professionMenuService = professionMenuService;
      this.questJournalMenuService = questJournalMenuService;
      this.partyMenuService = partyMenuService;
      this.trainingWeaponService = trainingWeaponService;
      this.messages = messages;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      HumanEntity var4 = event.getWhoClicked();
      if (var4 instanceof Player player) {
         InventoryHolder var5 = event.getInventory().getHolder();
         if (var5 instanceof MainMenuHolder holder) {
            event.setCancelled(true);
            if (holder.ownerId().equals(player.getUniqueId()) && event.getClickedInventory() == event.getInventory()) {
               CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
               if (character != null && holder.characterId().equals(character.id())) {
                  switch (event.getRawSlot()) {
                     case 4:
                        this.characterProfileMenuService.open(player);
                        break;
                     case 20:
                        this.contentBookMenuService.open(player);
                        break;
                     case 22:
                        this.skillCrystalMenuService.open(player);
                        break;
                     case 24:
                        this.abilityTreeMenuService.open(player);
                        break;
                     case 31:
                        this.professionMenuService.open(player);
                        break;
                     case 38:
                        this.questJournalMenuService.open(player);
                        break;
                     case 40:
                        this.partyMenuService.open(player);
                        break;
                     case 42:
                        this.trainingWeaponService.ensure(player, character);
                        player.sendMessage(this.messages.message("training-weapon-restored"));
                        break;
                     case 49:
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
      if (event.getInventory().getHolder() instanceof MainMenuHolder) {
         event.setCancelled(true);
      }

   }
}
