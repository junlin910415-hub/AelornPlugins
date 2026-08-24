package tw.linsy.aelorn.rpgcore.listener;

import tw.linsy.aelorn.rpgcore.combat.CharacterActivationService;
import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.stats.PrimarySkill;
import tw.linsy.aelorn.rpgcore.gui.SkillCrystalHolder;
import tw.linsy.aelorn.rpgcore.gui.SkillCrystalMenuService;
import tw.linsy.aelorn.rpgcore.progression.PrimarySkillService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public final class SkillCrystalMenuListener implements Listener {
   private final CharacterService characterService;
   private final PrimarySkillService primarySkillService;
   private final SkillCrystalMenuService menuService;
   private final CharacterActivationService activationService;
   private final MessageBundle messages;

   public SkillCrystalMenuListener(CharacterService characterService, PrimarySkillService primarySkillService, SkillCrystalMenuService menuService, CharacterActivationService activationService, MessageBundle messages) {
      this.characterService = characterService;
      this.primarySkillService = primarySkillService;
      this.menuService = menuService;
      this.activationService = activationService;
      this.messages = messages;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      HumanEntity var4 = event.getWhoClicked();
      if (var4 instanceof Player player) {
         InventoryHolder var9 = event.getInventory().getHolder();
         if (var9 instanceof SkillCrystalHolder holder) {
            event.setCancelled(true);
            if (holder.ownerId().equals(player.getUniqueId()) && event.getClickedInventory() == event.getInventory()) {
               CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
               if (character != null && holder.characterId().equals(character.id())) {
                  if (event.getRawSlot() == 40) {
                     if (!event.isShiftClick()) {
                        player.sendMessage(this.messages.message("skill-reset-confirm"));
                        return;
                     }

                     int removed = this.primarySkillService.reset(player.getUniqueId());
                     this.characterService.activeCharacter(player.getUniqueId()).ifPresent((updated) -> this.activationService.refreshAttributes(player, updated));
                     player.sendMessage(this.messages.message("skill-reset", MessageBundle.value("points", Integer.toString(removed))));
                     this.menuService.open(player);
                     return;
                  }

                  PrimarySkill skill = this.menuService.skillAt(event.getRawSlot());
                  if (skill == null) {
                     return;
                  }

                  int amount = event.isShiftClick() ? 5 : 1;
                  int requested = event.isRightClick() ? -amount : amount;
                  int changed = this.primarySkillService.change(player.getUniqueId(), skill, requested);
                  this.characterService.activeCharacter(player.getUniqueId()).ifPresent((updated) -> this.activationService.refreshAttributes(player, updated));
                  if (changed == 0) {
                     player.sendMessage(this.messages.message(requested > 0 ? "skill-no-points" : "skill-no-invested-points"));
                  } else {
                     player.sendMessage(this.messages.message(changed > 0 ? "skill-point-added" : "skill-point-removed", MessageBundle.value("skill", skill.displayName()), MessageBundle.value("points", Integer.toString(Math.abs(changed)))));
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
      if (event.getInventory().getHolder() instanceof SkillCrystalHolder) {
         event.setCancelled(true);
      }

   }
}
