package tw.linsy.aelorn.mmoitems;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

final class AffixBudget {
   private static final double CAPACITY_LIMIT = (double)1000.0F;
   static final double MIN_WEIGHT = 0.01;
   private final double capacity;
   private double remaining;

   private AffixBudget(double var1) {
      this.capacity = var1;
      this.remaining = var1;
   }

   static AffixBudget of(double var0) {
      double var2 = Double.isFinite(var0) ? Math.max((double)0.0F, Math.min((double)1000.0F, var0)) : (double)0.0F;
      return new AffixBudget(var2);
   }

   double capacity() {
      return this.capacity;
   }

   double remaining() {
      return this.remaining;
   }

   boolean canAfford(double var1) {
      return Math.max(0.01, var1) <= this.remaining + 1.0E-9;
   }

   void spend(double var1) {
      this.remaining -= Math.max(0.01, var1);
   }

   static List<AffixTemplate> select(List<AffixTemplate> var0, double var1, int var3, Random var4) {
      AffixBudget var5 = of(var1);
      ArrayList<AffixTemplate> var6 = new ArrayList<>(var0);
      ArrayList<AffixTemplate> var7 = new ArrayList<>();
      LinkedHashSet<String> var8 = new LinkedHashSet<>();
      boolean var9 = false;
      double var10 = (double)0.0F;

      for(AffixTemplate var13 : var6) {
         var10 += var13.chance();
      }

      while(!var6.isEmpty() && var7.size() < Math.max(0, var3) && var10 > (double)0.0F && Double.isFinite(var10)) {
         int var20 = pickIndex(var6, var10, var4);
         AffixTemplate var21 = (AffixTemplate)var6.remove(var20);
         var10 -= var21.chance();
         if (!var8.contains(var21.group()) && (!var9 || !var21.major())) {
            double var14 = var21.cost();
            if (var5.canAfford(var14)) {
               var5.spend(var14);
               var7.add(var21);
               var8.add(var21.group());
               var9 |= var21.major();
               double var16 = Double.MAX_VALUE;

               for(AffixTemplate var19 : var6) {
                  var16 = Math.min(var16, Math.max(0.01, var19.cost()));
               }

               if (var16 > var5.remaining() + 1.0E-9) {
                  break;
               }
            }
         }
      }

      return List.copyOf(var7);
   }

   private static int pickIndex(List<AffixTemplate> var0, double var1, Random var3) {
      double var4 = var3.nextDouble() * var1;

      for(int var6 = 0; var6 < var0.size(); ++var6) {
         var4 -= ((AffixTemplate)var0.get(var6)).chance();
         if (var4 <= (double)0.0F) {
            return var6;
         }
      }

      return var0.size() - 1;
   }
}
