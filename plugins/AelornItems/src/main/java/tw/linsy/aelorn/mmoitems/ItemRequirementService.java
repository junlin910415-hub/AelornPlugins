package tw.linsy.aelorn.mmoitems;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tw.linsy.aelorn.mythiccore.api.MythicCoreApi;
import tw.linsy.aelorn.mythiccore.api.PlayerClassState;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;

final class ItemRequirementService {
   private static final List<String> PRIMARY_SKILLS = List.of("STRENGTH", "DEXTERITY", "INTELLIGENCE", "DEFENCE", "AGILITY", "WISDOM", "VITALITY", "RESILIENCE");
   private static final Map<String, String> SKILL_NAMES = Map.of("STRENGTH", "力量", "DEXTERITY", "靈巧", "INTELLIGENCE", "智力", "DEFENCE", "護甲", "AGILITY", "敏捷", "WISDOM", "靈性", "VITALITY", "體魄", "RESILIENCE", "韌性");
   private final MythicCoreApi core;

   ItemRequirementService(MythicCoreApi var1) {
      this.core = var1;
   }

   Result check(Player var1, ItemStack var2) {
      if (var2 != null && !var2.getType().isAir() && !this.core.readItemType(var2).isBlank()) {
         if (var1.hasPermission("mmoitems.bypass.requirements")) {
            return ItemRequirementService.Result.allowed();
         } else {
            PlayerClassState var3 = this.core.playerClassState(var1.getUniqueId());
            int var4 = var3 == null ? Math.max(1, var1.getLevel()) : var3.level();
            int var5 = parsePositiveInt(this.core.readItemTag(var2, "required_level"));
            if (var5 > var4 && !var1.hasPermission("mmoitems.bypass.level")) {
               return ItemRequirementService.Result.denied("戰鬥等級不足，需要 " + var5 + " 級");
            } else {
               String var6 = this.core.readItemTag(var2, "required_class");
               if (!var6.isBlank() && !var1.hasPermission("mmoitems.bypass.class")) {
                  String var7 = var3 == null ? "" : normalize(var3.classId());
                  Stream var10000 = splitRequirements(var6).stream().map(ItemRequirementService::normalize);
                  Objects.requireNonNull(var7);
                  boolean var8 = var10000.anyMatch(var7::equals);
                  if (!var8) {
                     return ItemRequirementService.Result.denied(var7.isBlank() ? "角色職業尚未載入" : "職業不符，需要 " + var6.replace('|', '/'));
                  }
               }

               if (!var1.hasPermission("mmoitems.bypass.skills")) {
                  Map var12 = normalizedSkills(var3);

                  for(String var9 : PRIMARY_SKILLS) {
                     int var11 = parsePositiveInt(this.core.readItemTag(var2, "required_" + var9.toLowerCase(Locale.ROOT)));
                     if (var11 > (Integer)var12.getOrDefault(var9, 0)) {
                        String var14 = (String)SKILL_NAMES.getOrDefault(var9, var9);
                        return ItemRequirementService.Result.denied(var14 + "不足，需要 " + var11 + " 點");
                     }
                  }
               }

               return ItemRequirementService.Result.allowed();
            }
         }
      } else {
         return ItemRequirementService.Result.allowed();
      }
   }

   private static Map<String, Integer> normalizedSkills(PlayerClassState var0) {
      if (var0 != null && var0.primarySkills() != null) {
         LinkedHashMap var1 = new LinkedHashMap();
         var0.primarySkills().forEach((var1x, var2) -> {
            String var3 = normalize(var1x);
            if (var3.equals("DEFENSE")) {
               var3 = "DEFENCE";
            }

            var1.put(var3, Math.max(0, var2 == null ? 0 : var2));
         });
         return var1;
      } else {
         return Map.of();
      }
   }

   private static List<String> splitRequirements(String var0) {
      return List.of(var0.split("[,;/|]+"));
   }

   private static int parsePositiveInt(String var0) {
      if (var0 != null && !var0.isBlank()) {
         try {
            return Math.max(0, (int)Math.ceil(Double.parseDouble(var0.trim())));
         } catch (NumberFormatException var2) {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private static String normalize(String var0) {
      return StatSnapshot.normalize(var0);
   }

   static record Result(boolean usable, String message) {
      static Result allowed() {
         return new Result(true, "");
      }

      static Result denied(String var0) {
         return new Result(false, var0);
      }
   }
}
