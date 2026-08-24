package tw.linsy.aelorn.rpgcore.domain.encounter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EncounterDefinitionValidator {
   private EncounterDefinitionValidator() {
   }

   public static List<String> validate(EncounterDefinition definition) {
      List<String> errors = new ArrayList();
      String prefix = "Encounter " + definition.id() + " ";
      if (!definition.id().matches("[a-z0-9_]+")) {
         errors.add(prefix + "has an invalid id");
      }

      if (definition.displayName().isBlank()) {
         errors.add(prefix + "requires a display name");
      }

      if (definition.minimumLevel() < 1 || definition.maximumLevel() < definition.minimumLevel() || definition.maximumLevel() > 200) {
         errors.add(prefix + "has an invalid level range");
      }

      if (definition.arenaRadius() < (double)4.0F || definition.arenaRadius() > (double)16.0F) {
         errors.add(prefix + "arena radius must be between 4 and 16 blocks");
      }

      if (definition.waveDelayTicks() < 10L || definition.waveDelayTicks() > 400L || definition.timeoutTicks() < 400L || definition.timeoutTicks() > 36000L || definition.cooldownSeconds() < 0L || definition.cooldownSeconds() > 86400L || definition.completionExperience() < 1L) {
         errors.add(prefix + "has invalid timing or reward values");
      }

      if (definition.waves().isEmpty() || definition.waves().size() > 10) {
         errors.add(prefix + "must define between 1 and 10 waves");
      }

      Set<String> waveIds = new HashSet();

      for(EncounterWaveDefinition wave : definition.waves()) {
         if (!wave.id().matches("[a-z0-9_]+") || !waveIds.add(wave.id())) {
            errors.add(prefix + "has an invalid or duplicate wave id: " + wave.id());
         }

         if (wave.displayName().isBlank()) {
            errors.add(prefix + "wave " + wave.id() + " requires a display name");
         }

         if (wave.monsterCount() < 1 || wave.monsterCount() > 16) {
            errors.add(prefix + "wave " + wave.id() + " must contain 1 to 16 monsters");
         }

         for(EncounterSpawnDefinition spawn : wave.spawns()) {
            if (!spawn.monsterId().matches("[a-z0-9_]+") || spawn.amount() < 1 || spawn.amount() > 16 || spawn.levelOffset() < -20 || spawn.levelOffset() > 20) {
               errors.add(prefix + "wave " + wave.id() + " has invalid spawn values");
            }
         }
      }

      return errors;
   }
}
