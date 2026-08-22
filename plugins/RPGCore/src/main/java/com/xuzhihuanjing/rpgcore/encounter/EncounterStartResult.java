package com.xuzhihuanjing.rpgcore.encounter;

import java.util.UUID;

public record EncounterStartResult(Status status, UUID encounterId, long remainingCooldownSeconds) {
   public static EncounterStartResult started(UUID id) {
      return new EncounterStartResult(EncounterStartResult.Status.STARTED, id, 0L);
   }

   public static EncounterStartResult rejected(Status status, long remainingCooldownSeconds) {
      return new EncounterStartResult(status, (UUID)null, Math.max(0L, remainingCooldownSeconds));
   }

   public static enum Status {
      STARTED,
      INVALID_LEVEL,
      OVERLAPPING,
      COOLDOWN;
   }
}
