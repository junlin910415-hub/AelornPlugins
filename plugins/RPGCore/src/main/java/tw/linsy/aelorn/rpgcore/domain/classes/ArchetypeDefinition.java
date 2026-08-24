package tw.linsy.aelorn.rpgcore.domain.classes;

import java.util.Objects;

public record ArchetypeDefinition(String id, String displayName, String description) {
   public ArchetypeDefinition {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(displayName, "displayName");
      Objects.requireNonNull(description, "description");
   }
}
