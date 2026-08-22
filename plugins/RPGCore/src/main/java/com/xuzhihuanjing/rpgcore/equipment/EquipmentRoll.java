package com.xuzhihuanjing.rpgcore.equipment;

import java.util.Map;

public record EquipmentRoll(Map<EquipmentStatType, Integer> stats, Map<EquipmentStatType, Double> qualities) {
   public EquipmentRoll(Map<EquipmentStatType, Integer> stats, Map<EquipmentStatType, Double> qualities) {
      stats = Map.copyOf(stats);
      qualities = Map.copyOf(qualities);
      this.stats = stats;
      this.qualities = qualities;
   }
}
