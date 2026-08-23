package tw.linsy.aelorn.mmoitems;

import java.util.Random;
import org.bukkit.configuration.ConfigurationSection;
import tw.linsy.aelorn.mmoitems.forge.StatFormula;

final class ScaledValue {
   private static final double VALUE_LIMIT = (double)1000000.0F;
   private final StatFormula formula;
   private final double base;
   private final double scale;
   private final double spread;
   private final double maxSpread;

   private ScaledValue(StatFormula var1, double var2, double var4, double var6, double var8) {
      this.formula = var1;
      this.base = var2;
      this.scale = var4;
      this.spread = var6;
      this.maxSpread = var8;
   }

   double at(int var1, Random var2) {
      return this.formula.roll(var1, var2);
   }

   double lowerBound(int var1) {
      return this.formula.lowerBound(var1);
   }

   double upperBound(int var1) {
      return this.formula.upperBound(var1);
   }

   double quality(int var1, double var2) {
      return this.formula.quality(var1, var2);
   }

   StatFormula formula() {
      return this.formula;
   }

   double base() {
      return this.base;
   }

   double scale() {
      return this.scale;
   }

   double spread() {
      return this.spread;
   }

   double maxSpread() {
      return this.maxSpread;
   }

   public String toString() {
      return this.formula.toString();
   }

   static ScaledValue from(ConfigurationSection var0, String var1) {
      if (var0 == null) {
         return null;
      } else if (var0.isConfigurationSection(var1)) {
         return fromSection(var0.getConfigurationSection(var1));
      } else if (!var0.isDouble(var1) && !var0.isInt(var1) && !var0.isLong(var1)) {
         return null;
      } else {
         double var2 = sanitize(var0.getDouble(var1), (double)1000000.0F);
         return new ScaledValue(StatFormula.fixed(var2), var2, (double)0.0F, (double)0.0F, (double)0.0F);
      }
   }

   static ScaledValue fromSection(ConfigurationSection var0) {
      if (var0 == null) {
         return new ScaledValue(StatFormula.ZERO, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F);
      } else {
         double var1 = sanitize(var0.getDouble("base", var0.getDouble("value", (double)0.0F)), (double)1000000.0F);
         double var3 = sanitize(var0.getDouble("scale", var0.getDouble("per-level", (double)0.0F)), (double)1000000.0F);
         double var5 = sanitizeSpread(var0.getDouble("spread", (double)0.0F));
         double var7 = sanitizeSpread(var0.getDouble("max-spread", var0.getDouble("max_spread", (double)0.0F)));
         StatFormula var9 = StatFormula.legacy(var1, var3, var5, var7);
         if (var0.contains("curve")) {
            var9 = var9.withCurve(StatFormula.Curve.of(var0.getString("curve")));
         }

         if (var0.contains("rounding") || var0.contains("round")) {
            String var10 = var0.getString("rounding", var0.getString("round"));
            var9 = var9.withRounding(StatFormula.Rounding.of(var10), var0.getDouble("step", (double)1.0F));
         }

         if (var0.contains("min") || var0.contains("max")) {
            Double var12 = var0.contains("min") ? var0.getDouble("min") : null;
            Double var11 = var0.contains("max") ? var0.getDouble("max") : null;
            var9 = var9.withBounds(var12, var11);
         }

         return new ScaledValue(var9, var1, var3, var5, var7);
      }
   }

   static double sanitize(double var0, double var2) {
      if (!Double.isFinite(var0)) {
         return (double)0.0F;
      } else {
         double var4 = Math.max((double)1.0F, Math.abs(var2));
         return Math.max(-var4, Math.min(var4, var0));
      }
   }

   private static double sanitizeSpread(double var0) {
      return !Double.isFinite(var0) ? (double)0.0F : Math.max((double)0.0F, Math.min((double)1.0F, var0));
   }
}
