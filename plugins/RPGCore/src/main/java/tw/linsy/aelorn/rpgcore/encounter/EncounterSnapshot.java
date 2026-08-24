package tw.linsy.aelorn.rpgcore.encounter;

import java.util.UUID;

public record EncounterSnapshot(UUID runId, String definitionId, String displayName, int level, int currentWave, int waveCount, int remainingMonsters, int participants, String world, int blockX, int blockY, int blockZ) {
}
