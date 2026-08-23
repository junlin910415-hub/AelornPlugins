package tw.linsy.aelorn.mmoitems;

import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;

record BrowserCategory(String id, String display, Material icon, String description, Set<String> types, Set<String> keywords) {
   boolean matches(String var1) {
      String var2 = var1 == null ? "" : var1.toUpperCase(Locale.ROOT);
      if (this.types.contains(var2)) {
         return true;
      } else {
         for(String var5 : this.keywords) {
            if (var2.contains(var5)) {
               return true;
            }
         }

         return false;
      }
   }
}
