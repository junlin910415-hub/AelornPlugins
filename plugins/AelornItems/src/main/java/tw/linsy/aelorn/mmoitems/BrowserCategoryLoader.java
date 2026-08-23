package tw.linsy.aelorn.mmoitems;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

final class BrowserCategoryLoader {
   private static final Material FALLBACK_ICON;
   private final List<BrowserCategory> defaults;
   private final Logger logger;
   private List<BrowserCategory> categories;

   BrowserCategoryLoader(List<BrowserCategory> var1, Logger var2) {
      this.defaults = List.copyOf(var1);
      this.logger = var2;
      this.categories = this.defaults;
   }

   int load(List<?> var1) {
      ArrayList var2 = new ArrayList();
      if (var1 != null) {
         for(Object var4 : var1) {
            BrowserCategory var5 = this.parse(var4);
            if (var5 != null) {
               var2.add(var5);
            }
         }
      }

      this.categories = var2.isEmpty() ? this.defaults : List.copyOf(var2);
      return this.categories.size();
   }

   List<BrowserCategory> categories() {
      return this.categories;
   }

   private BrowserCategory parse(Object var1) {
      if (var1 instanceof Map var2) {
         String var3 = normalize(string(var2, "id"));
         String var4 = string(var2, "display");
         if (!var3.isBlank() && !var4.isBlank()) {
            return new BrowserCategory(var3, var4, this.icon(string(var2, "icon"), var3), string(var2, "description"), upperSet(var2.get("types")), upperSet(var2.get("keywords")));
         } else {
            this.warn("圖鑑分類缺少 id 或 display，已略過一筆。");
            return null;
         }
      } else {
         return null;
      }
   }

   private Material icon(String var1, String var2) {
      if (var1.isBlank()) {
         return FALLBACK_ICON;
      } else {
         Material var3 = Material.matchMaterial(var1.trim().toUpperCase(Locale.ROOT));
         if (var3 == null) {
            this.warn("圖鑑分類 " + var2 + " 的圖示材質無法辨識：" + var1 + "，改用預設圖示。");
            return FALLBACK_ICON;
         } else {
            return var3;
         }
      }
   }

   private static Set<String> upperSet(Object var0) {
      LinkedHashSet var1 = new LinkedHashSet();
      if (var0 instanceof Iterable) {
         for(Object var4 : (Iterable)var0) {
            if (var4 != null) {
               String var5 = normalize(String.valueOf(var4));
               if (!var5.isBlank()) {
                  var1.add(var5);
               }
            }
         }
      }

      return Set.copyOf(var1);
   }

   private static String string(Map<?, ?> var0, String var1) {
      Object var2 = var0.get(var1);
      return var2 == null ? "" : String.valueOf(var2);
   }

   private static String normalize(String var0) {
      return var0 == null ? "" : var0.trim().toUpperCase(Locale.ROOT);
   }

   private void warn(String var1) {
      if (this.logger != null) {
         this.logger.warning(var1);
      }

   }

   static List<?> listFrom(ConfigurationSection var0, String var1) {
      return var0 == null ? null : var0.getList(var1);
   }

   static {
      FALLBACK_ICON = Material.PAPER;
   }
}
