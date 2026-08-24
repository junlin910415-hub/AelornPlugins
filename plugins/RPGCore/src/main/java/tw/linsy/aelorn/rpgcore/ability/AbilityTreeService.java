package tw.linsy.aelorn.rpgcore.ability;

import tw.linsy.aelorn.rpgcore.config.AbilityTreeRegistry;
import tw.linsy.aelorn.rpgcore.domain.ability.AbilityTreeNodeDefinition;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public final class AbilityTreeService {
   private final AbilityTreeRegistry registry;
   private final CharacterService characterService;

   public AbilityTreeService(AbilityTreeRegistry registry, CharacterService characterService) {
      this.registry = registry;
      this.characterService = characterService;
   }

   public int earnedPoints(CharacterProfile character) {
      return Math.min(50, 1 + Math.max(0, character.level() - 1) / 2);
   }

   public int spentPoints(CharacterProfile character) {
      var var10000 = character.unlockedAbilityNodes().stream();
      AbilityTreeRegistry var10001 = this.registry;
      Objects.requireNonNull(var10001);
      return var10000.map(var10001::find).flatMap(Optional::stream).filter((node) -> node.classId().equals(character.classId())).mapToInt(AbilityTreeNodeDefinition::cost).sum();
   }

   public int availablePoints(CharacterProfile character) {
      return Math.max(0, this.earnedPoints(character) - this.spentPoints(character));
   }

   public List<Integer> nextAwardLevels(CharacterProfile character, int limit) {
      List<Integer> levels = new ArrayList();
      int currentEarned = this.earnedPoints(character);

      for(int level = character.level() + 1; level <= 120 && levels.size() < limit; ++level) {
         CharacterProfile simulated = character.withProgression(level, character.experience());
         if (this.earnedPoints(simulated) > currentEarned) {
            levels.add(level);
            currentEarned = this.earnedPoints(simulated);
         }
      }

      return List.copyOf(levels);
   }

   public UnlockResult unlock(UUID ownerId, AbilityTreeNodeDefinition node) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(ownerId).orElseThrow();
      if (!node.classId().equals(character.classId())) {
         return AbilityTreeService.UnlockResult.WRONG_CLASS;
      } else if (character.unlockedAbilityNodes().contains(node.id())) {
         return AbilityTreeService.UnlockResult.ALREADY_UNLOCKED;
      } else if (character.level() < node.minimumLevel()) {
         return AbilityTreeService.UnlockResult.LEVEL_REQUIRED;
      } else if (!character.unlockedAbilityNodes().containsAll(node.prerequisites())) {
         return AbilityTreeService.UnlockResult.PREREQUISITE_REQUIRED;
      } else if (this.availablePoints(character) < node.cost()) {
         return AbilityTreeService.UnlockResult.NOT_ENOUGH_POINTS;
      } else {
         Set<String> unlocked = new HashSet(character.unlockedAbilityNodes());
         unlocked.add(node.id());
         this.characterService.updateActiveCharacter(ownerId, (current) -> current.withAbilityNodes(unlocked));
         return AbilityTreeService.UnlockResult.SUCCESS;
      }
   }

   public void reset(UUID ownerId) {
      this.characterService.updateActiveCharacter(ownerId, (character) -> character.withAbilityNodes(Set.of()));
   }

   public static enum UnlockResult {
      SUCCESS,
      ALREADY_UNLOCKED,
      WRONG_CLASS,
      LEVEL_REQUIRED,
      PREREQUISITE_REQUIRED,
      NOT_ENOUGH_POINTS;
   }
}
