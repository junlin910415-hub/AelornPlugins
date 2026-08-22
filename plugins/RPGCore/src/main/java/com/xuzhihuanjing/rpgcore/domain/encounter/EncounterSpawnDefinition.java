package com.xuzhihuanjing.rpgcore.domain.encounter;

import java.util.Objects;

public record EncounterSpawnDefinition(String monsterId, int amount, int levelOffset) {
   public EncounterSpawnDefinition {
      Objects.requireNonNull(monsterId, "monsterId");
   }
}
