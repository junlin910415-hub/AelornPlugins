package tw.linsy.aelorn.rpgcore.progression;

import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.profession.ProfessionProgress;
import tw.linsy.aelorn.rpgcore.domain.profession.ProfessionType;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.EnumMap;
import java.util.UUID;

public final class ProfessionService {
   public static final int MAXIMUM_LEVEL = 120;
   private final CharacterService characterService;

   public ProfessionService(CharacterService characterService) {
      this.characterService = characterService;
   }

   public ProfessionGainResult grantExperience(UUID ownerId, ProfessionType profession, long requestedExperience) {
      CharacterProfile before = (CharacterProfile)this.characterService.activeCharacter(ownerId).orElseThrow(() -> new IllegalStateException("Account has no active character"));
      ProfessionProgress previous = (ProfessionProgress)before.professions().getOrDefault(profession, ProfessionProgress.fresh());
      long awarded = Math.max(0L, requestedExperience);
      ProfessionProgress next = this.advance(previous, awarded);
      if (!next.equals(previous)) {
         this.characterService.updateActiveCharacter(ownerId, (character) -> {
            EnumMap<ProfessionType, ProfessionProgress> updated = new EnumMap(ProfessionType.class);
            updated.putAll(character.professions());
            updated.put(profession, next);
            return character.withProfessions(updated);
         });
      }

      return this.describe(profession, previous.level(), next, awarded);
   }

   public ProfessionProgress progress(CharacterProfile character, ProfessionType profession) {
      return (ProfessionProgress)character.professions().getOrDefault(profession, ProfessionProgress.fresh());
   }

   public int totalProfessionLevel(CharacterProfile character) {
      return character.professions().values().stream().mapToInt(ProfessionProgress::level).sum();
   }

   public long experienceToNextLevel(int level) {
      if (level >= 120) {
         return 0L;
      } else {
         double index = Math.max((double)1.0F, (double)level);
         return Math.max(10L, Math.round((double)24.0F + (double)9.5F * Math.pow(index, 1.55) + 0.85 * index * index));
      }
   }

   public double progressRatio(ProfessionProgress progress) {
      long required = this.experienceToNextLevel(progress.level());
      return required <= 0L ? (double)1.0F : Math.max((double)0.0F, Math.min((double)1.0F, (double)progress.experience() / (double)required));
   }

   public String compactProgress(ProfessionProgress progress) {
      long required = this.experienceToNextLevel(progress.level());
      if (required <= 0L) {
         return "MAX";
      } else {
         long var10000 = progress.experience();
         return var10000 + "/" + required;
      }
   }

   private ProfessionProgress advance(ProfessionProgress start, long awarded) {
      int level = start.level();

      long experience;
      for(experience = start.experience() + awarded; level < 120; ++level) {
         long required = this.experienceToNextLevel(level);
         if (experience < required) {
            break;
         }

         experience -= required;
      }

      return level >= 120 ? new ProfessionProgress(120, 0L) : new ProfessionProgress(level, experience);
   }

   private ProfessionGainResult describe(ProfessionType profession, int previousLevel, ProfessionProgress progress, long awarded) {
      return new ProfessionGainResult(profession, awarded, previousLevel, progress.level(), progress.experience(), this.experienceToNextLevel(progress.level()));
   }
}
