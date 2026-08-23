package tw.linsy.aelorn.mmoitems;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

record SetBonusProfile(String id, String display, Map<Integer, Map<String, Double>> bonuses) {
   static SetBonusProfile from(String var0, ConfigurationSection var1) {
      LinkedHashMap var2 = new LinkedHashMap();
      ConfigurationSection var3 = var1.getConfigurationSection("bonuses");
      if (var3 != null) {
         for(String var5 : var3.getKeys(false)) {
            int var6 = parseInt(var5, 0);
            ConfigurationSection var7 = var3.getConfigurationSection(var5);
            if (var6 > 0 && var7 != null) {
               LinkedHashMap var8 = new LinkedHashMap();

               for(String var10 : var7.getKeys(false)) {
                  String var11;
                  if ((var7.isDouble(var10) || var7.isInt(var10) || var7.isLong(var10)) && !(var11 = WeaponStatCatalog.normalize(var10)).isBlank()) {
                     var8.put(var11, var7.getDouble(var10));
                  }
               }

               var2.put(var6, var8);
            }
         }
      }

      return new SetBonusProfile(var0.toUpperCase(Locale.ROOT), var1.getString("name", var0), var2);
   }

   private static int parseInt(String var0, int var1) {
      try {
         return Integer.parseInt(var0);
      } catch (NumberFormatException var3) {
         return var1;
      }
   }
}
