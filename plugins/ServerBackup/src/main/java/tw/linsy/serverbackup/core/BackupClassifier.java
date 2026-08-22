package tw.linsy.serverbackup.core;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class BackupClassifier {
   private final BackupSettings settings;
   private final Map<Path, Boolean> worldPathCache = new ConcurrentHashMap();

   BackupClassifier(BackupSettings var1) {
      this.settings = var1;
   }

   CategorizedPath categorize(Path var1) {
      Path var2 = this.settings.serverRoot().relativize(var1.toAbsolutePath().normalize());
      BackupCategory var3 = this.categoryFor(var2);
      Path var4 = this.targetRelativePath(var3, var2);
      return new CategorizedPath(var3, var4);
   }

   private BackupCategory categoryFor(Path var1) {
      if (var1.getNameCount() == 0) {
         return BackupCategory.SERVER_FILES;
      } else {
         String var2 = name(var1, 0);
         if ("plugins".equals(var2)) {
            return BackupCategory.PLUGINS;
         } else if ("logs".equals(var2)) {
            return BackupCategory.LOGS;
         } else if ("crash-reports".equals(var2)) {
            return BackupCategory.LOGS;
         } else if (!"config".equals(var2) && !"configs".equals(var2)) {
            if ("libraries".equals(var2)) {
               return BackupCategory.LIBRARIES;
            } else if ("versions".equals(var2)) {
               return BackupCategory.VERSIONS;
            } else if ("cache".equals(var2)) {
               return BackupCategory.CACHE;
            } else if (this.isInsideWorld(var1)) {
               return BackupCategory.WORLDS;
            } else {
               return isConfigFile(var1) ? BackupCategory.CONFIGS : BackupCategory.SERVER_FILES;
            }
         } else {
            return BackupCategory.CONFIGS;
         }
      }
   }

   private Path targetRelativePath(BackupCategory var1, Path var2) {
      if (var2.getNameCount() == 0) {
         return Path.of(".");
      } else if (var1 == BackupCategory.PLUGINS && "plugins".equals(name(var2, 0))) {
         return stripFirstName(var2);
      } else if (var1 == BackupCategory.LOGS && "logs".equals(name(var2, 0))) {
         return stripFirstName(var2);
      } else if (var1 == BackupCategory.LOGS && "crash-reports".equals(name(var2, 0))) {
         return var2;
      } else if (var1 != BackupCategory.CONFIGS || !"config".equals(name(var2, 0)) && !"configs".equals(name(var2, 0))) {
         if (var1 == BackupCategory.LIBRARIES && "libraries".equals(name(var2, 0))) {
            return stripFirstName(var2);
         } else if (var1 == BackupCategory.VERSIONS && "versions".equals(name(var2, 0))) {
            return stripFirstName(var2);
         } else {
            return var1 == BackupCategory.CACHE && "cache".equals(name(var2, 0)) ? stripFirstName(var2) : var2;
         }
      } else {
         return stripFirstName(var2);
      }
   }

   private boolean isInsideWorld(Path var1) {
      for(int var2 = var1.getNameCount(); var2 > 0; --var2) {
         Path var3 = var1.subpath(0, var2);
         Boolean var4 = (Boolean)this.worldPathCache.get(var3);
         if (var4 == null) {
            var4 = Files.exists(this.settings.serverRoot().resolve(var3).resolve(this.settings.worldMarkerFile()), new LinkOption[0]);
            this.worldPathCache.put(var3, var4);
         }

         if (var4) {
            return true;
         }
      }

      return false;
   }

   private static boolean isConfigFile(Path var0) {
      String var1 = var0.getFileName().toString().toLowerCase(Locale.ROOT);
      if (var0.getNameCount() != 1 || !var1.equals("server.properties") && !var1.equals("bukkit.yml") && !var1.equals("spigot.yml") && !var1.equals("paper.yml") && !var1.equals("permissions.yml") && !var1.equals("commands.yml") && !var1.equals("eula.txt")) {
         return var1.endsWith(".yml") || var1.endsWith(".yaml") || var1.endsWith(".json") || var1.endsWith(".toml") || var1.endsWith(".properties") || var1.endsWith(".conf") || var1.endsWith(".cfg");
      } else {
         return true;
      }
   }

   private static String name(Path var0, int var1) {
      return var0.getName(var1).toString().toLowerCase(Locale.ROOT);
   }

   private static Path stripFirstName(Path var0) {
      return var0.getNameCount() <= 1 ? Path.of(".") : var0.subpath(1, var0.getNameCount());
   }

   static record CategorizedPath(BackupCategory category, Path targetRelative) {
   }
}
