package com.xuzhihuanjing.rpgcore.config;

import com.xuzhihuanjing.rpgcore.domain.classes.ArchetypeDefinition;
import com.xuzhihuanjing.rpgcore.domain.classes.CharacterClassDefinition;
import com.xuzhihuanjing.rpgcore.domain.classes.ClassBalance;
import com.xuzhihuanjing.rpgcore.domain.classes.ClassDefinitionValidator;
import com.xuzhihuanjing.rpgcore.domain.classes.ClassRatings;
import com.xuzhihuanjing.rpgcore.domain.stats.BaseStats;
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

public final class ClassRegistry {
   private volatile Map<String, CharacterClassDefinition> classes = Map.of();

   public void load(File file) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      if (yaml.getInt("schema-version", -1) != 4) {
         throw new IllegalArgumentException("Unsupported classes.yml schema-version");
      } else {
         ConfigurationSection root = yaml.getConfigurationSection("classes");
         if (root != null && !root.getKeys(false).isEmpty()) {
            Map<String, CharacterClassDefinition> loaded = new LinkedHashMap();
            List<String> errors = new ArrayList();

            for(String id : root.getKeys(false)) {
               ConfigurationSection section = root.getConfigurationSection(id);
               if (section == null) {
                  errors.add("Class section is invalid: " + id);
               } else {
                  CharacterClassDefinition definition = this.readDefinition(id, section, errors);
                  if (definition != null) {
                     errors.addAll(ClassDefinitionValidator.validate(definition));
                     if (loaded.putIfAbsent(id, definition) != null) {
                        errors.add("Duplicate class id: " + id);
                     }
                  }
               }
            }

            if (loaded.size() != 5) {
               errors.add("Phase 1 requires exactly five class definitions, found " + loaded.size());
            }

            if (!errors.isEmpty()) {
               throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
            } else {
               this.classes = Collections.unmodifiableMap(loaded);
            }
         } else {
            throw new IllegalArgumentException("classes.yml does not define any classes");
         }
      }
   }

   private CharacterClassDefinition readDefinition(String id, ConfigurationSection section, List<String> errors) {
      String iconName = section.getString("icon", "STONE").toUpperCase(Locale.ROOT);
      String castingMaterial = section.getString("casting-material", iconName).toUpperCase(Locale.ROOT);
      if (Material.matchMaterial(iconName) == null) {
         errors.add("Class " + id + " has unknown icon material: " + iconName);
      }

      if (Material.matchMaterial(castingMaterial) == null) {
         errors.add("Class " + id + " has unknown casting material: " + castingMaterial);
      }

      ConfigurationSection stats = section.getConfigurationSection("base-stats");
      ConfigurationSection balance = section.getConfigurationSection("balance");
      ConfigurationSection ratings = section.getConfigurationSection("ratings");
      ConfigurationSection archetypeSection = section.getConfigurationSection("archetypes");
      if (stats != null && balance != null && ratings != null && archetypeSection != null) {
         List<ArchetypeDefinition> archetypes = new ArrayList();

         for(String archetypeId : archetypeSection.getKeys(false)) {
            ConfigurationSection archetype = archetypeSection.getConfigurationSection(archetypeId);
            if (archetype == null) {
               errors.add("Invalid archetype section: " + id + "." + archetypeId);
            } else {
               archetypes.add(new ArchetypeDefinition(archetypeId, archetype.getString("display-name", archetypeId), archetype.getString("description", "")));
            }
         }

         return new CharacterClassDefinition(id, section.getString("display-name", id), iconName, castingMaterial, section.getString("weapon", ""), section.getString("role", ""), section.getStringList("description"), new BaseStats(stats.getDouble("health"), stats.getDouble("mana"), stats.getDouble("attack"), stats.getDouble("defense"), stats.getDouble("resistance"), stats.getDouble("speed")), new ClassBalance(balance.getDouble("damage-taken-multiplier"), balance.getDouble("basic-attack-multiplier"), new ClassRatings(ratings.getInt("difficulty"), ratings.getInt("damage"), ratings.getInt("defense"), ratings.getInt("range"), ratings.getInt("mobility"), ratings.getInt("support"))), archetypes);
      } else {
         errors.add("Class " + id + " is missing base-stats, balance, ratings, or archetypes");
         return null;
      }
   }

   public Optional<CharacterClassDefinition> find(String id) {
      return Optional.ofNullable((CharacterClassDefinition)this.classes.get(id));
   }

   public Collection<CharacterClassDefinition> all() {
      return this.classes.values();
   }

   public int size() {
      return this.classes.size();
   }

   public void replaceWith(ClassRegistry source) {
      this.classes = source.classes;
   }
}
