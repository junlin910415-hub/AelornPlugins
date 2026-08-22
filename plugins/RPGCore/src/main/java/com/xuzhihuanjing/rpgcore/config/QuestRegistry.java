package com.xuzhihuanjing.rpgcore.config;

import com.xuzhihuanjing.rpgcore.domain.quest.QuestCategory;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestDefinitionValidator;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestObjectiveDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestObjectiveType;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestReward;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestStage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class QuestRegistry {
   private volatile Map<String, QuestDefinition> quests = Map.of();

   public void load(File file, MonsterRegistry monsters, EncounterRegistry encounters, DiscoveryRegistry discoveries) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      if (yaml.getInt("schema-version", -1) != 4) {
         throw new IllegalArgumentException("Unsupported quests.yml schema-version (expected 4)");
      } else {
         ConfigurationSection root = yaml.getConfigurationSection("quests");
         if (root != null && !root.getKeys(false).isEmpty()) {
            Map<String, QuestDefinition> loaded = new LinkedHashMap();
            List<String> errors = new ArrayList();

            for(String id : root.getKeys(false)) {
               ConfigurationSection section = root.getConfigurationSection(id);
               if (section == null) {
                  errors.add("Invalid quest section: " + id);
               } else {
                  QuestDefinition definition = this.readDefinition(id, section, errors);
                  if (definition != null) {
                     errors.addAll(QuestDefinitionValidator.validate(definition));
                     if (Material.matchMaterial(definition.iconMaterial()) == null) {
                        errors.add("Quest " + id + " has unknown icon material " + definition.iconMaterial());
                     }

                     this.validateTargets(definition, monsters, encounters, discoveries, errors);
                     if (loaded.putIfAbsent(id, definition) != null) {
                        errors.add("Duplicate quest id: " + id);
                     }
                  }
               }
            }

            this.validatePrerequisites(loaded, errors);
            if (!errors.isEmpty()) {
               throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
            } else {
               this.quests = Collections.unmodifiableMap(loaded);
            }
         } else {
            throw new IllegalArgumentException("quests.yml does not define any quests");
         }
      }
   }

   private QuestDefinition readDefinition(String id, ConfigurationSection section, List<String> errors) {
      QuestCategory category;
      try {
         category = QuestCategory.valueOf(section.getString("category", "SIDE").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var12) {
         errors.add("Quest " + id + " has an invalid category");
         return null;
      }

      List<QuestObjectiveDefinition> objectives = new ArrayList();
      ConfigurationSection objectiveRoot = section.getConfigurationSection("objectives");
      if (objectiveRoot != null) {
         for(String objectiveId : objectiveRoot.getKeys(false)) {
            ConfigurationSection objective = objectiveRoot.getConfigurationSection(objectiveId);
            if (objective == null) {
               errors.add("Quest " + id + " has invalid objective " + objectiveId);
            } else {
               try {
                  objectives.add(new QuestObjectiveDefinition(objectiveId, QuestObjectiveType.valueOf(objective.getString("type", "KILL_MONSTER").toUpperCase(Locale.ROOT)), objective.getString("target", ""), objective.getInt("amount", 1), objective.getString("description", objectiveId)));
               } catch (IllegalArgumentException var11) {
                  errors.add("Quest " + id + " objective " + objectiveId + " has an invalid type");
               }
            }
         }
      }

      String displayName = section.getString("display-name", id);
      String description = section.getString("description", id);
      String icon = section.getString("icon", "BOOK").toUpperCase(Locale.ROOT);
      int minimumLevel = section.getInt("minimum-level", 1);
      List<String> prerequisites = section.getStringList("prerequisites");
      QuestReward reward = this.readReward(id, section, errors);

      // stages 與扁平 objectives 二擇一;兩個都寫是設定錯誤,不要猜測意圖
      ConfigurationSection stageRoot = section.getConfigurationSection("stages");
      if (stageRoot != null && !stageRoot.getKeys(false).isEmpty()) {
         if (!objectives.isEmpty()) {
            errors.add("Quest " + id + " defines both objectives and stages; use one or the other");
            return null;
         }
         List<QuestStage> stages = new ArrayList<>();
         for (String stageId : stageRoot.getKeys(false)) {
            ConfigurationSection stageSection = stageRoot.getConfigurationSection(stageId);
            if (stageSection == null) {
               errors.add("Quest " + id + " has invalid stage " + stageId);
               continue;
            }
            List<QuestObjectiveDefinition> stageObjectives =
               this.readObjectives(id, stageSection.getConfigurationSection("objectives"), errors);
            if (stageObjectives.isEmpty()) {
               errors.add("Quest " + id + " stage " + stageId + " has no objectives");
            }
            stages.add(new QuestStage(stageId, stageSection.getString("description", stageId), stageObjectives));
         }
         if (stages.isEmpty()) {
            errors.add("Quest " + id + " has no usable stages");
            return null;
         }
         return QuestDefinition.staged(id, displayName, description, category, icon, minimumLevel,
            prerequisites, stages, reward);
      }

      long legacyExperience = section.getLong("reward-experience", 1L);
      QuestReward effective = reward == QuestReward.NONE
         ? QuestReward.experienceOnly(legacyExperience) : reward;
      return QuestDefinition.staged(id, displayName, description, category, icon, minimumLevel,
         prerequisites, List.of(QuestStage.implicit(description, objectives)), effective);
   }

   /** 目標區段的共用解析,扁平格式與階段格式都走這裡。 */
   private List<QuestObjectiveDefinition> readObjectives(String questId, ConfigurationSection root, List<String> errors) {
      List<QuestObjectiveDefinition> objectives = new ArrayList<>();
      if (root == null) {
         return objectives;
      }
      for (String objectiveId : root.getKeys(false)) {
         ConfigurationSection objective = root.getConfigurationSection(objectiveId);
         if (objective == null) {
            errors.add("Quest " + questId + " has invalid objective " + objectiveId);
            continue;
         }
         try {
            objectives.add(new QuestObjectiveDefinition(objectiveId,
               QuestObjectiveType.valueOf(objective.getString("type", "KILL_MONSTER").toUpperCase(Locale.ROOT)),
               objective.getString("target", ""),
               objective.getInt("amount", 1),
               objective.getString("description", objectiveId)));
         } catch (IllegalArgumentException invalidType) {
            errors.add("Quest " + questId + " objective " + objectiveId + " has an invalid type");
         }
      }
      return objectives;
   }

   private QuestReward readReward(String questId, ConfigurationSection section, List<String> errors) {
      ConfigurationSection rewardSection = section.getConfigurationSection("rewards");
      if (rewardSection == null) {
         return QuestReward.NONE;
      }

      List<QuestReward.ItemReward> items = new ArrayList<>();
      for (Map<?, ?> raw : rewardSection.getMapList("items")) {
         Object materialValue = raw.get("material");
         if (materialValue == null) {
            errors.add("Quest " + questId + " has a reward item without material");
            continue;
         }
         Material material = Material.matchMaterial(String.valueOf(materialValue).toUpperCase(Locale.ROOT));
         if (material == null || !material.isItem()) {
            errors.add("Quest " + questId + " has unknown reward material " + materialValue);
            continue;
         }
         int amount = raw.get("amount") instanceof Number number ? number.intValue() : 1;
         Object name = raw.get("name");
         items.add(new QuestReward.ItemReward(material, amount, name == null ? null : String.valueOf(name)));
      }

      Map<String, Long> professions = new LinkedHashMap<>();
      ConfigurationSection professionSection = rewardSection.getConfigurationSection("profession-experience");
      if (professionSection != null) {
         for (String professionId : professionSection.getKeys(false)) {
            professions.put(professionId, professionSection.getLong(professionId, 0L));
         }
      }

      return new QuestReward(rewardSection.getLong("experience", 0L), items, professions);
   }

   private void validateTargets(QuestDefinition definition, MonsterRegistry monsters, EncounterRegistry encounters, DiscoveryRegistry discoveries, List<String> errors) {
      for(QuestObjectiveDefinition objective : definition.objectives()) {
         boolean var10000;
         switch (objective.type()) {
            case KILL_MONSTER -> var10000 = monsters.find(objective.target()).isPresent();
            case COMPLETE_ENCOUNTER -> var10000 = encounters.find(objective.target()).isPresent();
            case DISCOVER_LOCATION -> var10000 = discoveries.find(objective.target()).isPresent();
            case COLLECT_ITEM -> var10000 = objective.target().matches("(?i)[a-z0-9_-]+:[a-z0-9_-]+");
            case TALK_TO_NPC -> var10000 = !objective.target().isBlank();
            case USE_ITEM -> var10000 = objective.target().matches("(?i)[a-z0-9_-]+:[a-z0-9_-]+");
            // NPC id / 物品類型:物品 id
            case DELIVER_ITEM -> var10000 = objective.target().matches("(?i)[a-z0-9_-]+/[a-z0-9_-]+:[a-z0-9_-]+");
            case CAST_ABILITY -> var10000 = !objective.target().isBlank();
            case REACH_LEVEL -> var10000 = objective.target().matches("[1-9][0-9]{0,3}");
            case ESCORT_NPC -> var10000 = !objective.target().isBlank();
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         boolean exists = var10000;
         if (!exists) {
            String var10001 = definition.id();
            errors.add("Quest " + var10001 + " objective " + objective.id() + " references unknown target " + objective.target());
         }
      }

   }

   private void validatePrerequisites(Map<String, QuestDefinition> loaded, List<String> errors) {
      for(QuestDefinition definition : loaded.values()) {
         for(String prerequisite : definition.prerequisites()) {
            if (!loaded.containsKey(prerequisite)) {
               String var10001 = definition.id();
               errors.add("Quest " + var10001 + " references unknown prerequisite " + prerequisite);
            }
         }
      }

      Set<String> visiting = new HashSet();
      Set<String> visited = new HashSet();

      for(String id : loaded.keySet()) {
         this.detectCycle(id, loaded, visiting, visited, errors);
      }

   }

   private void detectCycle(String id, Map<String, QuestDefinition> loaded, Set<String> visiting, Set<String> visited, List<String> errors) {
      if (!visited.contains(id) && loaded.containsKey(id)) {
         if (!visiting.add(id)) {
            errors.add("Quest prerequisite cycle detected at " + id);
         } else {
            for(String prerequisite : ((QuestDefinition)loaded.get(id)).prerequisites()) {
               this.detectCycle(prerequisite, loaded, visiting, visited, errors);
            }

            visiting.remove(id);
            visited.add(id);
         }
      }
   }

   public Optional<QuestDefinition> find(String id) {
      return Optional.ofNullable((QuestDefinition)this.quests.get(id));
   }

   public Collection<QuestDefinition> all() {
      return this.quests.values();
   }

   public int size() {
      return this.quests.size();
   }

   public void replaceWith(QuestRegistry source) {
      this.quests = source.quests;
   }
}
