package tw.linsy.aelorn.mythiccore.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class StatSnapshot {
   private final Map<String, Double> stats;

   public StatSnapshot(Map<String, Double> var1) {
      LinkedHashMap var2 = new LinkedHashMap();
      if (var1 != null) {
         for(Map.Entry var4 : var1.entrySet()) {
            if (var4.getKey() != null && var4.getValue() != null) {
               var2.put(normalize((String)var4.getKey()), (Double)var4.getValue());
            }
         }
      }

      this.stats = Collections.unmodifiableMap(var2);
   }

   public double get(String var1) {
      return (Double)this.stats.getOrDefault(normalize(var1), (double)0.0F);
   }

   public Map<String, Double> asMap() {
      return this.stats;
   }

   public StatSnapshot plus(Map<String, Double> var1) {
      LinkedHashMap var2 = new LinkedHashMap(this.stats);
      if (var1 != null) {
         for(Map.Entry var4 : var1.entrySet()) {
            if (var4.getKey() != null && var4.getValue() != null) {
               String var5 = normalize((String)var4.getKey());
               var2.merge(var5, (Double)var4.getValue(), Double::sum);
            }
         }
      }

      return new StatSnapshot(var2);
   }

   public static String normalize(String var0) {
      return var0 == null ? "" : var0.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
   }
}
