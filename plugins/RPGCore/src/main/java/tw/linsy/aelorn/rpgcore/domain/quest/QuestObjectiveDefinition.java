package tw.linsy.aelorn.rpgcore.domain.quest;

import java.util.Objects;

public record QuestObjectiveDefinition(String id, QuestObjectiveType type, String target, int requiredAmount, String description) {
   public QuestObjectiveDefinition {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(target, "target");
      Objects.requireNonNull(description, "description");
   }
}
