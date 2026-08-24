package tw.linsy.aelorn.rpgcore.domain.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class AbilityDefinitionValidator {
   private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]+$");

   private AbilityDefinitionValidator() {
   }

   public static List<String> validate(AbilityDefinition definition) {
      List<String> errors = new ArrayList();
      if (!ID_PATTERN.matcher(definition.id()).matches()) {
         errors.add("Invalid ability id: " + definition.id());
      }

      if (!ID_PATTERN.matcher(definition.classId()).matches()) {
         errors.add("Invalid ability class id: " + definition.classId());
      }

      if (definition.displayName().isBlank() || definition.description().isBlank()) {
         errors.add("Ability " + definition.id() + " has incomplete presentation data");
      }

      if (definition.input().size() != 3 || definition.input().getFirst() != InputToken.RIGHT) {
         errors.add("Ability " + definition.id() + " must use a three-token combo beginning with RIGHT");
      }

      if (definition.manaCost() < (double)0.0F || definition.cooldownSeconds() < (double)0.0F || definition.coefficient() < (double)0.0F || definition.flatPower() < (double)0.0F || definition.radius() < (double)0.0F || definition.range() < (double)0.0F || definition.durationTicks() < 0) {
         errors.add("Ability " + definition.id() + " has a negative numeric value");
      }

      return List.copyOf(errors);
   }
}
