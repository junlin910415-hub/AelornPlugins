package tw.linsy.aelorn.rpgcore.listener;

import tw.linsy.aelorn.rpgcore.combat.HudNotificationService;
import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.domain.profession.ProfessionType;
import tw.linsy.aelorn.rpgcore.progression.ProfessionGainResult;
import tw.linsy.aelorn.rpgcore.progression.ProfessionService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerFishEvent.State;
import org.bukkit.inventory.ItemStack;

public final class ProfessionExperienceListener implements Listener {
   private final CharacterService characterService;
   private final ProfessionService professionService;
   private final HudNotificationService notifications;
   private final MessageBundle messages;

   public ProfessionExperienceListener(CharacterService characterService, ProfessionService professionService, HudNotificationService notifications, MessageBundle messages) {
      this.characterService = characterService;
      this.professionService = professionService;
      this.notifications = notifications;
      this.messages = messages;
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onBlockBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      Material material = event.getBlock().getType();
      String name = material.name();
      if (this.isMiningResource(name)) {
         this.grant(player, ProfessionType.MINING, name.endsWith("_ORE") ? 8L : 2L);
      } else if (this.isWoodcuttingResource(name)) {
         this.grant(player, ProfessionType.WOODCUTTING, 5L);
      } else {
         if (this.isFarmResource(material, event.getBlock().getBlockData())) {
            this.grant(player, ProfessionType.FARMING, 4L);
         }

      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onFish(PlayerFishEvent event) {
      if (event.getState() == State.CAUGHT_FISH) {
         this.grant(event.getPlayer(), ProfessionType.FISHING, 10L);
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onCraft(CraftItemEvent event) {
      HumanEntity var3 = event.getWhoClicked();
      if (var3 instanceof Player player) {
         ItemStack var5 = event.getRecipe().getResult();
         ProfessionType profession = this.professionForCraft(var5.getType());
         if (profession != null) {
            this.grant(player, profession, Math.max(4L, (long)var5.getAmount() * 4L));
         }

      }
   }

   private void grant(Player player, ProfessionType profession, long experience) {
      if (!this.characterService.activeCharacter(player.getUniqueId()).isEmpty()) {
         ProfessionGainResult result = this.professionService.grantExperience(player.getUniqueId(), profession, experience);
         if (result.awardedExperience() > 0L) {
            this.notifications.show(player.getUniqueId(), this.messages.content("profession-xp", MessageBundle.value("profession", profession.displayName()), MessageBundle.value("experience", Long.toString(result.awardedExperience()))));
            if (result.leveledUp()) {
               player.sendMessage(this.messages.message("profession-level-up", MessageBundle.value("profession", profession.displayName()), MessageBundle.value("level", Integer.toString(result.level()))));
            }

         }
      }
   }

   private boolean isMiningResource(String name) {
      return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS") || name.equals("STONE") || name.equals("DEEPSLATE") || name.equals("TUFF") || name.equals("ANDESITE") || name.equals("DIORITE") || name.equals("GRANITE") || name.equals("CALCITE") || name.equals("BLACKSTONE");
   }

   private boolean isWoodcuttingResource(String name) {
      return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE");
   }

   private boolean isFarmResource(Material material, BlockData data) {
      if (data instanceof Ageable crop) {
         if (crop.getAge() < crop.getMaximumAge()) {
            return false;
         }
      }

      boolean var10000;
      switch (material) {
         case WHEAT:
         case CARROTS:
         case POTATOES:
         case BEETROOTS:
         case NETHER_WART:
         case COCOA:
         case MELON:
         case PUMPKIN:
         case SUGAR_CANE:
         case BAMBOO:
         case CACTUS:
            var10000 = true;
            break;
         default:
            var10000 = false;
      }

      return var10000;
   }

   private ProfessionType professionForCraft(Material material) {
      String name = material.name();
      if (!name.contains("BOOK") && !name.contains("PAPER") && !name.contains("MAP")) {
         if (!name.contains("AMETHYST") && !name.contains("DIAMOND") && !name.contains("EMERALD") && !name.contains("GOLD")) {
            if (!name.contains("POTION") && !name.contains("BLAZE_POWDER") && !name.contains("FERMENTED") && !name.contains("GLISTERING")) {
               if (!material.isEdible() && !name.contains("CAKE") && !name.contains("COOKIE") && !name.contains("STEW") && !name.contains("PIE")) {
                  if (!name.endsWith("_SWORD") && !name.endsWith("_AXE") && !name.equals("TRIDENT") && !name.equals("MACE")) {
                     if (!name.endsWith("_HELMET") && !name.endsWith("_CHESTPLATE") && !name.endsWith("_LEGGINGS") && !name.endsWith("_BOOTS") && !name.equals("SHIELD")) {
                        if (!name.contains("LEATHER") && !name.contains("WOOL") && !name.contains("CARPET") && !name.contains("BANNER")) {
                           return !name.contains("PLANKS") && !name.contains("WOOD") && !name.contains("LOG") && !name.contains("BOW") && !name.contains("STICK") ? null : ProfessionType.WOODWORKING;
                        } else {
                           return ProfessionType.TAILORING;
                        }
                     } else {
                        return ProfessionType.ARMOURING;
                     }
                  } else {
                     return ProfessionType.WEAPONSMITHING;
                  }
               } else {
                  return ProfessionType.COOKING;
               }
            } else {
               return ProfessionType.ALCHEMY;
            }
         } else {
            return ProfessionType.JEWELING;
         }
      } else {
         return ProfessionType.SCRIBING;
      }
   }
}
