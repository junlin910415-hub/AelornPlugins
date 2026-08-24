package tw.linsy.aelorn.rpgcore.progression;

import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.UUID;

public final class ProgressionService {
   private final CharacterService characterService;
   private final ExperienceCurve curve;

   public ProgressionService(CharacterService characterService, ExperienceCurve curve) {
      this.characterService = characterService;
      this.curve = curve;
   }

   public ProgressionResult grantExperience(UUID ownerId, long requestedExperience) {
      CharacterProfile before = (CharacterProfile)this.characterService.activeCharacter(ownerId).orElseThrow(() -> new IllegalStateException("Account has no active character"));
      long maximumExperience = this.curve.experienceAtLevel(this.curve.maximumLevel());
      long migratedExperience = Math.max(before.experience(), this.curve.experienceAtLevel(Math.min(before.level(), this.curve.maximumLevel())));
      long total = Math.min(maximumExperience, this.saturatingAdd(migratedExperience, Math.max(0L, requestedExperience)));
      int level = this.curve.levelForExperience(total);
      CharacterProfile after = this.characterService.updateActiveCharacter(ownerId, (character) -> character.withProgression(level, total));
      return this.describe(after, Math.max(0L, total - migratedExperience), before.level());
   }

   public ProgressionResult describe(CharacterProfile character) {
      return this.describe(character, 0L, character.level());
   }

   private ProgressionResult describe(CharacterProfile character, long awardedExperience, int previousLevel) {
      int safeLevel = Math.min(character.level(), this.curve.maximumLevel());
      long levelStart = this.curve.experienceAtLevel(safeLevel);
      long required = this.curve.experienceToNextLevel(safeLevel);
      return new ProgressionResult(awardedExperience, character.experience(), previousLevel, safeLevel, required == 0L ? 0L : Math.max(0L, character.experience() - levelStart), required);
   }

   private long saturatingAdd(long left, long right) {
      return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
   }
}
