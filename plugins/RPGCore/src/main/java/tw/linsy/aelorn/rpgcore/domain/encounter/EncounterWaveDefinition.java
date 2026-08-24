package tw.linsy.aelorn.rpgcore.domain.encounter;

import java.util.List;
import java.util.Objects;

public record EncounterWaveDefinition(String id, String displayName, List<EncounterSpawnDefinition> spawns) {
   public EncounterWaveDefinition(String id, String displayName, List<EncounterSpawnDefinition> spawns) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(displayName, "displayName");
      spawns = List.copyOf(spawns);
      this.id = id;
      this.displayName = displayName;
      this.spawns = spawns;
   }

   public int monsterCount() {
      return this.spawns.stream().mapToInt(EncounterSpawnDefinition::amount).sum();
   }
}
