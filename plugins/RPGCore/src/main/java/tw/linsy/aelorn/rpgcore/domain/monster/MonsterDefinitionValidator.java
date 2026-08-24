package tw.linsy.aelorn.rpgcore.domain.monster;

import tw.linsy.aelorn.rpgcore.equipment.MonsterEquipmentDropDefinition;
import java.util.ArrayList;
import java.util.List;

public final class MonsterDefinitionValidator {
   private MonsterDefinitionValidator() {
   }

   public static List<String> validate(MonsterDefinition definition) {
      List<String> errors = new ArrayList();
      String prefix = "Monster " + definition.id() + " ";
      if (!definition.id().matches("[a-z0-9_]+")) {
         errors.add(prefix + "has an invalid id");
      }

      if (definition.displayName().isBlank() || definition.entityType().isBlank() || definition.mythicMobId().isBlank()) {
         errors.add(prefix + "requires a display name, entity type, and MythicMobs id");
      }

      if (definition.minimumLevel() < 1 || definition.maximumLevel() < definition.minimumLevel() || definition.baseLevel() < definition.minimumLevel() || definition.baseLevel() > definition.maximumLevel()) {
         errors.add(prefix + "has an invalid level range");
      }

      if (definition.baseHealth() <= (double)0.0F || definition.baseDamage() < (double)0.0F || definition.baseDefense() < (double)0.0F || definition.baseResistance() < (double)0.0F || definition.baseExperience() < 1L) {
         errors.add(prefix + "has invalid base combat values");
      }

      if (definition.movementSpeed() <= (double)0.0F || definition.movementSpeed() > (double)1.0F || definition.followRange() < (double)4.0F || definition.followRange() > (double)128.0F || definition.abilityCooldownTicks() < 10L || definition.abilityPower() < (double)0.0F) {
         errors.add(prefix + "has invalid behavior values");
      }

      for(MonsterDropDefinition drop : definition.drops()) {
         if (drop.chance() <= (double)0.0F || drop.chance() > (double)1.0F || drop.minimumAmount() < 1 || drop.maximumAmount() < drop.minimumAmount() || drop.maximumAmount() > 64) {
            errors.add(prefix + "has an invalid drop for " + drop.material());
         }
      }

      for(MonsterEquipmentDropDefinition drop : definition.equipmentDrops()) {
         if (drop.chance() <= (double)0.0F || drop.chance() > (double)1.0F || drop.maximumLevelOffset() < drop.minimumLevelOffset() || drop.templateId().isBlank()) {
            errors.add(prefix + "has an invalid equipment drop for " + drop.templateId());
         }
      }

      return errors;
   }
}
