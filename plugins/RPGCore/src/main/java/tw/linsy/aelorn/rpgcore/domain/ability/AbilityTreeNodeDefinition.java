package tw.linsy.aelorn.rpgcore.domain.ability;

import java.util.List;
import java.util.Objects;

public record AbilityTreeNodeDefinition(String id, String classId, String archetypeId, String displayName, String iconMaterial, int inventorySlot, int cost, int minimumLevel, List<String> prerequisites, AbilityTreeEffectType effectType, double value, String description) {
   public AbilityTreeNodeDefinition(String id, String classId, String archetypeId, String displayName, String iconMaterial, int inventorySlot, int cost, int minimumLevel, List<String> prerequisites, AbilityTreeEffectType effectType, double value, String description) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(classId, "classId");
      Objects.requireNonNull(archetypeId, "archetypeId");
      Objects.requireNonNull(displayName, "displayName");
      Objects.requireNonNull(iconMaterial, "iconMaterial");
      prerequisites = List.copyOf(prerequisites);
      Objects.requireNonNull(effectType, "effectType");
      Objects.requireNonNull(description, "description");
      this.id = id;
      this.classId = classId;
      this.archetypeId = archetypeId;
      this.displayName = displayName;
      this.iconMaterial = iconMaterial;
      this.inventorySlot = inventorySlot;
      this.cost = cost;
      this.minimumLevel = minimumLevel;
      this.prerequisites = prerequisites;
      this.effectType = effectType;
      this.value = value;
      this.description = description;
   }
}
