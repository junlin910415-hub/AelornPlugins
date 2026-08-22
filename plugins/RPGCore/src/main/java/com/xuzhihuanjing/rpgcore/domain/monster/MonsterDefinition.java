package com.xuzhihuanjing.rpgcore.domain.monster;

import com.xuzhihuanjing.rpgcore.equipment.MonsterEquipmentDropDefinition;
import java.util.List;
import java.util.Objects;

public record MonsterDefinition(String id, String displayName, String entityType, String mythicMobId, MonsterRank rank, MonsterArchetype archetype, int baseLevel, int minimumLevel, int maximumLevel, double baseHealth, double baseDamage, double baseDefense, double baseResistance, long baseExperience, double movementSpeed, double followRange, long abilityCooldownTicks, double abilityPower, List<MonsterDropDefinition> drops, List<MonsterEquipmentDropDefinition> equipmentDrops, String modelId, String idleAnimation, String attackAnimation, String hurtAnimation, String deathAnimation) {
   public MonsterDefinition(String id, String displayName, String entityType, String mythicMobId, MonsterRank rank, MonsterArchetype archetype, int baseLevel, int minimumLevel, int maximumLevel, double baseHealth, double baseDamage, double baseDefense, double baseResistance, long baseExperience, double movementSpeed, double followRange, long abilityCooldownTicks, double abilityPower, List<MonsterDropDefinition> drops, List<MonsterEquipmentDropDefinition> equipmentDrops, String modelId, String idleAnimation, String attackAnimation, String hurtAnimation, String deathAnimation) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(displayName, "displayName");
      Objects.requireNonNull(entityType, "entityType");
      Objects.requireNonNull(mythicMobId, "mythicMobId");
      Objects.requireNonNull(rank, "rank");
      Objects.requireNonNull(archetype, "archetype");
      drops = List.copyOf(drops);
      equipmentDrops = List.copyOf(equipmentDrops);
      modelId = (String)Objects.requireNonNullElse(modelId, "");
      idleAnimation = (String)Objects.requireNonNullElse(idleAnimation, "idle");
      attackAnimation = (String)Objects.requireNonNullElse(attackAnimation, "attack");
      hurtAnimation = (String)Objects.requireNonNullElse(hurtAnimation, "hurt");
      deathAnimation = (String)Objects.requireNonNullElse(deathAnimation, "death");
      this.id = id;
      this.displayName = displayName;
      this.entityType = entityType;
      this.mythicMobId = mythicMobId;
      this.rank = rank;
      this.archetype = archetype;
      this.baseLevel = baseLevel;
      this.minimumLevel = minimumLevel;
      this.maximumLevel = maximumLevel;
      this.baseHealth = baseHealth;
      this.baseDamage = baseDamage;
      this.baseDefense = baseDefense;
      this.baseResistance = baseResistance;
      this.baseExperience = baseExperience;
      this.movementSpeed = movementSpeed;
      this.followRange = followRange;
      this.abilityCooldownTicks = abilityCooldownTicks;
      this.abilityPower = abilityPower;
      this.drops = drops;
      this.equipmentDrops = equipmentDrops;
      this.modelId = modelId;
      this.idleAnimation = idleAnimation;
      this.attackAnimation = attackAnimation;
      this.hurtAnimation = hurtAnimation;
      this.deathAnimation = deathAnimation;
   }

   public MonsterDefinition(String id, String displayName, String entityType, String mythicMobId, MonsterRank rank, MonsterArchetype archetype, int baseLevel, int minimumLevel, int maximumLevel, double baseHealth, double baseDamage, double baseDefense, double baseResistance, long baseExperience, double movementSpeed, double followRange, long abilityCooldownTicks, double abilityPower, List<MonsterDropDefinition> drops, List<MonsterEquipmentDropDefinition> equipmentDrops) {
      this(id, displayName, entityType, mythicMobId, rank, archetype, baseLevel, minimumLevel, maximumLevel, baseHealth, baseDamage, baseDefense, baseResistance, baseExperience, movementSpeed, followRange, abilityCooldownTicks, abilityPower, drops, equipmentDrops, "", "idle", "attack", "hurt", "death");
   }
}
