package com.xuzhihuanjing.rpgcore.config;

import com.xuzhihuanjing.rpgcore.domain.ability.AbilityTreeEffectType;
import com.xuzhihuanjing.rpgcore.domain.ability.AbilityTreeNodeDefinition;
import com.xuzhihuanjing.rpgcore.domain.classes.CharacterClassDefinition;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AbilityTreeRegistry {
   private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]+$");
   private volatile Map<String, AbilityTreeNodeDefinition> nodes = Map.of();
   private volatile Map<String, List<AbilityTreeNodeDefinition>> byClass = Map.of();

   public void load(File file, ClassRegistry classRegistry) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      if (yaml.getInt("schema-version", -1) != 3) {
         throw new IllegalArgumentException("Unsupported ability-tree.yml schema-version");
      } else {
         ConfigurationSection root = yaml.getConfigurationSection("nodes");
         if (root != null && !root.getKeys(false).isEmpty()) {
            Map<String, AbilityTreeNodeDefinition> loaded = new LinkedHashMap();
            Map<String, Set<Integer>> occupiedSlots = new HashMap();
            List<String> errors = new ArrayList();

            for(String id : root.getKeys(false)) {
               ConfigurationSection section = root.getConfigurationSection(id);
               if (section == null) {
                  errors.add("Invalid ability tree section: " + id);
               } else {
                  AbilityTreeNodeDefinition node = this.readNode(id, section, errors);
                  if (node != null) {
                     this.validateNode(node, classRegistry, errors);
                     if (loaded.putIfAbsent(id, node) != null) {
                        errors.add("Duplicate ability tree node id: " + id);
                     }

                     if (!((Set)occupiedSlots.computeIfAbsent(node.classId(), (ignored) -> new HashSet())).add(node.inventorySlot())) {
                        String var10001 = node.classId();
                        errors.add("Duplicate ability tree slot for class " + var10001 + ": " + node.inventorySlot());
                     }
                  }
               }
            }

            this.validateClassCoverage(loaded, classRegistry, errors);
            this.validatePrerequisitesAndCycles(loaded, errors);
            if (!errors.isEmpty()) {
               throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
            } else {
               Map<String, List<AbilityTreeNodeDefinition>> grouped = new LinkedHashMap();
               loaded.values().forEach((nodex) -> ((List)grouped.computeIfAbsent(nodex.classId(), (ignored) -> new ArrayList())).add(nodex));
               grouped.replaceAll((classId, definitions) -> List.copyOf(definitions));
               this.nodes = Collections.unmodifiableMap(loaded);
               this.byClass = Collections.unmodifiableMap(grouped);
            }
         } else {
            throw new IllegalArgumentException("ability-tree.yml does not define any nodes");
         }
      }
   }

   private void validateClassCoverage(Map<String, AbilityTreeNodeDefinition> loaded, ClassRegistry classRegistry, List<String> errors) {
      for(CharacterClassDefinition characterClass : classRegistry.all()) {
         List<AbilityTreeNodeDefinition> classNodes = loaded.values().stream().filter((node) -> node.classId().equals(characterClass.id())).toList();
         if (classNodes.size() != 6) {
            errors.add("Phase 2 requires exactly six ability tree nodes for class " + characterClass.id());
         }

         characterClass.archetypes().forEach((archetype) -> {
            long count = classNodes.stream().filter((node) -> node.archetypeId().equals(archetype.id())).count();
            if (count != 2L) {
               String var10001 = characterClass.id();
               errors.add("Phase 2 requires exactly two nodes for archetype " + var10001 + "." + archetype.id());
            }

         });
      }

   }

   private AbilityTreeNodeDefinition readNode(String id, ConfigurationSection section, List<String> errors) {
      AbilityTreeEffectType effect;
      try {
         effect = AbilityTreeEffectType.valueOf(section.getString("effect", "").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var6) {
         errors.add("Ability tree node " + id + " has invalid effect type");
         return null;
      }

      return new AbilityTreeNodeDefinition(id, section.getString("class", ""), section.getString("archetype", ""), section.getString("display-name", id), section.getString("icon", "STONE").toUpperCase(Locale.ROOT), section.getInt("slot", -1), section.getInt("cost", 1), section.getInt("minimum-level", 1), section.getStringList("requires"), effect, section.getDouble("value"), section.getString("description", ""));
   }

   private void validateNode(AbilityTreeNodeDefinition node, ClassRegistry classRegistry, List<String> errors) {
      if (!ID_PATTERN.matcher(node.id()).matches()) {
         errors.add("Invalid ability tree node id: " + node.id());
      }

      CharacterClassDefinition characterClass = (CharacterClassDefinition)classRegistry.find(node.classId()).orElse(null);
      if (characterClass == null) {
         String var10001 = node.id();
         errors.add("Ability tree node " + var10001 + " references unknown class " + node.classId());
      } else if (characterClass.archetypes().stream().noneMatch((archetype) -> archetype.id().equals(node.archetypeId()))) {
         String var5 = node.id();
         errors.add("Ability tree node " + var5 + " references unknown archetype " + node.archetypeId());
      }

      if (Material.matchMaterial(node.iconMaterial()) == null) {
         String var6 = node.id();
         errors.add("Ability tree node " + var6 + " has unknown icon " + node.iconMaterial());
      }

      if (node.inventorySlot() < 9 || node.inventorySlot() >= 45) {
         errors.add("Ability tree node " + node.id() + " must use a slot between 9 and 44");
      }

      if (node.cost() < 1 || node.minimumLevel() < 1 || node.value() <= (double)0.0F || node.value() > (double)0.5F) {
         errors.add("Ability tree node " + node.id() + " has invalid progression values");
      }

      if (node.displayName().isBlank() || node.description().isBlank()) {
         errors.add("Ability tree node " + node.id() + " has incomplete presentation data");
      }

   }

   private void validatePrerequisitesAndCycles(Map<String, AbilityTreeNodeDefinition> loaded, List<String> errors) {
      for(AbilityTreeNodeDefinition node : loaded.values()) {
         for(String prerequisiteId : node.prerequisites()) {
            AbilityTreeNodeDefinition prerequisite = (AbilityTreeNodeDefinition)loaded.get(prerequisiteId);
            if (prerequisite == null) {
               String var10001 = node.id();
               errors.add("Ability tree node " + var10001 + " requires missing node " + prerequisiteId);
            } else if (!prerequisite.classId().equals(node.classId())) {
               errors.add("Ability tree prerequisite crosses class boundary: " + node.id());
            }
         }
      }

      Set<String> visiting = new HashSet();
      Set<String> visited = new HashSet();

      for(String nodeId : loaded.keySet()) {
         if (this.hasCycle(nodeId, loaded, visiting, visited)) {
            errors.add("Ability tree contains a prerequisite cycle at " + nodeId);
            break;
         }
      }

   }

   private boolean hasCycle(String nodeId, Map<String, AbilityTreeNodeDefinition> loaded, Set<String> visiting, Set<String> visited) {
      if (visited.contains(nodeId)) {
         return false;
      } else if (!visiting.add(nodeId)) {
         return true;
      } else {
         AbilityTreeNodeDefinition node = (AbilityTreeNodeDefinition)loaded.get(nodeId);
         if (node != null) {
            for(String prerequisite : node.prerequisites()) {
               if (this.hasCycle(prerequisite, loaded, visiting, visited)) {
                  return true;
               }
            }
         }

         visiting.remove(nodeId);
         visited.add(nodeId);
         return false;
      }
   }

   public Optional<AbilityTreeNodeDefinition> find(String id) {
      return Optional.ofNullable((AbilityTreeNodeDefinition)this.nodes.get(id));
   }

   public Collection<AbilityTreeNodeDefinition> nodesFor(String classId) {
      return (Collection)this.byClass.getOrDefault(classId, List.of());
   }

   public Optional<AbilityTreeNodeDefinition> nodeAt(String classId, int inventorySlot) {
      return this.nodesFor(classId).stream().filter((node) -> node.inventorySlot() == inventorySlot).findFirst();
   }

   public int size() {
      return this.nodes.size();
   }

   public void replaceWith(AbilityTreeRegistry source) {
      this.nodes = source.nodes;
      this.byClass = source.byClass;
   }
}
