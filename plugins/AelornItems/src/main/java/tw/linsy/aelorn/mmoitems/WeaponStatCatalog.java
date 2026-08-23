package tw.linsy.aelorn.mmoitems;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tw.linsy.aelorn.mmoitems.forge.StatCatalogLoader;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;

final class WeaponStatCatalog {
   private static final double GENERAL_LIMIT = (double)100000.0F;
   private static final double RATING_LIMIT = (double)20000.0F;
   private static final double PERCENT_LIMIT = (double)500.0F;
   private static final double COST_LIMIT = (double)1000.0F;
   private static Set<String> METADATA_KEYS = Set.of("NAME", "MATERIAL", "DISPLAY", "DISPLAYED_TYPE", "LORE", "STORY", "TIER", "TOOLTIP", "TOOLTIP_PAGES", "ABILITY", "CRAFTING", "UPGRADE", "ENCHANTS", "PERM_EFFECTS", "DISABLE_ENCHANTING", "DISABLE_REPAIRING", "HIDE_ENCHANTS", "GEM_SOCKETS", "ITEM_PARTICLES", "CUSTOM_MODEL_DATA", "CUSTOM_MODEL_DATA_FLOAT", "ITEM_MODEL", "SKULL_TEXTURE", "MAX_DURABILITY", "UNBREAKABLE", "SET", "ITEM_SET", "REQUIRED_CLASS", "REQUIRED_CLASSES", "REQUIREMENTS", "CLASS", "COMMANDS", "NBTTAGS", "CUSTOM_NBT", "UPGRADE_TEMPLATE", "RUNE_SLOTS", "BROWSER_DISPLAY_IDX", "MAJOR_IDENTIFICATION", "MAJOR_ID");
   private static Map<String, String> ALIASES = Map.ofEntries(Map.entry("LIGHTNING_DAMAGE", "THUNDER_DAMAGE"), Map.entry("HOLY_DAMAGE", "LIGHTNESS_DAMAGE"), Map.entry("SHADOW_DAMAGE", "DARKNESS_DAMAGE"), Map.entry("AIR_DAMAGE", "WIND_DAMAGE"), Map.entry("WALK_SPEED", "MOVEMENT_SPEED"), Map.entry("MELEE_DAMAGE", "MAIN_ATTACK_DAMAGE_PERCENT"), Map.entry("RAW_MELEE_DAMAGE", "MAIN_ATTACK_DAMAGE"), Map.entry("RAW_MAIN_ATTACK_DAMAGE", "MAIN_ATTACK_DAMAGE"), Map.entry("RAW_SPELL_DAMAGE", "SPELL_DAMAGE"), Map.entry("HEALTH", "MAX_HEALTH"), Map.entry("HEALTH_REGEN", "HEALTH_REGENERATION"), Map.entry("MANA_REGEN", "MANA_REGENERATION"), Map.entry("LIFESTEAL", "LIFE_STEAL"), Map.entry("CRITICAL_DAMAGE_BONUS", "CRITICAL_STRIKE_POWER"), Map.entry("MAIN_ATTACK_RANGE", "RANGE"));
   private static Map<String, Info> STATS = buildCatalog();
   private static Map<String, CategoryInfo> CATEGORIES = defaultCategories();
   private static Set<String> LORE_HIDDEN = Set.of("REQUIRED_LEVEL");

   private WeaponStatCatalog() {
   }

   static boolean applyCatalog(StatCatalogLoader var0) {
      if (var0 != null && var0.isLoaded()) {
         LinkedHashMap var1 = new LinkedHashMap();

         for(StatCatalogLoader.Entry var3 : var0.stats().values()) {
            Category var4;
            try {
               var4 = WeaponStatCatalog.Category.valueOf(var3.category());
            } catch (IllegalArgumentException var6) {
               var4 = WeaponStatCatalog.Category.OFFENSE;
            }

            var1.put(var3.key(), new Info(var3.key(), var3.displayName(), var3.suffix(), var4, var3.limit()));
         }

         STATS = Collections.unmodifiableMap(var1);
         if (!var0.aliases().isEmpty()) {
            ALIASES = var0.aliases();
         }

         if (!var0.metadataKeys().isEmpty()) {
            METADATA_KEYS = var0.metadataKeys();
         }

         LinkedHashMap var7 = new LinkedHashMap();

         for(StatCatalogLoader.Category var9 : var0.categories().values()) {
            var7.put(var9.id(), new CategoryInfo(var9.id(), var9.displayName(), var9.color(), var9.icon(), var9.order()));
         }

         if (!var7.isEmpty()) {
            CATEGORIES = Collections.unmodifiableMap(var7);
         }

         LORE_HIDDEN = var0.loreHidden();
         return true;
      } else {
         return false;
      }
   }

