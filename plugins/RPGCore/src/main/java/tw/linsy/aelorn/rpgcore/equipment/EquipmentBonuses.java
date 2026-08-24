package tw.linsy.aelorn.rpgcore.equipment;

import tw.linsy.aelorn.rpgcore.domain.stats.PrimarySkill;
import java.util.EnumMap;
import java.util.Map;

public record EquipmentBonuses(Map<EquipmentStatType, Integer> stats) {
   public EquipmentBonuses(Map<EquipmentStatType, Integer> stats) {
      EnumMap<EquipmentStatType, Integer> normalized = new EnumMap(EquipmentStatType.class);
      if (stats != null) {
         stats.forEach((type, value) -> normalized.put(type, value == null ? 0 : value));
      }

      stats = Map.copyOf(normalized);
      this.stats = stats;
   }

   public static EquipmentBonuses empty() {
      return new EquipmentBonuses(Map.of());
   }

   public int value(EquipmentStatType type) {
      return (Integer)this.stats.getOrDefault(type, 0);
   }

   public int primarySkillBonus(PrimarySkill skill) {
      return this.value(skill.equipmentStatType());
   }

   public boolean isEmpty() {
      return this.stats.isEmpty();
   }
}
