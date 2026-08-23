package tw.linsy.aelorn.mythiccore.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public interface MythicCoreApi {
   void registerKnownStats(Iterable<String> var1);

   void registerSetBonuses(Map<String, Map<Integer, Map<String, Double>>> var1);

   void writeItemData(ItemMeta var1, String var2, String var3, String var4, int var5, Map<String, Double> var6);

   void writeItemTags(ItemMeta var1, Map<String, String> var2);

   Map<String, Double> readItemStats(ItemStack var1);

   String readItemTag(ItemStack var1, String var2);

   String readItemId(ItemStack var1);

   String readItemType(ItemStack var1);

   int readItemLevel(ItemStack var1);

   StatSnapshot snapshot(LivingEntity var1);

   double calculateAttackDamage(LivingEntity var1, LivingEntity var2, double var3);

   Map<String, ElementProfile> elementProfiles();

   Map<String, ClassProfile> classProfiles();

   ClassProfile classProfile(String var1);

   Map<String, List<ClassStageProfile>> classStages();

   List<ClassStageProfile> classStages(String var1);

   ClassStageProfile classStageAtLevel(String var1, int var2);

   PlayerClassState playerClassState(UUID var1);

   List<SkillProfile> skillProfiles();

   SkillProfile skillProfile(String var1);

   List<SkillProfile> skillsForClass(String var1);

   SkillProfile matchSkillCombo(String var1, String var2, int var3, List<String> var4);

   SkillCastResult calculateSkill(String var1, int var2, int var3, StatSnapshot var4, StatSnapshot var5);

   double balancedElementDamage(StatSnapshot var1, String var2);

   default Map<String, Double> normalizeItemStats(Map<String, Double> var1, String var2, int var3) {
      return this.normalizeItemStats(var1, var2, "", var3);
   }

   Map<String, Double> normalizeItemStats(Map<String, Double> var1, String var2, String var3, int var4);

   TradeQuote tradeQuote(String var1, int var2, int var3, double var4);

   double itemValue(String var1, int var2, int var3, double var4, boolean var6);
}
