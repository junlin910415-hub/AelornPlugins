package tw.linsy.aelorn.mmoitems.forge;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.configuration.ConfigurationSection;

public final class StatFormula {
   private static final double VALUE_LIMIT = (double)1000000.0F;
   private static final int LEVEL_LIMIT = 10000;
   private static final double EXTREME_SIGMA = (double)2.5F;
   private static final double DEFAULT_MAX_SPREAD = 0.3;
   public static final StatFormula ZERO;
   private final double base;
   private final double scale;
   private final double spread;
   private final double maxSpread;
   private final boolean uniform;
   private final boolean hasMin;
   private final double min;
   private final boolean hasMax;
   private final double max;
   private final Curve curve;
   private final Rounding rounding;
   private final double step;
   private final boolean relative;
   private final int levelOrigin;
   private final double curveFactor;

   private StatFormula(double var1, double var3, double var5, double var7, boolean var9, boolean var10, double var11, boolean var13, double var14, Curve var16, Rounding var17, double var18, boolean var20, int var21, double var22) {
      this.base = sanitize(var1);
      this.scale = sanitize(var3);
      this.spread = Math.max((double)0.0F, sanitize(var5));
      this.maxSpread = Math.max((double)0.0F, sanitize(var7));
      this.uniform = var9;
      this.hasMin = var10;
      this.min = sanitize(var11);
      this.hasMax = var13;
      this.max = sanitize(var14);
      this.curve = var16 == null ? StatFormula.Curve.LINEAR : var16;
      this.rounding = var17 == null ? StatFormula.Rounding.NONE : var17;
      this.step = var18 > (double)0.0F ? var18 : (double)1.0F;
      this.relative = var20;
      this.levelOrigin = var21 == 0 ? 0 : 1;
      this.curveFactor = Double.isFinite(var22) && var22 > (double)0.0F ? var22 : this.curve.defaultFactor();
   }

   public static StatFormula parse(Object var0) {
      if (var0 == null) {
         return ZERO;
      } else if (var0 instanceof ConfigurationSection) {
         ConfigurationSection var3 = (ConfigurationSection)var0;
         return fromSection(var3);
      } else if (var0 instanceof Number) {
         Number var2 = (Number)var0;
         return fixed(var2.doubleValue());
      } else if (var0 instanceof String) {
         String var1 = (String)var0;
         return fromString(var1);
      } else {
         throw new IllegalArgumentException("無法解析屬性公式：需要數字、字串或設定區段，實際收到 " + var0.getClass().getSimpleName());
      }
   }

   public static StatFormula parse(ConfigurationSection var0, String var1) {
      if (var0 != null && var1 != null && var0.contains(var1)) {
         if (var0.isConfigurationSection(var1)) {
            return fromSection(var0.getConfigurationSection(var1));
         } else {
            Object var2 = var0.get(var1);
            return var2 == null ? null : parse(var2);
         }
      } else {
         return null;
      }
   }

   public static StatFormula fixed(double var0) {
      return new StatFormula(var0, (double)0.0F, (double)0.0F, (double)0.0F, false, false, (double)0.0F, false, (double)0.0F, StatFormula.Curve.LINEAR, StatFormula.Rounding.NONE, (double)0.0F, false, 1, (double)0.0F);
   }

   public static StatFormula linear(double var0, double var2) {
      return new StatFormula(var0, var2, (double)0.0F, (double)0.0F, false, false, (double)0.0F, false, (double)0.0F, StatFormula.Curve.LINEAR, StatFormula.Rounding.NONE, (double)0.0F, false, 1, (double)0.0F);
   }

   public static StatFormula legacy(double var0, double var2, double var4, double var6) {
      double var8 = Math.min((double)1.0F, Math.max((double)0.0F, var6 > (double)0.0F ? var6 : var4));
      return var8 <= (double)0.0F ? new StatFormula(var0, var2, (double)0.0F, (double)0.0F, false, false, (double)0.0F, false, (double)0.0F, StatFormula.Curve.LINEAR, StatFormula.Rounding.NONE, (double)0.0F, true, 1, (double)0.0F) : new StatFormula(var0, var2, var8 / (double)2.5F, var8, false, false, (double)0.0F, false, (double)0.0F, StatFormula.Curve.LINEAR, StatFormula.Rounding.NONE, (double)0.0F, true, 1, (double)0.0F);
   }

