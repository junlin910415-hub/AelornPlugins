package tw.linsy.aelorn.rpgcore.equipment;

import tw.linsy.aelorn.rpgcore.domain.stats.PrimarySkill;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record EquipmentTemplate(String id, String displayName, String material, String customItem, EquipmentSlotType slotType, int minimumLevel, int maximumLevel, Set<String> classRequirements, Map<PrimarySkill, Integer> skillRequirements, Set<String> questRequirements, MajorIdentification majorIdentification, Map<EquipmentStatType, EquipmentStatRange> baseStats, List<EquipmentStatRange> affixes, Map<EquipmentRarity, Double> rarityWeights, int minimumAffixes, int maximumAffixes) {
   public EquipmentTemplate(String id, String displayName, String material, String customItem, EquipmentSlotType slotType, int minimumLevel, int maximumLevel, Set<String> classRequirements, Map<PrimarySkill, Integer> skillRequirements, Set<String> questRequirements, MajorIdentification majorIdentification, Map<EquipmentStatType, EquipmentStatRange> baseStats, List<EquipmentStatRange> affixes, Map<EquipmentRarity, Double> rarityWeights, int minimumAffixes, int maximumAffixes) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(displayName, "displayName");
      Objects.requireNonNull(material, "material");
      Objects.requireNonNull(customItem, "customItem");
      Objects.requireNonNull(slotType, "slotType");
      classRequirements = Set.copyOf(classRequirements);
      skillRequirements = Map.copyOf(skillRequirements);
      questRequirements = Set.copyOf(questRequirements);
      majorIdentification = majorIdentification == null ? MajorIdentification.empty() : majorIdentification;
      baseStats = Map.copyOf(baseStats);
      affixes = List.copyOf(affixes);
      rarityWeights = Map.copyOf(rarityWeights);
      if (minimumLevel >= 1 && maximumLevel >= minimumLevel) {
         if (minimumAffixes >= 0 && maximumAffixes >= minimumAffixes) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
            this.customItem = customItem;
            this.slotType = slotType;
            this.minimumLevel = minimumLevel;
            this.maximumLevel = maximumLevel;
            this.classRequirements = classRequirements;
            this.skillRequirements = skillRequirements;
            this.questRequirements = questRequirements;
            this.majorIdentification = majorIdentification;
            this.baseStats = baseStats;
            this.affixes = affixes;
            this.rarityWeights = rarityWeights;
            this.minimumAffixes = minimumAffixes;
            this.maximumAffixes = maximumAffixes;
         } else {
            throw new IllegalArgumentException("Invalid equipment affix count");
         }
      } else {
         throw new IllegalArgumentException("Invalid equipment level range");
      }
   }

   public EquipmentTemplate(String id, String displayName, String material, String customItem, EquipmentSlotType slotType, int minimumLevel, int maximumLevel, Set<String> classRequirements, Map<EquipmentStatType, EquipmentStatRange> baseStats, List<EquipmentStatRange> affixes, Map<EquipmentRarity, Double> rarityWeights, int minimumAffixes, int maximumAffixes) {
      this(id, displayName, material, customItem, slotType, minimumLevel, maximumLevel, classRequirements, Map.of(), Set.of(), MajorIdentification.empty(), baseStats, affixes, rarityWeights, minimumAffixes, maximumAffixes);
   }
}
