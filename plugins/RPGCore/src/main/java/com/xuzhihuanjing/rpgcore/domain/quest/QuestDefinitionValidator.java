package com.xuzhihuanjing.rpgcore.domain.quest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class QuestDefinitionValidator {
   private QuestDefinitionValidator() {
   }

   public static List<String> validate(QuestDefinition definition) {
      List<String> errors = new ArrayList();
      String prefix = "Quest " + definition.id() + " ";
      if (!definition.id().matches("[a-z0-9_]+")) {
         errors.add(prefix + "has an invalid id");
      }

      if (definition.displayName().isBlank() || definition.description().isBlank()) {
         errors.add(prefix + "requires a display name and description");
      }

      if (definition.iconMaterial().isBlank() || definition.minimumLevel() < 1 || definition.minimumLevel() > 200 || definition.rewardExperience() < 1L) {
         errors.add(prefix + "has invalid presentation or progression values");
      }

      if (definition.prerequisites().contains(definition.id()) || definition.prerequisites().stream().distinct().count() != (long)definition.prerequisites().size()) {
         errors.add(prefix + "has an invalid prerequisite list");
      }

      if (definition.objectives().isEmpty() || definition.objectives().size() > 12) {
         errors.add(prefix + "must define between 1 and 12 objectives");
      }

      Set<String> objectiveIds = new HashSet();

      for(QuestObjectiveDefinition objective : definition.objectives()) {
         if (!objective.id().matches("[a-z0-9_]+") || !objectiveIds.add(objective.id()) || !objective.target().matches("(?i)[a-z0-9_:-]+") || objective.requiredAmount() < 1 || objective.requiredAmount() > 10000 || objective.description().isBlank()) {
            errors.add(prefix + "has invalid objective " + objective.id());
         }
      }

      return errors;
   }
}
