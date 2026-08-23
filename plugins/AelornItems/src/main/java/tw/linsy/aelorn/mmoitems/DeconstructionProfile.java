package tw.linsy.aelorn.mmoitems;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.bukkit.configuration.ConfigurationSection;

record DeconstructionProfile(List<DropPool> successPools, List<DropPool> losePools) {
   static DeconstructionProfile empty() {
      return new DeconstructionProfile(List.of(), List.of());
   }

   static DeconstructionProfile from(ConfigurationSection var0) {
      return var0 == null ? empty() : new DeconstructionProfile(poolsFrom(var0.getConfigurationSection("success")), poolsFrom(var0.getConfigurationSection("lose")));
   }

   boolean hasDrops() {
      return !this.successPools.isEmpty() || !this.losePools.isEmpty();
   }

   List<DropSpec> roll(boolean var1, Random var2) {
      List var4 = var1 ? this.successPools : this.losePools;
      if (var4.isEmpty()) {
         var4 = this.successPools.isEmpty() ? this.losePools : this.successPools;
      }

      DropPool var3;
      return (var3 = choosePool(var4, var2)) == null ? List.of() : var3.roll(var2);
   }

   private static List<DropPool> poolsFrom(ConfigurationSection var0) {
      if (var0 == null) {
         return List.of();
      } else {
         ArrayList var1 = new ArrayList();
         if (var0.isConfigurationSection("items")) {
            var1.add(DeconstructionProfile.DropPool.from(var0));
            return List.copyOf(var1);
         } else {
            for(String var3 : var0.getKeys(false)) {
               ConfigurationSection var4 = var0.getConfigurationSection(var3);
               if (var4 != null && var4.isConfigurationSection("items")) {
                  var1.add(DeconstructionProfile.DropPool.from(var4));
               }
            }

            return List.copyOf(var1);
         }
      }
   }

   private static DropPool choosePool(List<DropPool> var0, Random var1) {
      if (var0.isEmpty()) {
         return null;
      } else {
         double var2 = var0.stream().mapToDouble(DropPool::coef).sum();
         double var4 = var1.nextDouble() * Math.max(1.0E-4, var2);
         double var6 = (double)0.0F;

         for(DropPool var9 : var0) {
            if (var4 <= (var6 += Math.max((double)0.0F, var9.coef()))) {
               return var9;
            }
         }

         return (DropPool)var0.get(var0.size() - 1);
      }
   }

   static record DropPool(double coef, List<DropSpec> drops) {
      static DropPool from(ConfigurationSection var0) {
         ArrayList var1 = new ArrayList();
         ConfigurationSection var2 = var0.getConfigurationSection("items");
         if (var2 != null) {
            for(String var4 : var2.getKeys(false)) {
               ConfigurationSection var5 = var2.getConfigurationSection(var4);
               if (var5 != null) {
                  for(String var7 : var5.getKeys(false)) {
                     DropSpec var8 = DeconstructionProfile.DropSpec.from(var4, var7, var5.getString(var7, ""));
                     if (var8 != null) {
                        var1.add(var8);
                     }
                  }
               }
            }
         }

         return new DropPool(Math.max(1.0E-4, var0.getDouble("coef", (double)1.0F)), List.copyOf(var1));
      }

      List<DropSpec> roll(Random var1) {
         ArrayList var2 = new ArrayList();

         for(DropSpec var4 : this.drops) {
            if (var1.nextDouble() <= var4.normalizedChance()) {
               var2.add(var4);
            }
         }

         return List.copyOf(var2);
      }
   }

   static record DropSpec(String type, String id, double chance, int minAmount, int maxAmount, double unidentifiedChance) {
      static DropSpec from(String var0, String var1, String var2) {
         if (var2 != null && !var2.isBlank()) {
            String[] var3 = var2.split(",");
            double var4 = parseDouble(var3.length > 0 ? var3[0] : "100", (double)100.0F);
            int var6 = 1;
            int var7 = 1;
            if (var3.length > 1) {
               String var8 = var3[1].trim();
               if (var8.contains("-")) {
                  String[] var9 = var8.split("-", 2);
                  var6 = Math.max(1, parseInt(var9[0], 1));
                  var7 = Math.max(var6, parseInt(var9[1], var6));
               } else {
                  var7 = var6 = Math.max(1, parseInt(var8, 1));
               }
            }

            double var10 = parseDouble(var3.length > 2 ? var3[2] : "0", (double)0.0F);
            return new DropSpec(var0.toUpperCase(Locale.ROOT), var1.toUpperCase(Locale.ROOT), var4, Math.min(var6, var7), Math.max(var6, var7), var10);
         } else {
            return null;
         }
      }

      int rollAmount(Random var1) {
         return this.minAmount + var1.nextInt(Math.max(1, this.maxAmount - this.minAmount + 1));
      }

      double normalizedChance() {
         return this.chance > (double)1.0F ? this.chance / (double)100.0F : Math.max((double)0.0F, this.chance);
      }

      private static int parseInt(String var0, int var1) {
         try {
            return Integer.parseInt(var0.trim());
         } catch (RuntimeException var3) {
            return var1;
         }
      }

      private static double parseDouble(String var0, double var1) {
         try {
            return Double.parseDouble(var0.trim());
         } catch (RuntimeException var4) {
            return var1;
         }
      }
   }
}
