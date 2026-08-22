package com.xuzhihuanjing.rpgcore.domain.stats;

import com.xuzhihuanjing.rpgcore.equipment.EquipmentStatType;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum PrimarySkill {
   STRENGTH("strength", "力量", "<green>", "大地", "LIME_DYE", EquipmentStatType.STRENGTH),
   DEXTERITY("dexterity", "靈巧", "<yellow>", "雷電", "YELLOW_DYE", EquipmentStatType.DEXTERITY),
   INTELLIGENCE("intelligence", "智力", "<aqua>", "流水", "CYAN_DYE", EquipmentStatType.INTELLIGENCE),
   DEFENCE("defence", "護甲", "<red>", "烈焰", "RED_DYE", EquipmentStatType.DEFENCE),
   AGILITY("agility", "敏捷", "<white>", "疾風", "WHITE_DYE", EquipmentStatType.AGILITY);

   private final String id;
   private final String displayName;
   private final String colorTag;
   private final String elementName;
   private final String iconMaterial;
   private final EquipmentStatType equipmentStatType;

   private PrimarySkill(String id, String displayName, String colorTag, String elementName, String iconMaterial, EquipmentStatType equipmentStatType) {
      this.id = id;
      this.displayName = displayName;
      this.colorTag = colorTag;
      this.elementName = elementName;
      this.iconMaterial = iconMaterial;
      this.equipmentStatType = equipmentStatType;
   }

   public String id() {
      return this.id;
   }

   public String displayName() {
      return this.displayName;
   }

   public String colorTag() {
      return this.colorTag;
   }

   public String elementName() {
      return this.elementName;
   }

   public String iconMaterial() {
      return this.iconMaterial;
   }

   public EquipmentStatType equipmentStatType() {
      return this.equipmentStatType;
   }

   public String coloredName() {
      String var10000 = this.colorTag;
      return var10000 + this.displayName + "</" + this.colorTag.substring(1);
   }

   public static Optional<PrimarySkill> parse(String value) {
      if (value == null) {
         return Optional.empty();
      } else {
         String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_');
         return Arrays.stream(values()).filter((skill) -> skill.id.equals(normalized) || skill.name().equalsIgnoreCase(normalized)).findFirst();
      }
   }
}
