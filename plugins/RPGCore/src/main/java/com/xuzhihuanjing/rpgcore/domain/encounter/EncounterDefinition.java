package com.xuzhihuanjing.rpgcore.domain.encounter;

import java.util.List;
import java.util.Objects;

public record EncounterDefinition(String id, String displayName, int minimumLevel, int maximumLevel, double arenaRadius, long waveDelayTicks, long timeoutTicks, long cooldownSeconds, long completionExperience, List<EncounterWaveDefinition> waves) {
   public EncounterDefinition(String id, String displayName, int minimumLevel, int maximumLevel, double arenaRadius, long waveDelayTicks, long timeoutTicks, long cooldownSeconds, long completionExperience, List<EncounterWaveDefinition> waves) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(displayName, "displayName");
      waves = List.copyOf(waves);
      this.id = id;
      this.displayName = displayName;
      this.minimumLevel = minimumLevel;
      this.maximumLevel = maximumLevel;
      this.arenaRadius = arenaRadius;
      this.waveDelayTicks = waveDelayTicks;
      this.timeoutTicks = timeoutTicks;
      this.cooldownSeconds = cooldownSeconds;
      this.completionExperience = completionExperience;
      this.waves = waves;
   }
}
