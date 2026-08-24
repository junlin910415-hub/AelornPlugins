package tw.linsy.aelorn.rpgcore.progression;

public record ProgressionResult(long awardedExperience, long totalExperience, int previousLevel, int level, long currentLevelExperience, long requiredLevelExperience) {
   public boolean leveledUp() {
      return this.level > this.previousLevel;
   }
}
