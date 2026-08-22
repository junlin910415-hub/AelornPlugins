package com.xuzhihuanjing.rpgcore.hud;

import java.util.Locale;

public final class HudNumberFormat {
   private HudNumberFormat() {
   }

   public static String compact(int value) {
      return compact((long)value);
   }

   public static String compact(long value) {
      long absolute = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
      if (absolute < 1000L) {
         return Long.toString(value);
      } else if (absolute < 1000000L) {
         return scaled(value, 1000L, "K");
      } else {
         return absolute < 1000000000L ? scaled(value, 1000000L, "M") : scaled(value, 1000000000L, "B");
      }
   }

   private static String scaled(long value, long unit, String suffix) {
      double scaled = (double)value / (double)unit;
      String pattern = Math.abs(scaled) < (double)10.0F ? "%.1f%s" : "%.0f%s";
      String rendered = String.format(Locale.ROOT, pattern, scaled, suffix);
      return rendered.replace(".0" + suffix, suffix);
   }
}
