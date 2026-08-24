package tw.linsy.aelorn.rpgcore.ability;

import tw.linsy.aelorn.rpgcore.config.AbilityTreeRegistry;
import tw.linsy.aelorn.rpgcore.domain.ability.AbilityModifiers;
import tw.linsy.aelorn.rpgcore.domain.ability.AbilityTreeNodeDefinition;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;

public final class AbilityModifierService {
   private final AbilityTreeRegistry registry;

   public AbilityModifierService(AbilityTreeRegistry registry) {
      this.registry = registry;
   }

   public AbilityModifiers modifiers(CharacterProfile character) {
      double power = (double)0.0F;
      double mana = (double)0.0F;
      double cooldown = (double)0.0F;

      for(String nodeId : character.unlockedAbilityNodes()) {
         AbilityTreeNodeDefinition node = (AbilityTreeNodeDefinition)this.registry.find(nodeId).orElse(null);
         if (node != null && node.classId().equals(character.classId())) {
            switch (node.effectType()) {
               case ABILITY_POWER:
                  power += node.value();
                  break;
               case MANA_EFFICIENCY:
                  mana += node.value();
                  break;
               case COOLDOWN_REDUCTION:
                  cooldown += node.value();
            }
         }
      }

      return new AbilityModifiers(Math.min((double)0.5F, power), Math.min(0.4, mana), Math.min(0.4, cooldown));
   }
}