   static CategoryInfo categoryInfo(Category var0) {
      CategoryInfo var1 = (CategoryInfo)CATEGORIES.get(var0.name());
      return var1 != null ? var1 : new CategoryInfo(var0.name(), var0.name(), "&7", "", 100);
   }

   static boolean hiddenInLore(String var0) {
      return LORE_HIDDEN.contains(var0);
   }

   static Category categoryOf(String var0) {
      if (var0 == null) {
         return null;
      } else {
         try {
            return WeaponStatCatalog.Category.valueOf(var0.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var2) {
            return null;
         }
      }
   }

   static List<CategoryInfo> orderedCategories() {
      return CATEGORIES.values().stream().sorted(Comparator.comparingInt(CategoryInfo::order)).toList();
   }

   private static Map<String, CategoryInfo> defaultCategories() {
      LinkedHashMap var0 = new LinkedHashMap();
      var0.put("OFFENSE", new CategoryInfo("OFFENSE", "攻擊", "&c", "✦", 1));
      var0.put("DEFENSE", new CategoryInfo("DEFENSE", "防禦", "&9", "◆", 2));
      var0.put("RESOURCE", new CategoryInfo("RESOURCE", "資源", "&b", "❖", 3));
      var0.put("MOBILITY", new CategoryInfo("MOBILITY", "機動", "&a", "✸", 4));
      var0.put("PRIMARY", new CategoryInfo("PRIMARY", "屬性", "&e", "✧", 5));
      var0.put("ECONOMY", new CategoryInfo("ECONOMY", "增益", "&6", "◈", 6));
      return var0;
   }

   static String normalize(String var0) {
      String var1 = StatSnapshot.normalize(var0);
      if (!var1.isBlank() && !METADATA_KEYS.contains(var1)) {
         String var2;
         return STATS.containsKey(var2 = (String)ALIASES.getOrDefault(var1, var1)) ? var2 : "";
      } else {
         return "";
      }
   }

   static Optional<Info> find(String var0) {
      String var1 = normalize(var0);
      return var1.isBlank() ? Optional.empty() : Optional.of((Info)STATS.get(var1));
   }

   static Set<String> knownKeys() {
      return STATS.keySet();
   }

   static double limit(String var0) {
      return (Double)find(var0).map(Info::limit).orElse((double)100000.0F);
   }

   private static Map<String, Info> buildCatalog() {
      LinkedHashMap var1 = new LinkedHashMap();
      add(var1, "ATTACK_DAMAGE", "攻擊傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "WEAPON_DAMAGE", "武器傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "MAIN_ATTACK_DAMAGE", "主攻擊傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "MAIN_ATTACK_DAMAGE_PERCENT", "主攻擊傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "SPELL_DAMAGE", "法術傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "SPELL_DAMAGE_PERCENT", "法術傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "MAGIC_DAMAGE", "魔法傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "SKILL_DAMAGE", "技能傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "ABILITY_DAMAGE", "能力傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "ALL_DAMAGE", "全傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "PVE_DAMAGE", "PvE 傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "PVP_DAMAGE", "PvP 傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "PROJECTILE_DAMAGE", "投射物傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "PHYSICAL_DAMAGE", "物理傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "ATTACK_SPEED", "攻擊速度", "", WeaponStatCatalog.Category.OFFENSE, (double)100.0F);
      add(var1, "ATTACK_SPEED_TIER", "攻擊速度階級", " 階", WeaponStatCatalog.Category.OFFENSE, (double)7.0F);
      add(var1, "RANGE", "主攻擊距離", " 格", WeaponStatCatalog.Category.OFFENSE, (double)64.0F);
      add(var1, "ARROW_VELOCITY", "投射速度", "", WeaponStatCatalog.Category.OFFENSE, (double)100.0F);
      add(var1, "KNOCKBACK", "擊退強度", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "CRITICAL_STRIKE_CHANCE", "暴擊機率", "%", WeaponStatCatalog.Category.OFFENSE, (double)100.0F);
      add(var1, "CRITICAL_STRIKE_RATING", "暴擊評級", "", WeaponStatCatalog.Category.OFFENSE, (double)20000.0F);
      add(var1, "CRITICAL_STRIKE_POWER", "暴擊威力", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "SKILL_CRITICAL_STRIKE_CHANCE", "技能暴擊機率", "%", WeaponStatCatalog.Category.OFFENSE, (double)100.0F);
      add(var1, "SKILL_CRITICAL_STRIKE_POWER", "技能暴擊威力", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "ARMOR_PENETRATION", "護甲穿透", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "ARMOR_PENETRATION_PERCENT", "護甲穿透", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "MAGIC_PENETRATION", "魔法穿透", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "MAGIC_PENETRATION_PERCENT", "魔法穿透", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "ELEMENTAL_PENETRATION", "元素穿透", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "ELEMENTAL_PENETRATION_PERCENT", "元素穿透", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "EXPLODING", "爆裂機率", "%", WeaponStatCatalog.Category.OFFENSE, (double)100.0F);
      add(var1, "POISON", "中毒傷害", "/3秒", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "SLOW_ENEMY", "緩速強度", "%", WeaponStatCatalog.Category.OFFENSE, (double)100.0F);
      add(var1, "WEAKEN_ENEMY", "弱化強度", "%", WeaponStatCatalog.Category.OFFENSE, (double)100.0F);
      add(var1, "HEALING_EFFICIENCY", "治療效率", "%", WeaponStatCatalog.Category.RESOURCE, (double)500.0F);
      add(var1, "DEFENSE", "防禦", "", WeaponStatCatalog.Category.DEFENSE, (double)100000.0F);
      add(var1, "ARMOR", "護甲", "", WeaponStatCatalog.Category.DEFENSE, (double)100000.0F);
      add(var1, "ARMOR_TOUGHNESS", "護甲韌性", "", WeaponStatCatalog.Category.DEFENSE, (double)100000.0F);
      add(var1, "MAGIC_RESISTANCE", "魔法抗性", "", WeaponStatCatalog.Category.DEFENSE, (double)100000.0F);
      add(var1, "ELEMENTAL_RESISTANCE", "元素抗性", "", WeaponStatCatalog.Category.DEFENSE, (double)100000.0F);
      add(var1, "ELEMENTAL_RESISTANCE_PERCENT", "元素抗性", "%", WeaponStatCatalog.Category.DEFENSE, (double)500.0F);
      add(var1, "DAMAGE_REDUCTION", "傷害減免", "%", WeaponStatCatalog.Category.DEFENSE, (double)90.0F);
      add(var1, "PHYSICAL_DAMAGE_REDUCTION", "物理減傷", "%", WeaponStatCatalog.Category.DEFENSE, (double)90.0F);
      add(var1, "MAGIC_DAMAGE_REDUCTION", "魔法減傷", "%", WeaponStatCatalog.Category.DEFENSE, (double)90.0F);
      add(var1, "PROJECTILE_DAMAGE_REDUCTION", "投射物減傷", "%", WeaponStatCatalog.Category.DEFENSE, (double)90.0F);
      add(var1, "PVE_DAMAGE_REDUCTION", "PvE 減傷", "%", WeaponStatCatalog.Category.DEFENSE, (double)90.0F);
      add(var1, "PVP_DAMAGE_REDUCTION", "PvP 減傷", "%", WeaponStatCatalog.Category.DEFENSE, (double)90.0F);
      add(var1, "KNOCKBACK_RESISTANCE", "擊退抗性", "%", WeaponStatCatalog.Category.DEFENSE, (double)100.0F);
      add(var1, "BLOCK_POWER", "格擋強度", "%", WeaponStatCatalog.Category.DEFENSE, (double)100.0F);
      add(var1, "BLOCK_RATING", "格擋評級", "", WeaponStatCatalog.Category.DEFENSE, (double)20000.0F);
      add(var1, "DODGE_RATING", "閃避評級", "", WeaponStatCatalog.Category.DEFENSE, (double)20000.0F);
      add(var1, "PARRY_RATING", "招架評級", "", WeaponStatCatalog.Category.DEFENSE, (double)20000.0F);
      add(var1, "BLUNT_POWER", "鈍擊威力", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "BLUNT_RATING", "鈍擊評級", "", WeaponStatCatalog.Category.OFFENSE, (double)20000.0F);
      add(var1, "THORNS", "荊棘反傷", "%", WeaponStatCatalog.Category.DEFENSE, (double)100.0F);
      add(var1, "REFLECTION", "投射反射", "%", WeaponStatCatalog.Category.DEFENSE, (double)100.0F);
      add(var1, "RESILIENCE", "韌性", "", WeaponStatCatalog.Category.PRIMARY, (double)100000.0F);
      add(var1, "MAX_HEALTH", "生命上限", "", WeaponStatCatalog.Category.RESOURCE, (double)100000.0F);
      add(var1, "MAX_HEALTH_PERCENT", "生命上限", "%", WeaponStatCatalog.Category.RESOURCE, (double)500.0F);
      add(var1, "HEALTH_REGENERATION", "生命回復", "/5秒", WeaponStatCatalog.Category.RESOURCE, (double)100000.0F);
      add(var1, "HEALTH_REGENERATION_PERCENT", "生命回復", "%", WeaponStatCatalog.Category.RESOURCE, (double)500.0F);
      add(var1, "MAX_MANA", "魔力上限", "", WeaponStatCatalog.Category.RESOURCE, (double)100000.0F);
      add(var1, "MAX_STAMINA", "耐力上限", "", WeaponStatCatalog.Category.RESOURCE, (double)100000.0F);
      add(var1, "MANA_REGENERATION", "魔力回復", "/5秒", WeaponStatCatalog.Category.RESOURCE, (double)100000.0F);
      add(var1, "STAMINA_REGENERATION", "耐力回復", "/5秒", WeaponStatCatalog.Category.RESOURCE, (double)100000.0F);
      add(var1, "LIFE_STEAL", "生命竊取", "/3秒", WeaponStatCatalog.Category.RESOURCE, (double)100000.0F);
      add(var1, "MANA_STEAL", "魔力竊取", "/3秒", WeaponStatCatalog.Category.RESOURCE, (double)100000.0F);
      add(var1, "SPELL_VAMPIRISM", "法術吸血", "%", WeaponStatCatalog.Category.RESOURCE, (double)100.0F);
      add(var1, "MANA_COST", "魔力消耗", "", WeaponStatCatalog.Category.RESOURCE, (double)1000.0F);
      add(var1, "STAMINA_COST", "耐力消耗", "", WeaponStatCatalog.Category.RESOURCE, (double)1000.0F);
      add(var1, "COOLDOWN", "技能冷卻", " 秒", WeaponStatCatalog.Category.RESOURCE, (double)1000.0F);
      add(var1, "COOLDOWN_REDUCTION", "冷卻縮減", "%", WeaponStatCatalog.Category.RESOURCE, (double)90.0F);

      for(int var2 = 1; var2 <= 4; ++var2) {
         add(var1, "SPELL_" + var2 + "_COST", "第 " + var2 + " 技能消耗", "", WeaponStatCatalog.Category.RESOURCE, (double)1000.0F);
         add(var1, "SPELL_" + var2 + "_COST_PERCENT", "第 " + var2 + " 技能消耗", "%", WeaponStatCatalog.Category.RESOURCE, (double)500.0F);
      }

      add(var1, "MOVEMENT_SPEED", "移動速度", "%", WeaponStatCatalog.Category.MOBILITY, (double)500.0F);
      add(var1, "SPRINT", "衝刺耐力", "%", WeaponStatCatalog.Category.MOBILITY, (double)500.0F);
      add(var1, "SPRINT_REGEN", "衝刺回復", "%", WeaponStatCatalog.Category.MOBILITY, (double)500.0F);
      add(var1, "JUMP_HEIGHT", "跳躍高度", "", WeaponStatCatalog.Category.MOBILITY, (double)16.0F);
      add(var1, "XP_BONUS", "戰鬥經驗", "%", WeaponStatCatalog.Category.ECONOMY, (double)500.0F);
      add(var1, "LOOT_BONUS", "戰利品數量", "%", WeaponStatCatalog.Category.ECONOMY, (double)500.0F);
      add(var1, "LOOT_QUALITY", "戰利品品質", "%", WeaponStatCatalog.Category.ECONOMY, (double)500.0F);
      add(var1, "STEALING", "掠奪機率", "%", WeaponStatCatalog.Category.ECONOMY, (double)100.0F);
      add(var1, "GATHER_XP_BONUS", "採集經驗", "%", WeaponStatCatalog.Category.ECONOMY, (double)500.0F);
      add(var1, "GATHER_SPEED", "採集速度", "%", WeaponStatCatalog.Category.ECONOMY, (double)500.0F);
      add(var1, "STRENGTH", "力量", "", WeaponStatCatalog.Category.PRIMARY, (double)200.0F);
      add(var1, "DEXTERITY", "靈巧", "", WeaponStatCatalog.Category.PRIMARY, (double)200.0F);
      add(var1, "INTELLIGENCE", "智力", "", WeaponStatCatalog.Category.PRIMARY, (double)200.0F);
      add(var1, "DEFENCE", "護甲能力", "", WeaponStatCatalog.Category.PRIMARY, (double)200.0F);
      add(var1, "AGILITY", "敏捷", "", WeaponStatCatalog.Category.PRIMARY, (double)200.0F);
      add(var1, "WISDOM", "靈性", "", WeaponStatCatalog.Category.PRIMARY, (double)200.0F);
      add(var1, "VITALITY", "體魄", "", WeaponStatCatalog.Category.PRIMARY, (double)200.0F);

      for(String[] var5 : new String[][]{{"EARTH", "大地"}, {"THUNDER", "雷霆"}, {"WATER", "流水"}, {"FIRE", "烈焰"}, {"WIND", "疾風"}, {"ICE", "冰霜"}, {"DARKNESS", "暗影"}, {"LIGHTNESS", "聖光"}, {"ARCANE", "奧術"}, {"NATURE", "自然"}}) {
         String var6 = var5[0];
         String var7 = var5[1];
         add(var1, var6 + "_DAMAGE", var7 + "傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
         add(var1, var6 + "_DAMAGE_PERCENT", var7 + "傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
         add(var1, var6 + "_MAIN_ATTACK_DAMAGE", var7 + "主攻擊傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
         add(var1, var6 + "_MAIN_ATTACK_DAMAGE_PERCENT", var7 + "主攻擊傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
         add(var1, var6 + "_SPELL_DAMAGE", var7 + "法術傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
         add(var1, var6 + "_SPELL_DAMAGE_PERCENT", var7 + "法術傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
         add(var1, var6 + "_RESISTANCE", var7 + "抗性", "", WeaponStatCatalog.Category.DEFENSE, (double)100000.0F);
         add(var1, var6 + "_RESISTANCE_PERCENT", var7 + "抗性", "%", WeaponStatCatalog.Category.DEFENSE, (double)500.0F);
      }

      add(var1, "ELEMENTAL_DAMAGE", "元素傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "ELEMENTAL_DAMAGE_PERCENT", "元素傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "ELEMENTAL_MAIN_ATTACK_DAMAGE", "元素主攻擊傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "ELEMENTAL_MAIN_ATTACK_DAMAGE_PERCENT", "元素主攻擊傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "ELEMENTAL_SPELL_DAMAGE", "元素法術傷害", "", WeaponStatCatalog.Category.OFFENSE, (double)100000.0F);
      add(var1, "ELEMENTAL_SPELL_DAMAGE_PERCENT", "元素法術傷害", "%", WeaponStatCatalog.Category.OFFENSE, (double)500.0F);
      add(var1, "REQUIRED_LEVEL", "需求等級", "", WeaponStatCatalog.Category.PRIMARY, (double)1000.0F);
      return Collections.unmodifiableMap(var1);
   }

   private static void add(Map<String, Info> var0, String var1, String var2, String var3, Category var4, double var5) {
      String var7 = var1.toUpperCase(Locale.ROOT);
      var0.put(var7, new Info(var7, var2, var3, var4, var5));
   }

   static record Info(String key, String displayName, String suffix, Category category, double limit) {
   }

   static record CategoryInfo(String id, String displayName, String color, String icon, int order) {
      CategoryInfo(String id, String displayName, String color, String icon, int order) {
         icon = icon == null ? "" : icon;
         this.id = id;
         this.displayName = displayName;
         this.color = color;
         this.icon = icon;
         this.order = order;
      }
   }

   static enum Category {
      OFFENSE,
      DEFENSE,
      RESOURCE,
      MOBILITY,
      ECONOMY,
      PRIMARY;

      private Category() {
      }
   }
}
