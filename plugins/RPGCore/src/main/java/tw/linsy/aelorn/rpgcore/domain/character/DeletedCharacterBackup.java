package tw.linsy.aelorn.rpgcore.domain.character;

import java.time.Instant;
import java.util.Objects;

public record DeletedCharacterBackup(CharacterProfile character, Instant deletedAt) {
   public DeletedCharacterBackup {
      Objects.requireNonNull(character, "character");
      Objects.requireNonNull(deletedAt, "deletedAt");
   }
}
