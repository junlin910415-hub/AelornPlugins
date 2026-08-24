package tw.linsy.aelorn.rpgcore.config;

import tw.linsy.aelorn.rpgcore.discovery.DiscoverySpatialIndex;
import tw.linsy.aelorn.rpgcore.domain.discovery.DiscoveryCategory;
import tw.linsy.aelorn.rpgcore.domain.discovery.DiscoveryDefinition;
import tw.linsy.aelorn.rpgcore.domain.discovery.DiscoveryDefinitionValidator;
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

public final class DiscoveryRegistry {
   private volatile Map<String, DiscoveryDefinition> discoveries = Map.of();
   private volatile DiscoverySpatialIndex spatialIndex = new DiscoverySpatialIndex(List.of());

   public void load(File file) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      if (yaml.getInt("schema-version", -1) != 1) {
         throw new IllegalArgumentException("Unsupported discoveries.yml schema-version");
      } else {
         ConfigurationSection root = yaml.getConfigurationSection("discoveries");
         if (root != null && !root.getKeys(false).isEmpty()) {
            Map<String, DiscoveryDefinition> loaded = new LinkedHashMap();
            List<String> errors = new ArrayList();

            for(String id : root.getKeys(false)) {
               ConfigurationSection section = root.getConfigurationSection(id);
               if (section == null) {
                  errors.add("Invalid discovery section: " + id);
               } else {
                  DiscoveryDefinition definition = this.readDefinition(id, section, errors);
                  if (definition != null) {
                     errors.addAll(DiscoveryDefinitionValidator.validate(definition));
                     if (Material.matchMaterial(definition.iconMaterial()) == null) {
                        errors.add("Discovery " + id + " has unknown icon material " + definition.iconMaterial());
                     }

                     if (loaded.putIfAbsent(id, definition) != null) {
                        errors.add("Duplicate discovery id: " + id);
                     }
                  }
               }
            }

            this.validatePrerequisites(loaded, errors);
            if (!errors.isEmpty()) {
               throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
            } else {
               this.discoveries = Collections.unmodifiableMap(loaded);
               this.spatialIndex = new DiscoverySpatialIndex(this.discoveries.values());
            }
         } else {
            throw new IllegalArgumentException("discoveries.yml does not define any discoveries");
         }
      }
   }

   private DiscoveryDefinition readDefinition(String id, ConfigurationSection section, List<String> errors) {
      DiscoveryCategory category;
      try {
         category = DiscoveryCategory.valueOf(section.getString("category", "LANDMARK").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var6) {
         errors.add("Discovery " + id + " has an invalid category");
         return null;
      }

      ConfigurationSection location = section.getConfigurationSection("location");
      if (location == null) {
         errors.add("Discovery " + id + " has no location");
         return null;
      } else {
         return new DiscoveryDefinition(id, section.getString("display-name", id), section.getString("description", id), category, section.getString("icon", "COMPASS").toUpperCase(Locale.ROOT), section.getInt("minimum-level", 1), location.getString("world", ""), location.getDouble("x"), location.getDouble("y"), location.getDouble("z"), location.getDouble("radius", (double)6.0F), section.getLong("reward-experience", 1L), section.getStringList("prerequisites"), section.getStringList("required-quests"), section.getBoolean("hidden-until-discovered", category == DiscoveryCategory.SECRET));
      }
   }

   private void validatePrerequisites(Map<String, DiscoveryDefinition> loaded, List<String> errors) {
      for(DiscoveryDefinition definition : loaded.values()) {
         for(String prerequisite : definition.prerequisites()) {
            if (!loaded.containsKey(prerequisite)) {
               String var10001 = definition.id();
               errors.add("Discovery " + var10001 + " references unknown prerequisite " + prerequisite);
            }
         }
      }

      Set<String> visiting = new HashSet();
      Set<String> visited = new HashSet();

      for(String id : loaded.keySet()) {
         this.detectCycle(id, loaded, visiting, visited, errors);
      }

   }

   private void detectCycle(String id, Map<String, DiscoveryDefinition> loaded, Set<String> visiting, Set<String> visited, List<String> errors) {
      if (!visited.contains(id) && loaded.containsKey(id)) {
         if (!visiting.add(id)) {
            errors.add("Discovery prerequisite cycle detected at " + id);
         } else {
            for(String prerequisite : ((DiscoveryDefinition)loaded.get(id)).prerequisites()) {
               this.detectCycle(prerequisite, loaded, visiting, visited, errors);
            }

            visiting.remove(id);
            visited.add(id);
         }
      }
   }

   public Optional<DiscoveryDefinition> find(String id) {
      return Optional.ofNullable((DiscoveryDefinition)this.discoveries.get(id));
   }

   public Collection<DiscoveryDefinition> all() {
      return this.discoveries.values();
   }

   public List<DiscoveryDefinition> candidates(String world, double x, double z) {
      return this.spatialIndex.candidates(world, x, z);
   }

   public int size() {
      return this.discoveries.size();
   }

   public void replaceWith(DiscoveryRegistry source) {
      this.discoveries = source.discoveries;
      this.spatialIndex = source.spatialIndex;
   }
}
