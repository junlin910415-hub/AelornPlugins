package tw.linsy.aelorn.rpgcore.config;

import tw.linsy.aelorn.rpgcore.domain.stats.PrimarySkill;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentRarity;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentSlotType;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentStatRange;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentStatType;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentTemplate;
import tw.linsy.aelorn.rpgcore.equipment.MajorIdentification;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class EquipmentRegistry {
   private volatile Map<String, EquipmentTemplate> templates = Map.of();

   public void load(File file, ClassRegistry classRegistry) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      int schemaVersion = yaml.getInt("schema-version", -1);
      if (schemaVersion >= 1 && schemaVersion <= 2) {
         ConfigurationSection root = yaml.getConfigurationSection("templates");
         if (root != null && !root.getKeys(false).isEmpty()) {
            Map<String, EquipmentTemplate> loaded = new LinkedHashMap();
            List<String> errors = new ArrayList();

            for(String id : root.getKeys(false)) {
               ConfigurationSection section = root.getConfigurationSection(id);
               if (section == null) {
                  errors.add("Invalid equipment template section: " + id);
               } else {
                  EquipmentTemplate template = this.readTemplate(id, section, classRegistry, errors);
                  if (template != null) {
                     this.validate(template, errors);
                     if (loaded.putIfAbsent(id, template) != null) {
                        errors.add("Duplicate equipment template id: " + id);
                     }
                  }
               }
            }

            if (!errors.isEmpty()) {
               throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
            } else {
               this.templates = Collections.unmodifiableMap(loaded);
            }
         } else {
            throw new IllegalArgumentException("equipment.yml does not define any templates");
         }
      } else {
         throw new IllegalArgumentException("Unsupported equipment.yml schema-version");
      }
   }

   private EquipmentTemplate readTemplate(String id, ConfigurationSection section, ClassRegistry classRegistry, List<String> errors) {
      EquipmentSlotType slotType;
      try {
         slotType = EquipmentSlotType.valueOf(section.getString("slot", "WEAPON").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var12) {
         errors.add("Equipment " + id + " has an invalid slot");
         return null;
      }

      ConfigurationSection requirements = section.getConfigurationSection("requirements");
      List<String> configuredClasses = requirements == null ? List.of() : requirements.getStringList("classes");
      if (configuredClasses.isEmpty()) {
         configuredClasses = section.getStringList("class-requirements");
      }

      Set<String> classRequirements = new LinkedHashSet();

      for(String classId : configuredClasses) {
         String normalized = classId.toLowerCase(Locale.ROOT);
         if (classRegistry.find(normalized).isEmpty()) {
            errors.add("Equipment " + id + " references unknown class " + classId);
         }

         classRequirements.add(normalized);
      }

      Map<PrimarySkill, Integer> skillRequirements = this.readSkillRequirements(id, requirements, errors);
      Set<String> questRequirements = this.readQuestRequirements(requirements);
      MajorIdentification majorIdentification = this.readMajorIdentification(section);
      String customItem = section.getString("custom-item", "");
      return new EquipmentTemplate(id, section.getString("display-name", id), section.getString("material", "STONE").toUpperCase(Locale.ROOT), customItem, slotType, section.getInt("level.minimum", 1), section.getInt("level.maximum", 1), classRequirements, skillRequirements, questRequirements, majorIdentification, this.readStatRanges(id, "base-stats", section.getConfigurationSection("base-stats"), errors), this.readStatRanges(id, "affixes", section.getConfigurationSection("affixes"), errors).values().stream().toList(), this.readRarityWeights(id, section.getConfigurationSection("rarity-weights"), errors), section.getInt("affix-count.minimum", 0), section.getInt("affix-count.maximum", 0));
   }

   private Map<PrimarySkill, Integer> readSkillRequirements(String templateId, ConfigurationSection requirements, List<String> errors) {
      Map<PrimarySkill, Integer> result = new EnumMap(PrimarySkill.class);
      ConfigurationSection skills = requirements == null ? null : requirements.getConfigurationSection("skills");
      if (skills == null) {
         return result;
      } else {
         for(String skillId : skills.getKeys(false)) {
            PrimarySkill skill = (PrimarySkill)PrimarySkill.parse(skillId).orElse(null);
            if (skill == null) {
               errors.add("Equipment " + templateId + " references unknown requirement skill " + skillId);
            } else {
               int value = skills.getInt(skillId, -1);
               if (value >= 0 && value <= 200) {
                  if (value > 0) {
                     result.put(skill, value);
                  }
               } else {
                  errors.add("Equipment " + templateId + " has invalid " + skillId + " requirement " + value);
               }
            }
         }

         return result;
      }
   }

   private Set<String> readQuestRequirements(ConfigurationSection requirements) {
      if (requirements == null) {
         return Set.of();
      } else {
         Set<String> result = new LinkedHashSet();

         for(String questId : requirements.getStringList("quests")) {
            String normalized = questId.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (!normalized.isBlank()) {
               result.add(normalized);
            }
         }

         return result;
      }
   }

   private MajorIdentification readMajorIdentification(ConfigurationSection section) {
      ConfigurationSection major = section.getConfigurationSection("major-identification");
      return major == null ? MajorIdentification.empty() : new MajorIdentification(major.getString("id", ""), major.getString("display-name", major.getString("name", "")), major.getStringList("description"));
   }

   private Map<EquipmentStatType, EquipmentStatRange> readStatRanges(String templateId, String group, ConfigurationSection section, List<String> errors) {
      Map<EquipmentStatType, EquipmentStatRange> ranges = new EnumMap(EquipmentStatType.class);
      if (section == null) {
         return ranges;
      } else {
         for(String statId : section.getKeys(false)) {
            Optional<EquipmentStatType> type = EquipmentStatType.parse(statId);
            if (type.isEmpty()) {
               errors.add("Equipment " + templateId + " has unknown " + group + " stat " + statId);
            } else {
               ConfigurationSection stat = section.getConfigurationSection(statId);
               if (stat == null) {
                  errors.add("Equipment " + templateId + " has invalid " + group + " stat " + statId);
               } else {
                  ranges.put((EquipmentStatType)type.get(), new EquipmentStatRange((EquipmentStatType)type.get(), stat.getInt("minimum", 0), stat.getInt("maximum", 0)));
               }
            }
         }

         return ranges;
      }
   }

   private Map<EquipmentRarity, Double> readRarityWeights(String templateId, ConfigurationSection section, List<String> errors) {
      Map<EquipmentRarity, Double> weights = defaultRarityWeights();
      if (section == null) {
         return weights;
      } else {
         weights = new EnumMap(EquipmentRarity.class);

         for(String rarityId : section.getKeys(false)) {
            EquipmentRarity rarity = parseRarity(templateId, rarityId, errors);
            if (rarity != null) {
               weights.put(rarity, section.getDouble(rarityId));
            }
         }

         return weights;
      }
   }

   public static EquipmentRarity parseRarity(String owner, String value, List<String> errors) {
      try {
         return EquipmentRarity.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
      } catch (IllegalArgumentException var4) {
         errors.add(owner + " references unknown rarity " + value);
         return null;
      }
   }

   public static Map<EquipmentRarity, Double> defaultRarityWeights() {
      Map<EquipmentRarity, Double> weights = new EnumMap(EquipmentRarity.class);
      weights.put(EquipmentRarity.COMMON, (double)40.0F);
      weights.put(EquipmentRarity.UNCOMMON, (double)26.0F);
      weights.put(EquipmentRarity.RARE, (double)17.0F);
      weights.put(EquipmentRarity.EPIC, (double)9.0F);
      weights.put(EquipmentRarity.LEGENDARY, (double)4.5F);
      weights.put(EquipmentRarity.VAST, (double)2.5F);
      weights.put(EquipmentRarity.MYTHIC, (double)1.0F);
      return weights;
   }

   private void validate(EquipmentTemplate template, List<String> errors) {
      String prefix = "Equipment " + template.id() + " ";
      if (!template.id().matches("[a-z0-9_]+")) {
         errors.add(prefix + "has an invalid id");
      }

      if (template.displayName().isBlank()) {
         errors.add(prefix + "requires a display name");
      }

      if (Material.matchMaterial(template.material()) == null) {
         errors.add(prefix + "has unknown material " + template.material());
      }

      if (template.baseStats().isEmpty()) {
         errors.add(prefix + "requires at least one base stat");
      }

      if (template.maximumAffixes() > template.affixes().size()) {
         errors.add(prefix + "cannot roll more affixes than it defines");
      }

      if (template.majorIdentification().enabled() && template.majorIdentification().displayName().isBlank()) {
         errors.add(prefix + "major identification requires a display name");
      }

      if (template.rarityWeights().isEmpty()) {
         errors.add(prefix + "requires at least one rarity weight");
      }

      for(Map.Entry<EquipmentRarity, Double> entry : template.rarityWeights().entrySet()) {
         if ((Double)entry.getValue() <= (double)0.0F) {
            errors.add(prefix + "has invalid rarity weight for " + ((EquipmentRarity)entry.getKey()).id());
         }
      }

   }

   public Optional<EquipmentTemplate> find(String id) {
      return Optional.ofNullable((EquipmentTemplate)this.templates.get(id));
   }

   public Collection<EquipmentTemplate> all() {
      return this.templates.values();
   }

   public int size() {
      return this.templates.size();
   }

   public void replaceWith(EquipmentRegistry source) {
      this.templates = source.templates;
   }
}
