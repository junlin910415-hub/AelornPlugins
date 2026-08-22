package com.xuzhihuanjing.rpgcore.equipment;

import java.util.Map;
import java.util.Objects;

public record MonsterEquipmentDropDefinition(String templateId, double chance, int minimumLevelOffset, int maximumLevelOffset, Map<EquipmentRarity, Double> rarityWeights) {
   public MonsterEquipmentDropDefinition(String templateId, double chance, int minimumLevelOffset, int maximumLevelOffset, Map<EquipmentRarity, Double> rarityWeights) {
      Objects.requireNonNull(templateId, "templateId");
      rarityWeights = Map.copyOf(rarityWeights);
      if (!(chance <= (double)0.0F) && !(chance > (double)1.0F)) {
         if (maximumLevelOffset < minimumLevelOffset) {
            throw new IllegalArgumentException("Equipment drop level offset is invalid");
         } else {
            this.templateId = templateId;
            this.chance = chance;
            this.minimumLevelOffset = minimumLevelOffset;
            this.maximumLevelOffset = maximumLevelOffset;
            this.rarityWeights = rarityWeights;
         }
      } else {
         throw new IllegalArgumentException("Equipment drop chance must be between 0 and 1");
      }
   }
}
