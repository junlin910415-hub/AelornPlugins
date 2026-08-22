package com.xuzhihuanjing.rpgcore.progression;

import com.xuzhihuanjing.rpgcore.domain.profession.ProfessionType;

public record ProfessionGainResult(ProfessionType profession, long awardedExperience, int previousLevel, int level, long currentLevelExperience, long requiredLevelExperience) {
   public boolean leveledUp() {
      return this.level > this.previousLevel;
   }
}
