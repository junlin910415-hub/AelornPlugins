package com.xuzhihuanjing.rpgcore.domain.monster;

import java.util.Objects;

public record MonsterDropDefinition(String material, String displayName, double chance, int minimumAmount, int maximumAmount) {
   public MonsterDropDefinition {
      Objects.requireNonNull(material, "material");
      Objects.requireNonNull(displayName, "displayName");
   }
}
