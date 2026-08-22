package tw.linsy.serverbackup.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class MaterialBackupRules {
   private final boolean enabled;
   private final PathRules pathRules;
   private final Set<String> extensions;

   MaterialBackupRules(boolean var1, List<String> var2, List<String> var3) {
      this.enabled = var1;
      this.pathRules = new PathRules(var2 == null ? List.<String>of() : var2);
      this.extensions = (var3 == null ? List.<String>of() : var3).stream().filter((var0) -> var0 != null && !var0.isBlank()).map((var0) -> var0.startsWith(".") ? var0.substring(1) : var0).map((var0) -> var0.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
   }

   boolean isCritical(Path var1) {
      if (this.enabled && var1 != null && var1.getNameCount() != 0) {
         String var2 = PathRules.normalize(var1).toLowerCase(Locale.ROOT);
         if (this.pathRules.isExcluded(var1)) {
            return true;
         } else {
            int var3 = var2.lastIndexOf(46);
            if (var3 >= 0 && var3 != var2.length() - 1) {
               String var4 = var2.substring(var3 + 1);
               return this.extensions.contains(var4) && (var2.contains("/assets/") || var2.startsWith("assets/") || var2.contains("resourcepack") || var2.contains("resource-pack") || var2.contains("/pack/") || var2.contains("/itemsadder/") || var2.contains("/nexo/") || var2.contains("/aeloriahud/") || var2.contains("/mythicmobs/") || var2.contains("/mythiccrucible/"));
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }
}
