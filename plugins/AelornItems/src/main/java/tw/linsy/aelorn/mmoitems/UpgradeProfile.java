package tw.linsy.aelorn.mmoitems;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;

record UpgradeProfile(String id, Map<String, UpgradeRule> rules) {
   static UpgradeProfile from(String var0, ConfigurationSection var1) {
      LinkedHashMap var2 = new LinkedHashMap();

      for(String var4 : var1.getKeys(false)) {
         String var5 = WeaponStatCatalog.normalize(var4);
         UpgradeRule var6 = UpgradeProfile.UpgradeRule.from(var1, var4);
         if (!var5.isBlank() && var6 != null) {
            var2.put(var5, var6);
         }
      }

      return new UpgradeProfile(StatSnapshot.normalize(var0), Map.copyOf(var2));
   }

   void apply(Map<String, Double> var1, int var2) {
      if (var2 > 0 && !this.rules.isEmpty()) {
         for(Map.Entry var4 : this.rules.entrySet()) {
            String var5 = (String)var4.getKey();
            double var6 = (Double)var1.getOrDefault(var5, (double)0.0F);
            var1.put(var5, ((UpgradeRule)var4.getValue()).apply(var6, var2));
         }
      }

   }

   List<String> statKeys() {
      return List.copyOf(this.rules.keySet());
   }

   static record UpgradeRule(double amount, boolean percent, boolean compound) {
      double apply(double var1, int var3) {
         if (this.percent) {
            double var4 = this.compound ? Math.pow((double)1.0F + this.amount / (double)100.0F, (double)var3) : (double)1.0F + this.amount / (double)100.0F * (double)var3;
            return var1 * var4;
         } else {
            return var1 + this.amount * (double)var3;
         }
      }

      static UpgradeRule from(ConfigurationSection var0, String var1) {
         if (var0.isConfigurationSection(var1)) {
            ConfigurationSection var7 = var0.getConfigurationSection(var1);
            if (var7 == null) {
               return null;
            } else {
               boolean var8 = var7.getBoolean("percent", var7.getBoolean("relative", false));
               return new UpgradeRule(var7.getDouble("amount", var7.getDouble("value", (double)0.0F)), var8, var7.getBoolean("compound", true));
            }
         } else if (var0.isString(var1)) {
            String var2 = var0.getString(var1, "").trim();
            boolean var3 = var2.endsWith("%");
            String var4 = var3 ? var2.substring(0, var2.length() - 1).trim() : var2;

            try {
               return new UpgradeRule(Double.parseDouble(var4), var3, true);
            } catch (NumberFormatException var6) {
               return null;
            }
         } else {
            return !var0.isDouble(var1) && !var0.isInt(var1) && !var0.isLong(var1) ? null : new UpgradeRule(var0.getDouble(var1), false, true);
         }
      }
   }
}
