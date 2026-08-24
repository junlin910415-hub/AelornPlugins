package tw.linsy.aelorn.rpgcore.domain.discovery;

import java.util.ArrayList;
import java.util.List;

public final class DiscoveryDefinitionValidator {
   private DiscoveryDefinitionValidator() {
   }

   public static List<String> validate(DiscoveryDefinition definition) {
      List<String> errors = new ArrayList();
      String prefix = "Discovery " + definition.id() + " ";
      if (!definition.id().matches("[a-z0-9_]+")) {
         errors.add(prefix + "has an invalid id");
      }

      if (definition.displayName().isBlank() || definition.description().isBlank() || definition.iconMaterial().isBlank() || definition.world().isBlank()) {
         errors.add(prefix + "requires complete presentation and location data");
      }

      if (definition.minimumLevel() < 1 || definition.minimumLevel() > 200 || definition.rewardExperience() < 1L || !Double.isFinite(definition.x()) || !Double.isFinite(definition.y()) || !Double.isFinite(definition.z()) || !Double.isFinite(definition.radius()) || definition.y() < (double)-2048.0F || definition.y() > (double)2048.0F || definition.radius() < (double)1.0F || definition.radius() > (double)128.0F) {
         errors.add(prefix + "has invalid progression or location values");
      }

      if (definition.prerequisites().contains(definition.id()) || definition.prerequisites().stream().distinct().count() != (long)definition.prerequisites().size()) {
         errors.add(prefix + "has an invalid prerequisite list");
      }

      if (definition.requiredQuests().stream().distinct().count() != (long)definition.requiredQuests().size()) {
         errors.add(prefix + "has duplicate required quests");
      }

      for(String id : definition.prerequisites()) {
         if (!id.matches("[a-z0-9_]+")) {
            errors.add(prefix + "has invalid prerequisite " + id);
         }
      }

      for(String id : definition.requiredQuests()) {
         if (!id.matches("[a-z0-9_]+")) {
            errors.add(prefix + "has invalid required quest " + id);
         }
      }

      return errors;
   }
}
