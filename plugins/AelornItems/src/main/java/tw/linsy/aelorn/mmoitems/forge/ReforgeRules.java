package tw.linsy.aelorn.mmoitems.forge;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

public record ReforgeRules(boolean keepTier, boolean keepQuality, boolean keepAffixes, boolean keepGems, boolean keepRune, boolean allowFlags) {
   public static final ReforgeRules LEGACY_DEFAULT = new ReforgeRules(false, false, false, true, true, true);

   public static ReforgeRules from(ConfigurationSection var0) {
      return var0 == null ? LEGACY_DEFAULT : new ReforgeRules(var0.getBoolean("keep-tier", false), var0.getBoolean("keep-quality", false), var0.getBoolean("keep-affixes", false), var0.getBoolean("keep-gems", true), var0.getBoolean("keep-rune", true), var0.getBoolean("allow-flags", true));
   }

   public ReforgeRules withFlags(String[] var1) {
      if (this.allowFlags && var1 != null) {
         boolean var2 = this.keepTier;
         boolean var3 = this.keepQuality;
         boolean var4 = this.keepAffixes;
         boolean var5 = this.keepGems;
         boolean var6 = this.keepRune;

         for(String var10 : var1) {
            if (var10 != null && var10.startsWith("--")) {
               String var11 = var10.substring(2).toLowerCase(Locale.ROOT).replace('_', '-');
               boolean var12 = var11.startsWith("no-");
               if (var12) {
                  var11 = var11.substring(3);
               }

               boolean var13 = !var12;
               switch (var11) {
                  case "keep-tier":
                     var2 = var13;
                     break;
                  case "keep-quality":
                     var3 = var13;
                     break;
                  case "keep-affixes":
                     var4 = var13;
                     break;
                  case "keep-gems":
                     var5 = var13;
                     break;
                  case "keep-rune":
                     var6 = var13;
               }
            }
         }

         return new ReforgeRules(var2, var3, var4, var5, var6, this.allowFlags);
      } else {
         return this;
      }
   }

   public boolean keepsAnything() {
      return this.keepTier || this.keepQuality || this.keepAffixes;
   }

   public String describe() {
      StringBuilder var1 = new StringBuilder();
      StringBuilder var2 = new StringBuilder();
      append(var1, var2, this.keepTier, "階級");
      append(var1, var2, this.keepQuality, "品質");
      append(var1, var2, this.keepAffixes, "詞綴");
      append(var1, var2, this.keepGems, "寶石");
      append(var1, var2, this.keepRune, "符文");
      StringBuilder var3 = new StringBuilder("&a重鑄完成");
      if (!var2.isEmpty()) {
         var3.append(" &7｜ &f重擲：&e").append(var2);
      }

      if (!var1.isEmpty()) {
         var3.append(" &7｜ &f保留：&b").append(var1);
      }

      var3.append(" &7｜ &f升級等級：&b已保留");
      return var3.toString();
   }

   private static void append(StringBuilder var0, StringBuilder var1, boolean var2, String var3) {
      StringBuilder var4 = var2 ? var0 : var1;
      if (!var4.isEmpty()) {
         var4.append("、");
      }

      var4.append(var3);
   }
}
