package tw.linsy.aelorn.rpgcore.progression;

import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.stats.PrimarySkill;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class PrimarySkillService {
   public static final int MAXIMUM_INVESTED_POINTS = 100;
   private final CharacterService characterService;

   public PrimarySkillService(CharacterService characterService) {
      this.characterService = characterService;
   }

   public int earnedPoints(CharacterProfile character) {
      return Math.max(0, (character.level() - 1) * 2);
   }

   public int spentPoints(CharacterProfile character) {
      return character.skillPoints().values().stream().mapToInt(Integer::intValue).sum();
   }

   public int availablePoints(CharacterProfile character) {
      return Math.max(0, this.earnedPoints(character) - this.spentPoints(character));
   }

   public int investedPoints(CharacterProfile character, PrimarySkill skill) {
      return Math.max(0, (Integer)character.skillPoints().getOrDefault(skill, 0));
   }

   public int change(UUID ownerId, PrimarySkill skill, int requestedDelta) {
      if (requestedDelta == 0) {
         return 0;
      } else {
         AtomicInteger applied = new AtomicInteger();
         this.characterService.updateActiveCharacter(ownerId, (character) -> {
            int current = this.investedPoints(character, skill);
            int delta = requestedDelta > 0 ? Math.min(requestedDelta, Math.min(this.availablePoints(character), 100 - current)) : -Math.min(current, Math.abs(requestedDelta));
            if (delta == 0) {
               return character;
            } else {
               EnumMap<PrimarySkill, Integer> updated = new EnumMap(PrimarySkill.class);
               updated.putAll(character.skillPoints());
               updated.put(skill, current + delta);
               applied.set(delta);
               return character.withSkillPoints(updated);
            }
         });
         return applied.get();
      }
   }

   public int reset(UUID ownerId) {
      AtomicInteger removed = new AtomicInteger();
      this.characterService.updateActiveCharacter(ownerId, (character) -> {
         int spent = this.spentPoints(character);
         if (spent <= 0) {
            return character;
         } else {
            removed.set(spent);
            EnumMap<PrimarySkill, Integer> cleared = new EnumMap(PrimarySkill.class);

            for(PrimarySkill skill : PrimarySkill.values()) {
               cleared.put(skill, 0);
            }

            return character.withSkillPoints(cleared);
         }
      });
      return removed.get();
   }

   public double effectPercent(PrimarySkill skill, int totalPoints) {
      int points = Math.max(0, Math.min(200, totalPoints));
      double constant = skill == PrimarySkill.INTELLIGENCE ? (double)160.0F : (double)110.0F;
      return (double)100.0F * (double)points / ((double)points + constant);
   }

   public double damageMultiplier(int strengthPoints) {
      return (double)1.0F + this.effectPercent(PrimarySkill.STRENGTH, strengthPoints) / (double)100.0F;
   }

   public double spellCostReduction(int intelligencePoints) {
      return Math.min(0.65, this.effectPercent(PrimarySkill.INTELLIGENCE, intelligencePoints) / (double)100.0F);
   }

   public double damageTakenMultiplier(int defencePoints) {
      return (double)1.0F - Math.min(0.7, this.effectPercent(PrimarySkill.DEFENCE, defencePoints) / (double)100.0F);
   }

   public double dodgeChance(int agilityPoints) {
      return Math.min(0.55, this.effectPercent(PrimarySkill.AGILITY, agilityPoints) / (double)100.0F);
   }

   public double criticalChance(int dexterityPoints) {
      return Math.min(0.55, this.effectPercent(PrimarySkill.DEXTERITY, dexterityPoints) / (double)100.0F);
   }

   public Map<PrimarySkill, Integer> emptyAllocations() {
      EnumMap<PrimarySkill, Integer> allocations = new EnumMap(PrimarySkill.class);

      for(PrimarySkill skill : PrimarySkill.values()) {
         allocations.put(skill, 0);
      }

      return Map.copyOf(allocations);
   }
}