   private static StatFormula fromString(String var0) {
      String var1 = var0.trim();
      if (var1.contains("->")) {
         String[] var17 = var1.split("->", 2);
         double var18 = parseNumber(var17[0].trim(), var1);
         double var19 = parseNumber(var17[1].trim(), var1);
         return new StatFormula((double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F, true, true, Math.min(var18, var19), true, Math.max(var18, var19), StatFormula.Curve.LINEAR, StatFormula.Rounding.NONE, (double)0.0F, false, 1, (double)0.0F);
      } else {
         String[] var2 = var1.split("\\s+");
         double var3 = parseNumber(var2[0], var1);
         double var5 = var2.length > 1 ? parseNumber(var2[1], var1) : (double)0.0F;
         double var7 = var2.length > 2 ? parseNumber(var2[2], var1) : (double)0.0F;
         double var9 = var2.length > 3 ? parseNumber(var2[3], var1) : (double)0.0F;
         boolean var11 = var2.length > 4;
         boolean var12 = var2.length > 5;
         double var13 = var11 ? parseNumber(var2[4], var1) : (double)0.0F;
         double var15 = var12 ? parseNumber(var2[5], var1) : (double)0.0F;
         return new StatFormula(var3, var5, var7, var9, false, var11, var13, var12, var15, StatFormula.Curve.LINEAR, StatFormula.Rounding.NONE, (double)0.0F, false, 1, (double)0.0F);
      }
   }

   private static StatFormula fromSection(ConfigurationSection var0) {
      if (var0 == null) {
         return ZERO;
      } else {
         double var1 = var0.getDouble("base", var0.getDouble("value", (double)0.0F));
         double var3 = var0.getDouble("scale", var0.getDouble("per-level", (double)0.0F));
         double var5 = var0.getDouble("spread", (double)0.0F);
         double var7 = var0.getDouble("max-spread", var0.getDouble("max_spread", 0.3));
         boolean var9 = var0.contains("min");
         boolean var10 = var0.contains("max");
         double var11 = var9 ? var0.getDouble("min") : (double)0.0F;
         double var13 = var10 ? var0.getDouble("max") : (double)0.0F;
         boolean var15 = var9 && var10 && !var0.contains("base") && !var0.contains("value") && !var0.contains("scale") && !var0.contains("per-level") && !var0.contains("spread");
         Curve var16 = StatFormula.Curve.of(var0.getString("curve"));
         Rounding var17 = StatFormula.Rounding.of(var0.getString("rounding", var0.getString("round")));
         double var18 = var0.getDouble("step", (double)1.0F);
         boolean var20 = var0.getBoolean("relative", false);
         int var21 = var0.getInt("level-origin", var0.getInt("level_origin", 1));
         double var22 = var0.getDouble("curve-factor", var0.getDouble("curve_factor", (double)0.0F));
         return new StatFormula(var1, var3, var5, var7, var15, var9, var11, var10, var13, var16, var17, var18, var20, var21, var22);
      }
   }

   public StatFormula withCurve(Curve var1) {
      return new StatFormula(this.base, this.scale, this.spread, this.maxSpread, this.uniform, this.hasMin, this.min, this.hasMax, this.max, var1, this.rounding, this.step, this.relative, this.levelOrigin, (double)0.0F);
   }

   public StatFormula withRounding(Rounding var1, double var2) {
      return new StatFormula(this.base, this.scale, this.spread, this.maxSpread, this.uniform, this.hasMin, this.min, this.hasMax, this.max, this.curve, var1, var2, this.relative, this.levelOrigin, this.curveFactor);
   }

   public StatFormula withBounds(Double var1, Double var2) {
      return new StatFormula(this.base, this.scale, this.spread, this.maxSpread, this.uniform, var1 != null, var1 == null ? (double)0.0F : var1, var2 != null, var2 == null ? (double)0.0F : var2, this.curve, this.rounding, this.step, this.relative, this.levelOrigin, this.curveFactor);
   }

   private static double parseNumber(String var0, String var1) {
      try {
         return Double.parseDouble(var0);
      } catch (NumberFormatException var3) {
         throw new IllegalArgumentException("屬性公式含有無法解析的數字「" + var0 + "」：" + var1, var3);
      }
   }

   public double roll(int var1) {
      return this.evaluate(var1, this.sample(ThreadLocalRandom.current()));
   }

   public double roll(int var1, Random var2) {
      return this.evaluate(var1, var2 == null ? (double)0.0F : this.sample(var2));
   }

   public double at(int var1, Bound var2) {
      double var10000;
      switch ((var2 == null ? StatFormula.Bound.AVERAGE : var2).ordinal()) {
         case 0 -> var10000 = this.evaluate(var1, this.uniform ? (double)0.0F : (double)-2.5F);
         case 1 -> var10000 = this.evaluate(var1, this.uniform ? (double)0.5F : (double)0.0F);
         case 2 -> var10000 = this.evaluate(var1, this.uniform ? (double)1.0F : (double)2.5F);
         case 3 -> var10000 = this.roll(var1);
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   public double lowerBound(int var1) {
      return this.at(var1, StatFormula.Bound.LOWER);
   }

   public double upperBound(int var1) {
      return this.at(var1, StatFormula.Bound.UPPER);
   }

   public double average(int var1) {
      return this.at(var1, StatFormula.Bound.AVERAGE);
   }

   public double base() {
      return this.base;
   }

   public double scale() {
      return this.scale;
   }

   public boolean isConstant() {
      return this.scale == (double)0.0F && this.spread == (double)0.0F && !this.uniform;
   }

   private double evaluate(int var1, double var2) {
      int var4 = Math.max(0, Math.min(10000, var1));
      double var5 = this.curve.apply(Math.max(0, var4 - this.levelOrigin), this.curveFactor);
      double var7;
      if (this.uniform) {
         double var9 = this.max - this.min;
         var7 = this.min + var9 * clamp01(var2) + this.scale * var5;
      } else {
         double var13 = this.base + this.scale * var5;
         double var11 = clamp(var2 * this.spread, -this.maxSpread, this.maxSpread);
         var7 = this.relative ? var13 * ((double)1.0F + var11) : var13 + var11;
         if (this.hasMin) {
            var7 = Math.max(this.min, var7);
         }

         if (this.hasMax) {
            var7 = Math.min(this.max, var7);
         }
      }

      return this.rounding.apply(sanitize(var7), this.step);
   }

   public double quality(int var1, double var2) {
      double var4 = this.lowerBound(var1);
      double var6 = this.upperBound(var1);
      double var8 = var6 - var4;
      return Math.abs(var8) < 1.0E-9 ? (double)1.0F : clamp01((var2 - var4) / var8);
   }

   public double reroll(double var1, int var3) {
      double var4 = this.lowerBound(var3);
      double var6 = this.upperBound(var3);
      return var1 >= var4 && var1 <= var6 ? var1 : this.roll(var3);
   }

   public void save(ConfigurationSection var1, String var2) {
      if (var1 != null && var2 != null) {
         if (this.isConstant() && !this.hasMin && !this.hasMax && this.rounding == StatFormula.Rounding.NONE) {
            var1.set(var2, this.base);
         } else {
            ConfigurationSection var3 = var1.createSection(var2);
            if (this.uniform) {
               var3.set("min", this.min);
               var3.set("max", this.max);
               if (this.scale != (double)0.0F) {
                  var3.set("scale", this.scale);
               }
            } else {
               var3.set("base", this.base);
               if (this.scale != (double)0.0F) {
                  var3.set("scale", this.scale);
               }

               if (this.spread != (double)0.0F) {
                  var3.set("spread", this.spread);
                  var3.set("max-spread", this.maxSpread);
               }

               if (this.hasMin) {
                  var3.set("min", this.min);
               }

               if (this.hasMax) {
                  var3.set("max", this.max);
               }
            }

            if (this.curve != StatFormula.Curve.LINEAR) {
               var3.set("curve", this.curve.name());
            }

            if (this.rounding != StatFormula.Rounding.NONE) {
               var3.set("rounding", this.rounding.name());
               if (this.rounding == StatFormula.Rounding.STEP) {
                  var3.set("step", this.step);
               }
            }

            if (this.relative) {
               var3.set("relative", true);
            }

            if (this.levelOrigin != 1) {
               var3.set("level-origin", this.levelOrigin);
            }

         }
      }
   }

   public String toString() {
      if (this.uniform) {
         String var10000 = trim(this.min);
         return "[" + var10000 + " → " + trim(this.max) + "]";
      } else if (this.isConstant()) {
         return trim(this.base);
      } else {
         StringBuilder var1 = (new StringBuilder("基礎 ")).append(trim(this.base));
         if (this.scale != (double)0.0F) {
            var1.append("，每級 +").append(trim(this.scale));
         }

         if (this.spread != (double)0.0F) {
            var1.append("，浮動 ±").append(trim(this.maxSpread * (double)(this.relative ? 100 : 1))).append(this.relative ? "%" : "");
         }

         return var1.toString();
      }
   }

   private static String trim(double var0) {
      return var0 == Math.rint(var0) ? String.valueOf((long)var0) : String.format(Locale.ROOT, "%.2f", var0);
   }

   private double sample(Random var1) {
      return this.uniform ? var1.nextDouble() : var1.nextGaussian();
   }

   private static double clamp(double var0, double var2, double var4) {
      return Math.max(var2, Math.min(var4, var0));
   }

   private static double clamp01(double var0) {
      return clamp(var0, (double)0.0F, (double)1.0F);
   }

   private static double sanitize(double var0) {
      return !Double.isFinite(var0) ? (double)0.0F : clamp(var0, (double)-1000000.0F, (double)1000000.0F);
   }

   static {
      ZERO = new StatFormula((double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F, false, false, (double)0.0F, false, (double)0.0F, StatFormula.Curve.LINEAR, StatFormula.Rounding.NONE, (double)0.0F, false, 1, (double)0.0F);
   }

   public static enum Bound {
      LOWER,
      AVERAGE,
      UPPER,
      RANDOM;

      private Bound() {
      }
   }

   public static enum Curve {
      LINEAR {
         double apply(int var1, double var2) {
            return (double)var1 * var2;
         }

         public double defaultFactor() {
            return (double)1.0F;
         }
      },
      QUADRATIC {
         double apply(int var1, double var2) {
            return (double)var1 * (double)var1 / var2;
         }

         public double defaultFactor() {
            return (double)100.0F;
         }
      },
      SQUARE_ROOT {
         double apply(int var1, double var2) {
            return Math.sqrt((double)var1) * var2;
         }

         public double defaultFactor() {
            return (double)10.0F;
         }
      },
      LOGARITHMIC {
         double apply(int var1, double var2) {
            return Math.log1p((double)var1) * var2;
         }

         public double defaultFactor() {
            return (double)20.0F;
         }
      },
      EXPONENTIAL {
         double apply(int var1, double var2) {
            return (Math.pow((double)2.0F, (double)var1 / var2) - (double)1.0F) * var2;
         }

         public double defaultFactor() {
            return (double)50.0F;
         }
      };

      private Curve() {
      }

      abstract double apply(int var1, double var2);

      public abstract double defaultFactor();

      public static Curve of(String var0) {
         if (var0 != null && !var0.isBlank()) {
            try {
               return valueOf(var0.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException var2) {
               return LINEAR;
            }
         } else {
            return LINEAR;
         }
      }
   }

   public static enum Rounding {
      NONE {
         double apply(double var1, double var3) {
            return var1;
         }
      },
      FLOOR {
         double apply(double var1, double var3) {
            return Math.floor(var1);
         }
      },
      CEIL {
         double apply(double var1, double var3) {
            return Math.ceil(var1);
         }
      },
      NEAREST {
         double apply(double var1, double var3) {
            return Math.rint(var1);
         }
      },
      STEP {
         double apply(double var1, double var3) {
            return Math.rint(var1 / var3) * var3;
         }
      };

      private Rounding() {
      }

      abstract double apply(double var1, double var3);

      public static Rounding of(String var0) {
         if (var0 != null && !var0.isBlank()) {
            try {
               return valueOf(var0.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException var2) {
               return NONE;
            }
         } else {
            return NONE;
         }
      }
   }
}
