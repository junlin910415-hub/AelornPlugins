package tw.linsy.serverbackup.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class PathRules {
   private final List<Pattern> excludePatterns = new ArrayList();

   PathRules(List<String> var1) {
      for(String var3 : var1) {
         if (var3 != null && !var3.isBlank()) {
            this.excludePatterns.add(Pattern.compile(globToRegex(normalize(var3.trim()))));
         }
      }

   }

   boolean isExcluded(Path var1) {
      return this.matches(normalize(var1));
   }

   boolean matches(String var1) {
      String var2 = normalize(var1);

      for(Pattern var4 : this.excludePatterns) {
         if (var4.matcher(var2).matches()) {
            return true;
         }
      }

      return false;
   }

   static String normalize(Path var0) {
      return normalize(var0.toString());
   }

   static String normalize(String var0) {
      return var0.replace('\\', '/');
   }

   private static String globToRegex(String var0) {
      StringBuilder var1 = new StringBuilder("^");

      for(int var2 = 0; var2 < var0.length(); ++var2) {
         char var3 = var0.charAt(var2);
         if (var3 == '*') {
            boolean var4 = var2 + 1 < var0.length() && var0.charAt(var2 + 1) == '*';
            if (var4) {
               var1.append(".*");
               ++var2;
            } else {
               var1.append("[^/]*");
            }
         } else if (var3 == '?') {
            var1.append("[^/]");
         } else if ("\\.[]{}()+-^$|".indexOf(var3) >= 0) {
            var1.append('\\').append(var3);
         } else {
            var1.append(var3);
         }
      }

      var1.append("$");
      return var1.toString();
   }
}
