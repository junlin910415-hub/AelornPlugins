package tw.linsy.aelorn.rpgcore.equipment;

import java.util.EnumMap;
import java.util.Map;

public final class MmoItemsEquipmentMapper {
   private MmoItemsEquipmentMapper() {
   }

   public static void merge(EnumMap<EquipmentStatType, Integer> totals, Map<String, Double> stats) {
      for(EquipmentStatType type : EquipmentStatType.values()) {
         addFirst(totals, stats, type, (String[])type.externalKeys().toArray((x$0) -> new String[x$0]));
      }

   }

   public static boolean meetsRequirements(String requiredClass, int requiredLevel, String characterClass, int characterLevel) {
      if (requiredLevel > characterLevel) {
         return false;
      } else if (requiredClass != null && !requiredClass.isBlank()) {
         for(String candidate : requiredClass.split("[,|/]+")) {
            if (candidate.trim().equalsIgnoreCase(characterClass)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private static void addFirst(EnumMap<EquipmentStatType, Integer> totals, Map<String, Double> stats, EquipmentStatType target, String... candidates) {
      for(String candidate : candidates) {
         Double value = (Double)stats.get(candidate);
         if (value != null && Double.isFinite(value) && !(Math.abs(value) < 1.0E-6)) {
            int rounded = (int)Math.max(-2147483648L, Math.min(2147483647L, Math.round(value)));
            if (rounded != 0) {
               totals.merge(target, rounded, Integer::sum);
            }

            return;
         }
      }

   }
}
