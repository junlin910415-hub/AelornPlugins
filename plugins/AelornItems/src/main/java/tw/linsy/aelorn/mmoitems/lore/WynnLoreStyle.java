package tw.linsy.aelorn.mmoitems.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WynnLoreStyle {
   private static final int VALUE_COLUMN = 26;
   private static final String GRAY = "&7";
   private static final String DARK = "&8";
   private static final String WHITE = "&f";
   private static final String GREEN = "&a";
   private static final String RED = "&c";

   private WynnLoreStyle() {
   }

   public static String badge(String var0, String var1, String var2) {
      return var0 + var1.toUpperCase(Locale.ROOT) + "&8 " + var0 + var2.toUpperCase(Locale.ROOT);
   }

   public static String primaryStat(String var0, double var1) {
      String var3 = var1 >= (double)0.0F ? "+" : "";
      return "&f" + var3 + trim(var1) + "&7 " + var0;
   }

   public static String divider() {
      return "&8◇" + "─".repeat(14) + "◇";
   }

   public static String requirement(Requirement var0) {
      String var1;
      String var2;
      if (var0.met() == null) {
         var1 = "&8◆ ";
         var2 = "&7";
      } else if (var0.met()) {
         var1 = "&a✔ ";
         var2 = "&7";
      } else {
         var1 = "&c✖ ";
         var2 = "&c";
      }

      String var3 = var1 + var2 + var0.label();
      return pad(var3, "&f" + var0.value());
   }

   public static String categoryHeader(String var0, String var1, String var2) {
      String var3 = var1 != null && !var1.isBlank() ? var1 + " " : "";
      return var0 + var3 + var2;
   }

   public static String identification(Identification var0) {
      boolean var1 = var0.value() >= (double)0.0F;
      String var2 = (var1 ? "&a+" : "&c") + trim(var0.value()) + var0.suffix();
      return pad(" &7" + var0.label(), var2);
   }

   public static List<String> sealedBlock(int var0, int var1, String var2) {
      ArrayList var3 = new ArrayList(5);
      var3.add("&f" + var0 + "&8-&f" + var1 + "&7 等級區間");
      var3.add(divider());
      var3.add("&7這件物品的力量已被封印，");
      var3.add("&7需要 &f" + var2 + "&7 才能解放其潛能。");
      return var3;
   }

   private static String pad(String var0, String var1) {
      int var2 = visibleWidth(var0);
      int var3 = Math.max(1, 26 - var2 - visibleWidth(var1));
      return var0 + " ".repeat(var3) + var1;
   }

   static int visibleWidth(String var0) {
      int var1 = 0;

      for(int var2 = 0; var2 < var0.length(); ++var2) {
         char var3 = var0.charAt(var2);
         if (var3 == '&' && var2 + 1 < var0.length() && isColorCode(var0.charAt(var2 + 1))) {
            ++var2;
         } else {
            var1 += isWide(var3) ? 2 : 1;
         }
      }

      return var1;
   }

   private static boolean isColorCode(char var0) {
      return var0 >= '0' && var0 <= '9' || var0 >= 'a' && var0 <= 'f' || var0 >= 'A' && var0 <= 'F' || "klmnorKLMNOR".indexOf(var0) >= 0;
   }

   private static boolean isWide(char var0) {
      return var0 >= 4352 && var0 <= 4447 || var0 >= 11904 && var0 <= '꓏' || var0 >= '가' && var0 <= '힣' || var0 >= '豈' && var0 <= '﫿' || var0 >= '︰' && var0 <= '﹯' || var0 >= '＀' && var0 <= '｠' || var0 >= '￠' && var0 <= '￦';
   }

   private static String trim(double var0) {
      return Math.abs(var0 - (double)Math.round(var0)) < 1.0E-4 ? String.format(Locale.ROOT, "%,d", Math.round(var0)) : String.format(Locale.ROOT, "%,.1f", var0);
   }

   public static record Requirement(String label, String value, Boolean met) {
      public static Requirement of(String var0, String var1) {
         return new Requirement(var0, var1, (Boolean)null);
      }

      public static Requirement checked(String var0, String var1, boolean var2) {
         return new Requirement(var0, var1, var2);
      }
   }

   public static record Identification(String label, double value, String suffix) {
      public Identification(String label, double value, String suffix) {
         suffix = suffix == null ? "" : suffix;
         this.label = label;
         this.value = value;
         this.suffix = suffix;
      }
   }
}
