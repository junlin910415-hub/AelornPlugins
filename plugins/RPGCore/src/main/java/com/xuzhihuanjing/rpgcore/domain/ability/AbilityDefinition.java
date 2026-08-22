package com.xuzhihuanjing.rpgcore.domain.ability;

import java.util.List;
import java.util.Objects;

public record AbilityDefinition(String id, String classId, String displayName, List<InputToken> input, double manaCost, double cooldownSeconds, AbilityEffectType effectType, double coefficient, double flatPower, double radius, double range, int durationTicks, String description) {
   public AbilityDefinition(String id, String classId, String displayName, List<InputToken> input, double manaCost, double cooldownSeconds, AbilityEffectType effectType, double coefficient, double flatPower, double radius, double range, int durationTicks, String description) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(classId, "classId");
      Objects.requireNonNull(displayName, "displayName");
      input = List.copyOf(input);
      Objects.requireNonNull(effectType, "effectType");
      Objects.requireNonNull(description, "description");
      this.id = id;
      this.classId = classId;
      this.displayName = displayName;
      this.input = input;
      this.manaCost = manaCost;
      this.cooldownSeconds = cooldownSeconds;
      this.effectType = effectType;
      this.coefficient = coefficient;
      this.flatPower = flatPower;
      this.radius = radius;
      this.range = range;
      this.durationTicks = durationTicks;
      this.description = description;
   }
}
