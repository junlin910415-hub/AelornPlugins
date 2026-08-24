package tw.linsy.aelorn.rpgcore.config;

import tw.linsy.aelorn.rpgcore.domain.ability.AbilityDefinition;
import tw.linsy.aelorn.rpgcore.domain.ability.AbilityDefinitionValidator;
import tw.linsy.aelorn.rpgcore.domain.ability.AbilityEffectType;
import tw.linsy.aelorn.rpgcore.domain.ability.InputToken;
import tw.linsy.aelorn.rpgcore.domain.classes.CharacterClassDefinition;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AbilityRegistry {
   private volatile Map<String, AbilityDefinition> abilities = Map.of();
   private volatile Map<String, Map<List<InputToken>, AbilityDefinition>> byClassAndInput = Map.of();

   public void load(File file, ClassRegistry classRegistry) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      if (yaml.getInt("schema-version", -1) != 2) {
         throw new IllegalArgumentException("Unsupported abilities.yml schema-version");
      } else {
         ConfigurationSection root = yaml.getConfigurationSection("abilities");
         if (root != null && !root.getKeys(false).isEmpty()) {
            Map<String, AbilityDefinition> loaded = new LinkedHashMap();
            Map<String, Map<List<InputToken>, AbilityDefinition>> indexed = new LinkedHashMap();
            List<String> errors = new ArrayList();

            for(String id : root.getKeys(false)) {
               ConfigurationSection section = root.getConfigurationSection(id);
               if (section == null) {
                  errors.add("Invalid ability section: " + id);
               } else {
                  AbilityDefinition definition = this.readDefinition(id, section, errors);
                  if (definition != null) {
                     errors.addAll(AbilityDefinitionValidator.validate(definition));
                     if (classRegistry.find(definition.classId()).isEmpty()) {
                        errors.add("Ability " + id + " references unknown class " + definition.classId());
                     }

                     if (loaded.putIfAbsent(id, definition) != null) {
                        errors.add("Duplicate ability id: " + id);
                     }

                     AbilityDefinition previous = (AbilityDefinition)((Map)indexed.computeIfAbsent(definition.classId(), (ignored) -> new LinkedHashMap())).putIfAbsent(definition.input(), definition);
                     if (previous != null) {
                        String var10001 = definition.classId();
                        errors.add("Duplicate input for class " + var10001 + ": " + String.valueOf(definition.input()));
                     }
                  }
               }
            }

            this.validateClassCoverage(indexed, classRegistry, errors);
            if (!errors.isEmpty()) {
               throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
            } else {
               Map<String, Map<List<InputToken>, AbilityDefinition>> immutableIndex = new LinkedHashMap();
               indexed.forEach((classId, definitions) -> immutableIndex.put(classId, Collections.unmodifiableMap(new LinkedHashMap(definitions))));
               this.abilities = Collections.unmodifiableMap(loaded);
               this.byClassAndInput = Collections.unmodifiableMap(immutableIndex);
            }
         } else {
            throw new IllegalArgumentException("abilities.yml does not define any abilities");
         }
      }
   }

   private AbilityDefinition readDefinition(String id, ConfigurationSection section, List<String> errors) {
      List<InputToken> input = new ArrayList();

      for(String token : section.getStringList("input")) {
         try {
            input.add(InputToken.valueOf(token.toUpperCase(Locale.ROOT)));
         } catch (IllegalArgumentException var9) {
            errors.add("Ability " + id + " has invalid input token: " + token);
         }
      }

      AbilityEffectType effectType;
      try {
         effectType = AbilityEffectType.valueOf(section.getString("effect", "").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var8) {
         errors.add("Ability " + id + " has invalid effect type");
         return null;
      }

      return new AbilityDefinition(id, section.getString("class", ""), section.getString("display-name", id), input, section.getDouble("mana"), section.getDouble("cooldown-seconds"), effectType, section.getDouble("coefficient"), section.getDouble("flat-power"), section.getDouble("radius"), section.getDouble("range"), section.getInt("duration-ticks"), section.getString("description", ""));
   }

   private void validateClassCoverage(Map<String, Map<List<InputToken>, AbilityDefinition>> indexed, ClassRegistry classRegistry, List<String> errors) {
      Set<AbilityEffectType> configuredTypes = EnumSet.noneOf(AbilityEffectType.class);

      for(CharacterClassDefinition characterClass : classRegistry.all()) {
         Collection<AbilityDefinition> classAbilities = this.abilitiesOf(indexed, characterClass.id());
         classAbilities.forEach((ability) -> configuredTypes.add(ability.effectType()));
         if (classAbilities.size() != 4) {
            errors.add("Phase 2 requires exactly four abilities for class " + characterClass.id());
         }
      }

      Set<AbilityEffectType> missing = new HashSet(EnumSet.allOf(AbilityEffectType.class));
      missing.removeAll(configuredTypes);
      if (!missing.isEmpty()) {
         errors.add("Phase 2 is missing ability effect types: " + String.valueOf(missing));
      }

   }

   private Collection<AbilityDefinition> abilitiesOf(Map<String, Map<List<InputToken>, AbilityDefinition>> index, String classId) {
      return ((Map)index.getOrDefault(classId, Map.of())).values();
   }

   public Optional<AbilityDefinition> findByInput(String classId, List<InputToken> input) {
      return Optional.ofNullable((AbilityDefinition)((Map)this.byClassAndInput.getOrDefault(classId, Map.of())).get(input));
   }

   public boolean hasPrefix(String classId, List<InputToken> input) {
      return this.byClassAndInput.getOrDefault(classId, Map.<List<InputToken>, AbilityDefinition>of()).keySet().stream().anyMatch((sequence) -> sequence.size() >= input.size() && sequence.subList(0, input.size()).equals(input));
   }

   public Collection<AbilityDefinition> abilitiesFor(String classId) {
      return List.copyOf(((Map)this.byClassAndInput.getOrDefault(classId, Map.of())).values());
   }

   public int size() {
      return this.abilities.size();
   }

   public void replaceWith(AbilityRegistry source) {
      this.abilities = source.abilities;
      this.byClassAndInput = source.byClassAndInput;
   }
}
