package com.xuzhihuanjing.rpgcore.config;

import com.xuzhihuanjing.rpgcore.domain.monster.MonsterArchetype;
import com.xuzhihuanjing.rpgcore.domain.monster.MonsterDefinition;
import com.xuzhihuanjing.rpgcore.domain.monster.MonsterDefinitionValidator;
import com.xuzhihuanjing.rpgcore.domain.monster.MonsterDropDefinition;
import com.xuzhihuanjing.rpgcore.domain.monster.MonsterRank;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentRarity;
import com.xuzhihuanjing.rpgcore.equipment.MonsterEquipmentDropDefinition;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

public final class MonsterRegistry {
   private volatile Map<String, MonsterDefinition> monsters = Map.of();

   public void load(File file) {
      this.load(file, (EquipmentRegistry)null);
   }

   public void load(File file, EquipmentRegistry equipmentRegistry) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      if (yaml.getInt("schema-version", -1) != 2) {
         throw new IllegalArgumentException("Unsupported monsters.yml schema-version");
      } else {
         ConfigurationSection root = yaml.getConfigurationSection("monsters");
         if (root != null && !root.getKeys(false).isEmpty()) {
            Map<String, MonsterDefinition> loaded = new LinkedHashMap();
            List<String> errors = new ArrayList();

            for(String id : root.getKeys(false)) {
               ConfigurationSection section = root.getConfigurationSection(id);
               if (section == null) {
                  errors.add("Invalid monster section: " + id);
               } else {
                  MonsterDefinition definition = this.readDefinition(id, section, errors);
                  if (definition != null) {
                     errors.addAll(MonsterDefinitionValidator.validate(definition));
                     this.validateEntityType(definition, errors);
                     this.validateDrops(definition, errors);
                     this.validateEquipmentDrops(definition, equipmentRegistry, errors);
                     if (loaded.putIfAbsent(id, definition) != null) {
                        errors.add("Duplicate monster id: " + id);
                     }
                  }
               }
            }

            if (!errors.isEmpty()) {
               throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
            } else {
               this.monsters = Collections.unmodifiableMap(loaded);
            }
         } else {
            throw new IllegalArgumentException("monsters.yml does not define any monsters");
         }
      }
   }

   private MonsterDefinition readDefinition(String id, ConfigurationSection section, List<String> errors) {
      MonsterRank rank;
      MonsterArchetype archetype;
      try {
         rank = MonsterRank.valueOf(section.getString("rank", "COMMON").toUpperCase(Locale.ROOT));
         archetype = MonsterArchetype.valueOf(section.getString("archetype", "BRUISER").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var13) {
         errors.add("Monster " + id + " has an invalid rank or archetype");
         return null;
      }

      List<MonsterDropDefinition> drops = new ArrayList();
      ConfigurationSection dropSection = section.getConfigurationSection("drops");
      if (dropSection != null) {
         for(String dropId : dropSection.getKeys(false)) {
            ConfigurationSection drop = dropSection.getConfigurationSection(dropId);
            if (drop == null) {
               errors.add("Monster " + id + " has an invalid drop section " + dropId);
            } else {
               drops.add(new MonsterDropDefinition(drop.getString("material", "STONE").toUpperCase(Locale.ROOT), drop.getString("display-name", dropId), drop.getDouble("chance", (double)1.0F), drop.getInt("minimum", 1), drop.getInt("maximum", 1)));
            }
         }
      }

      List<MonsterEquipmentDropDefinition> equipmentDrops = new ArrayList();
      ConfigurationSection equipmentDropSection = section.getConfigurationSection("equipment-drops");
      if (equipmentDropSection == null) {
         equipmentDropSection = section.getConfigurationSection("gear-drops");
      }

      if (equipmentDropSection != null) {
         for(String dropId : equipmentDropSection.getKeys(false)) {
            ConfigurationSection drop = equipmentDropSection.getConfigurationSection(dropId);
            if (drop == null) {
               errors.add("Monster " + id + " has an invalid equipment drop section " + dropId);
            } else {
               equipmentDrops.add(new MonsterEquipmentDropDefinition(drop.getString("template", dropId).toLowerCase(Locale.ROOT), drop.getDouble("chance", (double)1.0F), drop.getInt("level-offset.minimum", 0), drop.getInt("level-offset.maximum", 0), this.readRarityWeights(id + "." + dropId, drop.getConfigurationSection("rarity-weights"), errors)));
            }
         }
      }

      return new MonsterDefinition(id, section.getString("display-name", id), section.getString("entity-type", "ZOMBIE").toUpperCase(Locale.ROOT), section.getString("mythic-mob", "RPGCore_" + id), rank, archetype, section.getInt("level.base", 1), section.getInt("level.minimum", 1), section.getInt("level.maximum", 1), section.getDouble("stats.health", (double)20.0F), section.getDouble("stats.damage", (double)2.0F), section.getDouble("stats.defense", (double)0.0F), section.getDouble("stats.resistance", (double)0.0F), section.getLong("experience", 1L), section.getDouble("behavior.movement-speed", 0.23), section.getDouble("behavior.follow-range", (double)24.0F), section.getLong("behavior.ability-cooldown-ticks", 100L), section.getDouble("behavior.ability-power", (double)1.0F), drops, equipmentDrops, section.getString("model.id", ""), section.getString("model.animations.idle", "idle"), section.getString("model.animations.attack", "attack"), section.getString("model.animations.hurt", "hurt"), section.getString("model.animations.death", "death"));
   }

   private Map<EquipmentRarity, Double> readRarityWeights(String owner, ConfigurationSection section, List<String> errors) {
      if (section == null) {
         return Map.of();
      } else {
         Map<EquipmentRarity, Double> weights = new LinkedHashMap();

         for(String rarityId : section.getKeys(false)) {
            EquipmentRarity rarity = EquipmentRegistry.parseRarity(owner, rarityId, errors);
            if (rarity != null) {
               weights.put(rarity, section.getDouble(rarityId));
            }
         }

         return weights;
      }
   }

   private void validateEntityType(MonsterDefinition definition, List<String> errors) {
      try {
         EntityType type = EntityType.valueOf(definition.entityType());
         Class<?> entityClass = type.getEntityClass();
         if (entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)) {
            errors.add("Monster " + definition.id() + " entity type is not living");
         }
      } catch (IllegalArgumentException var5) {
         String var10001 = definition.id();
         errors.add("Monster " + var10001 + " has unknown entity type " + definition.entityType());
      }

   }

   private void validateDrops(MonsterDefinition definition, List<String> errors) {
      for(MonsterDropDefinition drop : definition.drops()) {
         if (Material.matchMaterial(drop.material()) == null) {
            String var10001 = definition.id();
            errors.add("Monster " + var10001 + " has unknown drop material " + drop.material());
         }
      }

   }

   private void validateEquipmentDrops(MonsterDefinition definition, EquipmentRegistry equipmentRegistry, List<String> errors) {
      if (equipmentRegistry != null) {
         for(MonsterEquipmentDropDefinition drop : definition.equipmentDrops()) {
            if (equipmentRegistry.find(drop.templateId()).isEmpty()) {
               String var10001 = definition.id();
               errors.add("Monster " + var10001 + " references unknown equipment template " + drop.templateId());
            }

            for(Map.Entry<EquipmentRarity, Double> entry : drop.rarityWeights().entrySet()) {
               if ((Double)entry.getValue() <= (double)0.0F) {
                  String var8 = definition.id();
                  errors.add("Monster " + var8 + " has invalid rarity weight for " + ((EquipmentRarity)entry.getKey()).id());
               }
            }
         }

      }
   }

   public Optional<MonsterDefinition> find(String id) {
      return Optional.ofNullable((MonsterDefinition)this.monsters.get(id));
   }

   public Collection<MonsterDefinition> all() {
      return this.monsters.values();
   }

   public int size() {
      return this.monsters.size();
   }

   public void replaceWith(MonsterRegistry source) {
      this.monsters = source.monsters;
   }
}
