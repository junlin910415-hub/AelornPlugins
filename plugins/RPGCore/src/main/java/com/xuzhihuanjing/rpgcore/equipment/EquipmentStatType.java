package com.xuzhihuanjing.rpgcore.equipment;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum EquipmentStatType {
   ATTACK("attack", "攻擊傷害", "", new String[]{"ATTACK_DAMAGE", "WEAPON_DAMAGE"}),
   MAIN_ATTACK_DAMAGE("main_attack_damage", "主攻擊傷害", "", new String[0]),
   MAIN_ATTACK_DAMAGE_PERCENT("main_attack_damage_percent", "主攻擊傷害", "%", new String[0]),
   SPELL_DAMAGE("spell_damage", "法術傷害", "", new String[0]),
   SPELL_DAMAGE_PERCENT("spell_damage_percent", "法術傷害", "%", new String[0]),
   MAGIC_DAMAGE("magic_damage", "魔法傷害", "", new String[0]),
   SKILL_DAMAGE("skill_damage", "技能傷害", "", new String[0]),
   ABILITY_DAMAGE("ability_damage", "能力傷害", "", new String[0]),
   ALL_DAMAGE("all_damage", "全傷害", "%", new String[0]),
   PVE_DAMAGE("pve_damage", "PvE 傷害", "%", new String[0]),
   PVP_DAMAGE("pvp_damage", "PvP 傷害", "%", new String[0]),
   PROJECTILE_DAMAGE("projectile_damage", "投射物傷害", "%", new String[0]),
   PHYSICAL_DAMAGE("physical_damage", "物理傷害", "%", new String[0]),
   ATTACK_SPEED("attack_speed", "攻擊速度", "%", new String[0]),
   ATTACK_SPEED_TIER("attack_speed_tier", "攻擊速度階級", " 階", new String[0]),
   RANGE("range", "主攻擊距離", " 格", new String[0]),
   PROJECTILE_VELOCITY("projectile_velocity", "投射速度", "", new String[]{"ARROW_VELOCITY"}),
   CRITICAL_CHANCE("critical_strike_chance", "暴擊機率", "%", new String[0]),
   CRITICAL_POWER("critical_strike_power", "暴擊威力", "%", new String[0]),
   ARMOR_PENETRATION("armor_penetration", "護甲穿透", "", new String[0]),
   ARMOR_PENETRATION_PERCENT("armor_penetration_percent", "護甲穿透", "%", new String[0]),
   MAGIC_PENETRATION("magic_penetration", "魔法穿透", "", new String[0]),
   MAGIC_PENETRATION_PERCENT("magic_penetration_percent", "魔法穿透", "%", new String[0]),
   ELEMENTAL_PENETRATION("elemental_penetration", "元素穿透", "", new String[0]),
   ELEMENTAL_PENETRATION_PERCENT("elemental_penetration_percent", "元素穿透", "%", new String[0]),
   EXPLODING("exploding", "爆裂機率", "%", new String[0]),
   POISON("poison", "中毒傷害", "/3秒", new String[0]),
   SLOW_ENEMY("slow_enemy", "緩速強度", "%", new String[0]),
   WEAKEN_ENEMY("weaken_enemy", "弱化強度", "%", new String[0]),
   KNOCKBACK("knockback", "擊退強度", "%", new String[]{"KNOCKBACK_POWER"}),
   HEALTH("health", "生命上限", "", new String[]{"MAX_HEALTH", "HEALTH"}),
   HEALTH_PERCENT("max_health_percent", "生命上限", "%", new String[0]),
   HEALTH_REGEN("health_regen", "生命回復", "/5秒", new String[]{"HEALTH_REGENERATION", "HEALTH_REGEN"}),
   HEALTH_REGEN_PERCENT("health_regeneration_percent", "生命回復", "%", new String[0]),
   DEFENSE("defense", "防禦", "", new String[0]),
   ARMOR("armor", "護甲值", "", new String[0]),
   ARMOR_TOUGHNESS("armor_toughness", "護甲韌性", "", new String[0]),
   RESISTANCE("resistance", "抗性", "", new String[]{"RESISTANCE", "MAGIC_RESISTANCE"}),
   ELEMENTAL_RESISTANCE("elemental_resistance", "元素抗性", "", new String[0]),
   ELEMENTAL_RESISTANCE_PERCENT("elemental_resistance_percent", "元素抗性", "%", new String[0]),
   DAMAGE_REDUCTION("damage_reduction", "傷害減免", "%", new String[0]),
   PHYSICAL_DAMAGE_REDUCTION("physical_damage_reduction", "物理減傷", "%", new String[0]),
   MAGIC_DAMAGE_REDUCTION("magic_damage_reduction", "魔法減傷", "%", new String[0]),
   PROJECTILE_DAMAGE_REDUCTION("projectile_damage_reduction", "投射物減傷", "%", new String[0]),
   PVE_DAMAGE_REDUCTION("pve_damage_reduction", "PvE 減傷", "%", new String[0]),
   PVP_DAMAGE_REDUCTION("pvp_damage_reduction", "PvP 減傷", "%", new String[0]),
   KNOCKBACK_RESISTANCE("knockback_resistance", "擊退抗性", "%", new String[0]),
   BLOCK_POWER("block_power", "格擋強度", "%", new String[0]),
   BLOCK_RATING("block_rating", "格擋評級", "", new String[0]),
   DODGE_RATING("dodge_rating", "閃避評級", "", new String[0]),
   PARRY_RATING("parry_rating", "招架評級", "", new String[0]),
   THORNS("thorns", "荊棘反傷", "%", new String[0]),
   REFLECTION("reflection", "投射反射", "%", new String[0]),
   MANA("mana", "魔力上限", "", new String[]{"MAX_MANA", "MANA"}),
   STAMINA("max_stamina", "耐力上限", "", new String[0]),
   MANA_REGEN("mana_regen", "魔力回復", "/5秒", new String[]{"MANA_REGENERATION", "MANA_REGEN"}),
   STAMINA_REGEN("stamina_regeneration", "耐力回復", "/5秒", new String[0]),
   LIFE_STEAL("life_steal", "生命竊取", "/3秒", new String[0]),
   MANA_STEAL("mana_steal", "魔力竊取", "/3秒", new String[0]),
   SPELL_VAMPIRISM("spell_vampirism", "法術吸血", "%", new String[0]),
   HEALING_EFFICIENCY("healing_efficiency", "治療效率", "%", new String[0]),
   MANA_COST("mana_cost", "魔力消耗", "", new String[0]),
   STAMINA_COST("stamina_cost", "耐力消耗", "", new String[0]),
   COOLDOWN("cooldown", "技能冷卻", " 秒", new String[0]),
   COOLDOWN_REDUCTION("cooldown_reduction", "冷卻縮減", "%", new String[0]),
   SPELL_1_COST("spell_1_cost", "第一技能消耗", "", new String[0]),
   SPELL_2_COST("spell_2_cost", "第二技能消耗", "", new String[0]),
   SPELL_3_COST("spell_3_cost", "第三技能消耗", "", new String[0]),
   SPELL_4_COST("spell_4_cost", "第四技能消耗", "", new String[0]),
   SPELL_1_COST_PERCENT("spell_1_cost_percent", "第一技能消耗", "%", new String[0]),
   SPELL_2_COST_PERCENT("spell_2_cost_percent", "第二技能消耗", "%", new String[0]),
   SPELL_3_COST_PERCENT("spell_3_cost_percent", "第三技能消耗", "%", new String[0]),
   SPELL_4_COST_PERCENT("spell_4_cost_percent", "第四技能消耗", "%", new String[0]),
   SPEED("speed", "移動速度", "%", new String[]{"MOVEMENT_SPEED", "SPEED"}),
   SPRINT("sprint", "衝刺耐力", "%", new String[0]),
   SPRINT_REGEN("sprint_regen", "衝刺回復", "%", new String[0]),
   JUMP_HEIGHT("jump_height", "跳躍高度", "", new String[0]),
   XP_BONUS("xp_bonus", "戰鬥經驗", "%", new String[0]),
   LOOT_BONUS("loot_bonus", "戰利品數量", "%", new String[0]),
   LOOT_QUALITY("loot_quality", "戰利品品質", "%", new String[0]),
   STEALING("stealing", "掠奪機率", "%", new String[0]),
   GATHER_XP_BONUS("gather_xp_bonus", "採集經驗", "%", new String[0]),
   GATHER_SPEED("gather_speed", "採集速度", "%", new String[0]),
   STRENGTH("strength", "力量", "", new String[0]),
   DEXTERITY("dexterity", "靈巧", "", new String[0]),
   INTELLIGENCE("intelligence", "智力", "", new String[0]),
   DEFENCE("defence", "護甲能力", "", new String[]{"DEFENCE"}),
   AGILITY("agility", "敏捷", "", new String[0]),
   EARTH_DAMAGE("earth_damage", "大地傷害", "", new String[0]),
   EARTH_DAMAGE_PERCENT("earth_damage_percent", "大地傷害", "%", new String[0]),
   EARTH_RESISTANCE("earth_resistance", "大地抗性", "", new String[0]),
   THUNDER_DAMAGE("thunder_damage", "雷霆傷害", "", new String[0]),
   THUNDER_DAMAGE_PERCENT("thunder_damage_percent", "雷霆傷害", "%", new String[0]),
   THUNDER_RESISTANCE("thunder_resistance", "雷霆抗性", "", new String[0]),
   WATER_DAMAGE("water_damage", "流水傷害", "", new String[0]),
   WATER_DAMAGE_PERCENT("water_damage_percent", "流水傷害", "%", new String[0]),
   WATER_RESISTANCE("water_resistance", "流水抗性", "", new String[0]),
   FIRE_DAMAGE("fire_damage", "烈焰傷害", "", new String[0]),
   FIRE_DAMAGE_PERCENT("fire_damage_percent", "烈焰傷害", "%", new String[0]),
   FIRE_RESISTANCE("fire_resistance", "烈焰抗性", "", new String[0]),
   WIND_DAMAGE("wind_damage", "疾風傷害", "", new String[0]),
   WIND_DAMAGE_PERCENT("wind_damage_percent", "疾風傷害", "%", new String[0]),
   WIND_RESISTANCE("wind_resistance", "疾風抗性", "", new String[0]);

   private static final Map<String, String> ALIASES = Map.ofEntries(Map.entry("melee_damage", "main_attack_damage_percent"), Map.entry("raw_melee_damage", "main_attack_damage"), Map.entry("raw_main_attack_damage", "main_attack_damage"), Map.entry("raw_spell_damage", "spell_damage"), Map.entry("walk_speed", "speed"), Map.entry("movement_speed", "speed"), Map.entry("max_health", "health"), Map.entry("max_mana", "mana"), Map.entry("mana_regeneration", "mana_regen"), Map.entry("health_regeneration", "health_regen"), Map.entry("arrow_velocity", "projectile_velocity"), Map.entry("lightning_damage", "thunder_damage"), Map.entry("air_damage", "wind_damage"));
   private final String id;
   private final String displayName;
   private final String suffix;
   private final List<String> externalKeys;

   private EquipmentStatType(String id, String displayName, String suffix, String... externalKeys) {
      this.id = id;
      this.displayName = displayName;
      this.suffix = suffix;
      this.externalKeys = externalKeys.length == 0 ? List.of(id.toUpperCase(Locale.ROOT)) : List.copyOf(Arrays.asList(externalKeys));
   }

   public String id() {
      return this.id;
   }

   public String displayName() {
      return this.displayName;
   }

   public String suffix() {
      return this.suffix;
   }

   public List<String> externalKeys() {
      return this.externalKeys;
   }

   public static Optional<EquipmentStatType> parse(String value) {
      if (value == null) {
         return Optional.empty();
      } else {
         String raw = value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
         String normalized = ALIASES.getOrDefault(raw, raw);
         return Arrays.stream(values()).filter((type) -> type.id.equals(normalized)).findFirst();
      }
   }
}
