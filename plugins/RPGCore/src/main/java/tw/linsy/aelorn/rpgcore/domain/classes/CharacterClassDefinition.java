package tw.linsy.aelorn.rpgcore.domain.classes;

import tw.linsy.aelorn.rpgcore.domain.stats.BaseStats;
import java.util.List;
import java.util.Objects;

public record CharacterClassDefinition(String id, String displayName, String iconMaterial, String castingMaterial, String weapon, String role, List<String> description, BaseStats baseStats, ClassBalance balance, List<ArchetypeDefinition> archetypes) {
   public CharacterClassDefinition(String id, String displayName, String iconMaterial, String castingMaterial, String weapon, String role, List<String> description, BaseStats baseStats, ClassBalance balance, List<ArchetypeDefinition> archetypes) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(displayName, "displayName");
      Objects.requireNonNull(iconMaterial, "iconMaterial");
      Objects.requireNonNull(castingMaterial, "castingMaterial");
      Objects.requireNonNull(weapon, "weapon");
      Objects.requireNonNull(role, "role");
      description = List.copyOf(description);
      Objects.requireNonNull(baseStats, "baseStats");
      Objects.requireNonNull(balance, "balance");
      archetypes = List.copyOf(archetypes);
      this.id = id;
      this.displayName = displayName;
      this.iconMaterial = iconMaterial;
      this.castingMaterial = castingMaterial;
      this.weapon = weapon;
      this.role = role;
      this.description = description;
      this.baseStats = baseStats;
      this.balance = balance;
      this.archetypes = archetypes;
   }
}
