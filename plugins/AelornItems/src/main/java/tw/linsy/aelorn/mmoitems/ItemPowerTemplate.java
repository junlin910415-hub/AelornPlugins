package tw.linsy.aelorn.mmoitems;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

record ItemPowerTemplate(String id, String display, String family, Material material, int minLevel, int maxLevel, Map<String, ScaledValue> stats, ItemTemplate.AbilityData ability) {
   static ItemPowerTemplate from(String var0, ConfigurationSection var1) {
      LinkedHashMap var2 = new LinkedHashMap();
      ConfigurationSection var3 = var1.getConfigurationSection("stats");
      if (var3 != null) {
         collectStats(var3, var2);
      }

      return new ItemPowerTemplate(var0.toUpperCase(Locale.ROOT), var1.getString("name", var1.getString("display", var0)), var1.getString("family", var1.getString("type", "GENERAL")).toUpperCase(Locale.ROOT), parseMaterial(var1.getString("material", var1.getString("icon", "AMETHYST_SHARD"))), var1.getInt("min-level", 1), var1.getInt("max-level", 999), Map.copyOf(var2), ItemTemplate.AbilityData.from(var1));
   }

   boolean matches(int var1) {
      return var1 >= this.minLevel && var1 <= this.maxLevel;
   }

   Map<String, Double> statsAtLevel(int var1, Random var2) {
      LinkedHashMap var3 = new LinkedHashMap();

      for(Map.Entry var5 : this.stats.entrySet()) {
         String var6 = (String)var5.getKey();
         double var7 = ScaledValue.sanitize(((ScaledValue)var5.getValue()).at(var1, var2), WeaponStatCatalog.limit(var6));
         if (Math.abs(var7) > 1.0E-6) {
            var3.put(var6, var7);
         }
      }

      return var3;
   }

   List<String> statKeys() {
      return List.copyOf(this.stats.keySet());
   }

   private static void collectStats(ConfigurationSection var0, Map<String, ScaledValue> var1) {
      for(String var3 : var0.getKeys(false)) {
         String var5 = normalizeStatKey(var3);
         ScaledValue var4;
         if (!var5.isBlank() && (var4 = ScaledValue.from(var0, var3)) != null) {
            var1.put(var5, var4);
         }
      }

   }

   private static String normalizeStatKey(String var0) {
      return WeaponStatCatalog.normalize(var0);
   }

   private static Material parseMaterial(String var0) {
      if (var0 != null && !var0.isBlank()) {
         String var1 = var0.split(":", 2)[0].trim().toUpperCase(Locale.ROOT);

         try {
            return Material.valueOf(var1);
         } catch (IllegalArgumentException var3) {
            return Material.AMETHYST_SHARD;
         }
      } else {
         return Material.AMETHYST_SHARD;
      }
   }
}
