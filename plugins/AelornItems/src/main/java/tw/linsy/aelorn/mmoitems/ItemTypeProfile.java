package tw.linsy.aelorn.mmoitems;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

record ItemTypeProfile(String id, String displayName, Material icon, boolean hidden, String tooltip, String category, double browserIndex, Set<String> damageTypes) {
   static ItemTypeProfile from(String var0, ConfigurationSection var1) {
      String var2 = var0.toUpperCase(Locale.ROOT);
      LinkedHashSet var3 = new LinkedHashSet();

      for(String var5 : var1.getStringList("attack-damage-types")) {
         if (!var5.isBlank()) {
            var3.add(var5.trim().toUpperCase(Locale.ROOT));
         }
      }

      return new ItemTypeProfile(var2, var1.getString("name", "&f" + var2), parseMaterial(var1.getString("display", "CHEST")), var1.getBoolean("hide-in-game", false), var1.getString("tooltip", ""), var1.getString("browser-category", var1.getString("category", "")).toUpperCase(Locale.ROOT), var1.getDouble("browser-display-idx", var1.getDouble("browser-index", (double)0.0F)), Set.copyOf(var3));
   }

   static ItemTypeProfile fallback(String var0, Material var1) {
      String var2 = var0 != null && !var0.isBlank() ? var0.toUpperCase(Locale.ROOT) : "UNKNOWN";
      return new ItemTypeProfile(var2, "&f" + var2, var1 == null ? Material.CHEST : var1, false, "", "", (double)0.0F, Set.of());
   }

   boolean hasTooltip() {
      return this.tooltip != null && !this.tooltip.isBlank();
   }

   private static Material parseMaterial(String var0) {
      if (var0 != null && !var0.isBlank()) {
         String var1 = var0.split(":", 2)[0].trim().toUpperCase(Locale.ROOT);
         Material var2 = Material.matchMaterial(var1);
         return var2 != null && var2 != Material.AIR ? var2 : Material.CHEST;
      } else {
         return Material.CHEST;
      }
   }
}
