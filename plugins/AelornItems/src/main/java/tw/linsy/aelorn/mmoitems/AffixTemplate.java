package tw.linsy.aelorn.mmoitems;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

record AffixTemplate(String id, String prefix, String suffix, double chance, int minLevel, int maxLevel, Set<String> itemTypes, Set<String> excludedItemTypes, Set<String> tiers, String group, boolean major, Map<String, ScaledValue> stats, ItemTemplate.AbilityData ability, double cost) {
   AffixTemplate(String id, String prefix, String suffix, double chance, int minLevel, int maxLevel, Set<String> itemTypes, Set<String> excludedItemTypes, Set<String> tiers, String group, boolean major, Map<String, ScaledValue> stats, ItemTemplate.AbilityData ability, double cost) {
      itemTypes = Set.copyOf(itemTypes);
      excludedItemTypes = Set.copyOf(excludedItemTypes);
      tiers = Set.copyOf(tiers);
      stats = Map.copyOf(stats);
      cost = Double.isFinite(cost) && cost > (double)0.0F ? Math.min((double)1000.0F, cost) : (double)1.0F;
      this.id = id;
      this.prefix = prefix;
      this.suffix = suffix;
      this.chance = chance;
      this.minLevel = minLevel;
      this.maxLevel = maxLevel;
      this.itemTypes = itemTypes;
      this.excludedItemTypes = excludedItemTypes;
      this.tiers = tiers;
      this.group = group;
      this.major = major;
      this.stats = stats;
      this.ability = ability;
      this.cost = cost;
   }

   static AffixTemplate from(String var0, ConfigurationSection var1) {
      LinkedHashMap var3 = new LinkedHashMap();
      ConfigurationSection var4 = var1.getConfigurationSection("stats");
      if (var4 != null) {
         for(String var6 : var4.getKeys(false)) {
            String var8 = WeaponStatCatalog.normalize(var6);
            ScaledValue var7;
            if (!var8.isBlank() && (var7 = ScaledValue.from(var4, var6)) != null) {
               var3.put(var8, var7);
            }
         }
      }

      ItemTemplate.AbilityData var2;
      if (!(var2 = ItemTemplate.AbilityData.from(var1)).enabled() && var4 != null) {
         var2 = ItemTemplate.AbilityData.from(var4);
      }

      return new AffixTemplate(normalize(var0), readFormatted(var1, "prefix"), readFormatted(var1, "suffix"), Math.max((double)0.0F, var1.getDouble("weight", var1.getDouble("chance", (double)1.0F))), Math.max(1, var1.getInt("min-level", 1)), Math.max(1, var1.getInt("max-level", 999)), readIds(var1, "item-types"), readIds(var1, "excluded-item-types"), readIds(var1, "tiers"), normalize(var1.getString("group", var0)), var1.getBoolean("major", false), var3, var2, var1.getDouble("cost", (double)1.0F));
   }

   boolean matches(int var1, ItemTemplate var2, TierProfile var3) {
      if (var1 >= this.minLevel && var1 <= this.maxLevel) {
         String var4 = normalize(var2.type());
         if (!this.itemTypes.isEmpty() && !this.itemTypes.contains(var4)) {
            return false;
         } else if (this.excludedItemTypes.contains(var4)) {
            return false;
         } else {
            return this.tiers.isEmpty() || this.tiers.contains(normalize(var3.id()));
         }
      } else {
         return false;
      }
   }

   Map<String, Double> statsAtLevel(int var1, Random var2) {
      LinkedHashMap var3 = new LinkedHashMap();

      for(Map.Entry var5 : this.stats.entrySet()) {
         double var6 = ScaledValue.sanitize(((ScaledValue)var5.getValue()).at(var1, var2), WeaponStatCatalog.limit((String)var5.getKey()));
         if (Math.abs(var6) > 1.0E-6) {
            var3.put((String)var5.getKey(), var6);
         }
      }

      return var3;
   }

   List<String> statKeys() {
      return List.copyOf(this.stats.keySet());
   }

   private static Set<String> readIds(ConfigurationSection var0, String var1) {
      LinkedHashSet var2 = new LinkedHashSet();

      for(String var4 : var0.getStringList(var1)) {
         String var5 = normalize(var4);
         if (!var5.isBlank()) {
            var2.add(var5);
         }
      }

      return var2;
   }

   private static String readFormatted(ConfigurationSection var0, String var1) {
      ConfigurationSection var2 = var0.getConfigurationSection(var1);
      return var2 == null ? var0.getString(var1, "") : var2.getString("format", "");
   }

   private static String normalize(String var0) {
      return var0 == null ? "" : var0.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
   }
}
