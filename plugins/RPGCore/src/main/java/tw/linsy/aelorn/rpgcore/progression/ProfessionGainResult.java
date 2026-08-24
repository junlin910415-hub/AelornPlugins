package tw.linsy.aelorn.rpgcore.progression;

import tw.linsy.aelorn.rpgcore.domain.profession.ProfessionType;

public record ProfessionGainResult(ProfessionType profession, long awardedExperience, int previousLevel, int level, long currentLevelExperience, long requiredLevelExperience) {
   public boolean leveledUp() {
      return this.level > this.previousLevel;
   }
}
