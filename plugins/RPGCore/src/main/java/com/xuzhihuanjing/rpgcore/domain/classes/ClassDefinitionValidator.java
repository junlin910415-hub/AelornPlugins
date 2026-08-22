package com.xuzhihuanjing.rpgcore.domain.classes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class ClassDefinitionValidator {
   private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]+$");

   private ClassDefinitionValidator() {
   }

   public static List<String> validate(CharacterClassDefinition definition) {
      List<String> errors = new ArrayList();
      if (!ID_PATTERN.matcher(definition.id()).matches()) {
         errors.add("Class id must match [a-z0-9_]+: " + definition.id());
      }

      if (definition.displayName().isBlank()) {
         errors.add("Class " + definition.id() + " has no display name");
      }

      if (definition.weapon().isBlank()) {
         errors.add("Class " + definition.id() + " has no weapon");
      }

      if (definition.castingMaterial().isBlank()) {
         errors.add("Class " + definition.id() + " has no casting material");
      }

      if (definition.role().isBlank() || definition.description().isEmpty()) {
         errors.add("Class " + definition.id() + " has no role or description");
      }

      if (!definition.baseStats().isValid()) {
         errors.add("Class " + definition.id() + " has invalid base stats");
      }

      if (!definition.balance().isValid()) {
         errors.add("Class " + definition.id() + " has invalid balance values or ratings");
      }

      if (definition.archetypes().size() != 3) {
         errors.add("Class " + definition.id() + " must define exactly three archetypes");
      }

      Set<String> archetypeIds = new HashSet();

      for(ArchetypeDefinition archetype : definition.archetypes()) {
         if (!ID_PATTERN.matcher(archetype.id()).matches()) {
            String var10001 = definition.id();
            errors.add("Invalid archetype id in " + var10001 + ": " + archetype.id());
         }

         if (!archetypeIds.add(archetype.id())) {
            String var5 = definition.id();
            errors.add("Duplicate archetype id in " + var5 + ": " + archetype.id());
         }

         if (archetype.displayName().isBlank() || archetype.description().isBlank()) {
            String var6 = archetype.id();
            errors.add("Archetype " + var6 + " in " + definition.id() + " is incomplete");
         }
      }

      return List.copyOf(errors);
   }
}
