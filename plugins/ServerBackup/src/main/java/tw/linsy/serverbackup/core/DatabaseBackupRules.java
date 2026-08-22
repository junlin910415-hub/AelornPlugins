package tw.linsy.serverbackup.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class DatabaseBackupRules {
   private final boolean enabled;
   private final PathRules pathRules;
   private final Set<String> extensions;
   private final Set<String> fileNames;

   DatabaseBackupRules(boolean var1, List<String> var2, List<String> var3, List<String> var4) {
      this.enabled = var1;
      this.pathRules = new PathRules(lowercaseList(var2));
      this.extensions = normalizeSet(var3);
      this.fileNames = normalizeSet(var4);
   }

   boolean isCritical(Path var1) {
      if (this.enabled && var1 != null && var1.getNameCount() != 0) {
         String var2 = PathRules.normalize(var1).toLowerCase(Locale.ROOT);
         if (this.pathRules.matches(var2)) {
            return true;
         } else {
            String var3 = var1.getFileName().toString().toLowerCase(Locale.ROOT);
            if (this.fileNames.contains(var3)) {
               return true;
            } else {
               for(String var5 : this.extensions) {
                  if (var2.endsWith("." + var5)) {
                     return isLikelyDataPath(var2);
                  }
               }

               return false;
            }
         }
      } else {
         return false;
      }
   }

   private static boolean isLikelyDataPath(String var0) {
      return var0.startsWith("plugins/") || var0.contains("/database") || var0.contains("/databases") || var0.contains("/storage/") || var0.contains("/data/") || var0.contains("/cache/") || var0.contains("/userdata/") || var0.contains("/playerdata/") || var0.contains("/players/") || var0.contains("/profiles/") || var0.contains("/leveldb/");
   }

   private static Set<String> normalizeSet(List<String> var0) {
      return (var0 == null ? List.<String>of() : var0).stream().filter((var0x) -> var0x != null && !var0x.isBlank()).map((var0x) -> var0x.startsWith(".") ? var0x.substring(1) : var0x).map((var0x) -> var0x.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
   }

   private static List<String> lowercaseList(List<String> var0) {
      return (var0 == null ? List.<String>of() : var0).stream().filter((var0x) -> var0x != null && !var0x.isBlank()).map((var0x) -> var0x.toLowerCase(Locale.ROOT)).toList();
   }
}
