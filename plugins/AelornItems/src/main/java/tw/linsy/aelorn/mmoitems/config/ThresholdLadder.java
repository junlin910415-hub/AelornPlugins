package tw.linsy.aelorn.mmoitems.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class ThresholdLadder {
   private final List<Tier> defaults;
   private List<Tier> tiers;

   public ThresholdLadder(List<Tier> var1) {
      this.defaults = sorted(var1);
      this.tiers = this.defaults;
   }

   public int load(List<?> var1) {
      ArrayList var2 = new ArrayList();
      if (var1 != null) {
         for(Object var4 : var1) {
            Tier var5 = parse(var4);
            if (var5 != null) {
               var2.add(var5);
            }
         }
      }

      this.tiers = var2.isEmpty() ? this.defaults : sorted(var2);
      return this.tiers.size();
   }

   private static Tier parse(Object var0) {
      String var1;
      double var2;
      if (var0 instanceof ConfigurationSection var5) {
         var1 = var5.getString("name", "");
         var2 = var5.getDouble("at-least", Double.NaN);
      } else {
         if (!(var0 instanceof Map)) {
            return null;
         }

         Map var4 = (Map)var0;
         Object var6 = var4.get("name");
         Object var7 = var4.get("at-least");
         var1 = var6 == null ? "" : String.valueOf(var6);
         double var10000;
         if (var7 instanceof Number var8) {
            var10000 = var8.doubleValue();
         } else {
            var10000 = parseDouble(var7);
         }

         var2 = var10000;
      }

      return !var1.isBlank() && Double.isFinite(var2) ? new Tier(var2, var1) : null;
   }

   private static double parseDouble(Object var0) {
      if (var0 == null) {
         return Double.NaN;
      } else {
         try {
            return Double.parseDouble(String.valueOf(var0));
         } catch (NumberFormatException var2) {
            return Double.NaN;
         }
      }
   }

   public String label(double var1) {
      for(Tier var4 : this.tiers) {
         if (var1 >= var4.atLeast()) {
            return var4.name();
         }
      }

      return this.tiers.isEmpty() ? "" : ((Tier)this.tiers.get(this.tiers.size() - 1)).name();
   }

   private static List<Tier> sorted(List<Tier> var0) {
      ArrayList var1 = var0 == null ? new ArrayList() : new ArrayList(var0);
      var1.sort(Comparator.comparingDouble(Tier::atLeast).reversed());
      return List.copyOf(var1);
   }

   public static record Tier(double atLeast, String name) {
   }
}
