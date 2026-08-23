package tw.linsy.aelorn.mmoitems.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class TextBundle {
   private final Map<String, String> values = new LinkedHashMap();
   private final Map<String, List<String>> lists = new LinkedHashMap();

   public TextBundle() {
   }

   public int load(ConfigurationSection var1) {
      this.values.clear();
      this.lists.clear();
      this.absorb(var1, "");
      return this.values.size() + this.lists.size();
   }

   private void absorb(ConfigurationSection var1, String var2) {
      if (var1 != null) {
         for(String var4 : var1.getKeys(false)) {
            ConfigurationSection var5 = var1.getConfigurationSection(var4);
            if (var5 != null) {
               this.absorb(var5, var2 + var4 + ".");
            } else if (var1.isList(var4)) {
               List var6 = var1.getStringList(var4);
               if (!var6.isEmpty()) {
                  this.lists.put(normalize(var2 + var4), List.copyOf(var6));
               }
            } else {
               String var7 = var1.getString(var4);
               if (var7 != null && !var7.isBlank()) {
                  this.values.put(normalize(var2 + var4), var7);
               }
            }
         }

      }
   }

   public String get(String var1, String var2) {
      String var3 = (String)this.values.get(normalize(var1));
      return var3 == null ? var2 : var3;
   }

   public String format(String var1, String var2, Object... var3) {
      return substitute(this.get(var1, var2), var3);
   }

   public List<String> getList(String var1, List<String> var2) {
      List<String> var3 = this.lists.get(normalize(var1));
      return var3 == null ? var2 : var3;
   }

   public List<String> formatList(String var1, List<String> var2, Object... var3) {
      List<String> var4 = this.getList(var1, var2);
      ArrayList<String> var5 = new ArrayList<>(var4.size());

      for(String var7 : var4) {
         var5.add(substitute(var7, var3));
      }

      return var5;
   }

   public static String substitute(String var0, Object... var1) {
      if (var0 == null) {
         return "";
      } else if (!var0.isEmpty() && var1 != null) {
         String var2 = var0;

         for(int var3 = 0; var3 + 1 < var1.length; var3 += 2) {
            Object var4 = var1[var3];
            if (var4 != null) {
               Object var5 = var1[var3 + 1];
               var2 = var2.replace("{" + String.valueOf(var4) + "}", var5 == null ? "" : var5.toString());
            }
         }

         return var2;
      } else {
         return var0;
      }
   }

   private static String normalize(String var0) {
      return var0 == null ? "" : var0.trim().toLowerCase(Locale.ROOT).replace('_', '-');
   }
}
