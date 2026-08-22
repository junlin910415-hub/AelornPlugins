package com.xuzhihuanjing.rpgcore.config;

import com.xuzhihuanjing.rpgcore.domain.encounter.EncounterDefinition;
import com.xuzhihuanjing.rpgcore.domain.encounter.EncounterDefinitionValidator;
import com.xuzhihuanjing.rpgcore.domain.encounter.EncounterSpawnDefinition;
import com.xuzhihuanjing.rpgcore.domain.encounter.EncounterWaveDefinition;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class EncounterRegistry {
   private volatile Map<String, EncounterDefinition> encounters = Map.of();

   public void load(File file, MonsterRegistry monsters) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      if (yaml.getInt("schema-version", -1) != 1) {
         throw new IllegalArgumentException("Unsupported encounters.yml schema-version");
      } else {
         ConfigurationSection root = yaml.getConfigurationSection("encounters");
         if (root != null && !root.getKeys(false).isEmpty()) {
            Map<String, EncounterDefinition> loaded = new LinkedHashMap();
            List<String> errors = new ArrayList();

            for(String id : root.getKeys(false)) {
               ConfigurationSection section = root.getConfigurationSection(id);
               if (section == null) {
                  errors.add("Invalid encounter section: " + id);
               } else {
                  EncounterDefinition definition = this.readDefinition(id, section, errors);
                  errors.addAll(EncounterDefinitionValidator.validate(definition));
                  this.validateMonsterReferences(definition, monsters, errors);
                  if (loaded.putIfAbsent(id, definition) != null) {
                     errors.add("Duplicate encounter id: " + id);
                  }
               }
            }

            if (!errors.isEmpty()) {
               throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
            } else {
               this.encounters = Collections.unmodifiableMap(loaded);
            }
         } else {
            throw new IllegalArgumentException("encounters.yml does not define any encounters");
         }
      }
   }

   private EncounterDefinition readDefinition(String id, ConfigurationSection section, List<String> errors) {
      List<EncounterWaveDefinition> waves = new ArrayList();
      ConfigurationSection waveRoot = section.getConfigurationSection("waves");
      if (waveRoot != null) {
         for(String waveId : waveRoot.getKeys(false)) {
            ConfigurationSection wave = waveRoot.getConfigurationSection(waveId);
            if (wave == null) {
               errors.add("Encounter " + id + " has invalid wave " + waveId);
            } else {
               List<EncounterSpawnDefinition> spawns = new ArrayList();
               ConfigurationSection spawnRoot = wave.getConfigurationSection("spawns");
               if (spawnRoot != null) {
                  for(String spawnId : spawnRoot.getKeys(false)) {
                     ConfigurationSection spawn = spawnRoot.getConfigurationSection(spawnId);
                     if (spawn == null) {
                        errors.add("Encounter " + id + " wave " + waveId + " has invalid spawn " + spawnId);
                     } else {
                        spawns.add(new EncounterSpawnDefinition(spawn.getString("monster", ""), spawn.getInt("amount", 1), spawn.getInt("level-offset", 0)));
                     }
                  }
               }

               waves.add(new EncounterWaveDefinition(waveId, wave.getString("display-name", waveId), spawns));
            }
         }
      }

      return new EncounterDefinition(id, section.getString("display-name", id), section.getInt("levels.minimum", 1), section.getInt("levels.maximum", 1), section.getDouble("arena-radius", (double)8.0F), section.getLong("wave-delay-ticks", 60L), section.getLong("timeout-ticks", 3600L), section.getLong("cooldown-seconds", 180L), section.getLong("completion-experience", 50L), waves);
   }

   private void validateMonsterReferences(EncounterDefinition definition, MonsterRegistry monsters, List<String> errors) {
      for(EncounterWaveDefinition wave : definition.waves()) {
         for(EncounterSpawnDefinition spawn : wave.spawns()) {
            if (monsters.find(spawn.monsterId()).isEmpty()) {
               String var10001 = definition.id();
               errors.add("Encounter " + var10001 + " wave " + wave.id() + " references unknown monster " + spawn.monsterId());
            }
         }
      }

   }

   public Optional<EncounterDefinition> find(String id) {
      return Optional.ofNullable((EncounterDefinition)this.encounters.get(id));
   }

   public Collection<EncounterDefinition> all() {
      return this.encounters.values();
   }

   public int size() {
      return this.encounters.size();
   }

   public void replaceWith(EncounterRegistry source) {
      this.encounters = source.encounters;
   }
}
