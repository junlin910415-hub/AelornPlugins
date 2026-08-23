package tw.linsy.aelorn.mmoitems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

final class ItemTemplate {
   private static final int MAX_TEMPLATE_LEVEL = 1000;
   private static final int MAX_SOCKETS = 12;
   private final String type;
   private final String id;
   private final String name;
   private final String displayedType;
   private final Material material;
   private final String tier;
   private final String tooltip;
   private final List<String> lore;
   private final List<String> story;
   private final int tooltipPages;
   private final double browserIndex;
   private final int customModelData;
   private final String itemModel;
   private final boolean unbreakable;
   private final String setId;
   private final String requiredClass;
   private final int requiredLevel;
   private final Map<String, Integer> requiredSkills;
   private final List<String> requiredQuests;
   private final MajorIdentificationData majorIdentification;
   private final String upgradeTemplate;
   private final int gemSockets;
   private final int runeSlots;
   private final AbilityData ability;
   private final Map<String, ScaledValue> stats;

   private ItemTemplate(String var1, String var2, String var3, String var4, Material var5, String var6, String var7, List<String> var8, List<String> var9, int var10, double var11, int var13, String var14, boolean var15, String var16, String var17, int var18, Map<String, Integer> var19, List<String> var20, MajorIdentificationData var21, String var22, int var23, int var24, AbilityData var25, Map<String, ScaledValue> var26) {
      this.type = var1;
      this.id = var2;
      this.name = var3;
      this.displayedType = var4 == null ? "" : var4.trim();
      this.material = var5;
      this.tier = var6;
      this.tooltip = var7 == null ? "" : var7.trim();
      this.lore = List.copyOf(var8);
      this.story = List.copyOf(var9.isEmpty() ? var8 : var9);
      this.tooltipPages = var10 != 3 && var10 != 5 ? 0 : var10;
      this.browserIndex = var11;
      this.customModelData = var13;
      this.itemModel = var14 == null ? "" : var14.trim().toLowerCase(Locale.ROOT);
      this.unbreakable = var15;
      this.setId = normalizeId(var16);
      this.requiredClass = var17 == null ? "" : var17.trim();
      this.requiredLevel = Math.max(0, Math.min(1000, var18));
      this.requiredSkills = Map.copyOf(var19);
      this.requiredQuests = List.copyOf(var20);
      this.majorIdentification = var21 == null ? ItemTemplate.MajorIdentificationData.empty() : var21;
      this.upgradeTemplate = var22 != null && !var22.isBlank() ? normalizeId(var22) : "weapon-default";
      this.gemSockets = Math.max(0, Math.min(12, var23));
      this.runeSlots = Math.max(0, Math.min(12, var24));
      this.ability = var25 == null ? ItemTemplate.AbilityData.empty() : var25;
      this.stats = Map.copyOf(var26);
   }

   static ItemTemplate fromSection(String var0, String var1, ConfigurationSection var2) {
      ConfigurationSection var3 = var2.getConfigurationSection("base");
      ConfigurationSection var4 = var3 == null ? var2 : var3;
      Material var5 = parseMaterial(var4.getString("material", var4.getString("display", "IRON_SWORD")));
      String var6 = var4.getString("name", "&f" + humanize(var1));
      String var7 = var4.getString("displayed-type", var4.getString("displayed_type", ""));
      String var8 = var4.getString("tier", "COMMON").toUpperCase(Locale.ROOT);
      String var9 = var4.getString("tooltip", "");
      List var10 = var4.getStringList("lore");
      List var11 = var4.getStringList("story");
      int var12 = var4.getInt("tooltip-pages", var4.getInt("tooltip_pages", 0));
      double var13 = var4.getDouble("browser-display-idx", var4.getDouble("browser_display_idx", (double)0.0F));
      int var15 = var4.getInt("custom-model-data", var4.getInt("custom_model_data", 0));
      String var16 = var4.getString("item-model", var4.getString("item_model", ""));
      boolean var17 = var4.getBoolean("unbreakable", false);
      String var18 = var4.getString("set", var4.getString("item-set", ""));
      ConfigurationSection var19 = var4.getConfigurationSection("requirements");
      String var20 = readRequiredClasses(var4, var19);
      int var21 = readRequiredLevel(var4, var19);
      Map var22 = readRequiredSkills(var4, var19);
      List var23 = readRequiredQuests(var4, var19);
      MajorIdentificationData var24 = ItemTemplate.MajorIdentificationData.from(var4);
      String var25 = readUpgradeTemplate(var4);
      int var26 = readGemSockets(var4);
      int var27 = var4.getInt("rune-slots", var4.getInt("rune_slots", 1));
      AbilityData var28 = ItemTemplate.AbilityData.from(var4);
      LinkedHashMap var29 = new LinkedHashMap();
      collectStats(var4, var29);
      return new ItemTemplate(var0.toUpperCase(Locale.ROOT), var1.toUpperCase(Locale.ROOT), var6, var7, var5, var8, var9, var10, var11, var12, var13, var15, var16, var17, var18, var20, var21, var22, var23, var24, var25, var26, var27, var28, var29);
   }

   Map<String, Double> statsAtLevel(int var1, Random var2) {
      LinkedHashMap var3 = new LinkedHashMap();
      int var4 = Math.max(1, Math.min(1000, var1));

      for(Map.Entry var6 : this.stats.entrySet()) {
         double var7 = ScaledValue.sanitize(((ScaledValue)var6.getValue()).at(var4, var2), WeaponStatCatalog.limit((String)var6.getKey()));
         if (Math.abs(var7) > 1.0E-6) {
            var3.put((String)var6.getKey(), var7);
         }
      }

      return var3;
   }

   List<String> statKeys() {
      return List.copyOf(this.stats.keySet());
   }

   double baseStat(String var1) {
      String var2 = WeaponStatCatalog.normalize(var1);
      ScaledValue var3 = var2.isBlank() ? null : (ScaledValue)this.stats.get(var2);
      return var3 == null ? (double)0.0F : var3.base();
   }

   String type() {
      return this.type;
   }

   String id() {
      return this.id;
   }

   String name() {
      return this.name;
   }

   String displayedType() {
      return this.displayedType;
   }

   Material material() {
      return this.material;
   }

   String tier() {
      return this.tier;
   }

   String tooltip() {
      return this.tooltip;
   }

   List<String> lore() {
      return this.lore;
   }

   List<String> story() {
      return this.story;
   }

   int tooltipPages() {
      return this.tooltipPages;
   }

   double browserIndex() {
      return this.browserIndex;
   }

   int customModelData() {
      return this.customModelData;
   }

   String itemModel() {
      return this.itemModel;
   }

   boolean unbreakable() {
      return this.unbreakable;
   }

   String setId() {
      return this.setId;
   }

   String requiredClass() {
      return this.requiredClass;
   }

   int requiredLevel() {
      return this.requiredLevel;
   }

   Map<String, Integer> requiredSkills() {
      return this.requiredSkills;
   }

   List<String> requiredQuests() {
      return this.requiredQuests;
   }

   MajorIdentificationData majorIdentification() {
      return this.majorIdentification;
   }

   AbilityData ability() {
      return this.ability;
   }

   String upgradeTemplate() {
      return this.upgradeTemplate;
   }

   int gemSockets() {
      return this.gemSockets;
   }

   int runeSlots() {
      return this.runeSlots;
   }

   private static void collectStats(ConfigurationSection var0, Map<String, ScaledValue> var1) {
      for(String var3 : var0.getKeys(false)) {
         if (var3.equalsIgnoreCase("element")) {
            collectElementStats(var0.getConfigurationSection(var3), var1);
         } else {
            String var5 = WeaponStatCatalog.normalize(var3);
            ScaledValue var4;
            if (!var5.isBlank() && (var4 = ScaledValue.from(var0, var3)) != null) {
               var1.put(var5, var4);
            }
         }
      }

   }

   private static void collectElementStats(ConfigurationSection var0, Map<String, ScaledValue> var1) {
      if (var0 != null) {
         for(String var3 : var0.getKeys(false)) {
            ConfigurationSection var4 = var0.getConfigurationSection(var3);
            if (var4 != null) {
               ScaledValue var5 = var4.isConfigurationSection("damage") ? ScaledValue.fromSection(var4.getConfigurationSection("damage")) : ScaledValue.from(var4, "damage");
               String var6 = WeaponStatCatalog.normalize(var3 + "_damage");
               if (var5 != null && !var6.isBlank()) {
                  var1.put(var6, var5);
               }
            }
         }

      }
   }

   private static String readRequiredClasses(ConfigurationSection var0, ConfigurationSection var1) {
      List var3 = var1 == null ? List.of() : var1.getStringList("classes");
      if (!var3.isEmpty()) {
         return String.join("|", var3);
      } else {
         String var2 = var1 == null ? "" : var1.getString("class", "");
         return !var2.isBlank() ? var2 : var0.getString("required-class", var0.getString("class", ""));
      }
   }

   private static int readRequiredLevel(ConfigurationSection var0, ConfigurationSection var1) {
      if (var1 == null || !var1.contains("combat-level") && !var1.contains("level")) {
         ScaledValue var2 = ScaledValue.from(var0, "required-level");
         if (var2 != null) {
            return (int)Math.ceil(var2.base());
         } else {
            ScaledValue var3 = ScaledValue.from(var0, "required_level");
            return var3 == null ? 0 : (int)Math.ceil(var3.base());
         }
      } else {
         return var1.getInt("combat-level", var1.getInt("level", 0));
      }
   }

   private static Map<String, Integer> readRequiredSkills(ConfigurationSection var0, ConfigurationSection var1) {
      LinkedHashMap var3 = new LinkedHashMap();
      ConfigurationSection var2 = var1 == null ? null : var1.getConfigurationSection("skills");
      if (var2 != null) {
         for(String var6 : var2.getKeys(false)) {
            putRequirement(var3, var6, var2.getInt(var6));
         }
      }

      for(String var9 : List.of("strength", "dexterity", "intelligence", "defence", "defense", "agility", "wisdom", "vitality", "resilience")) {
         String var7 = "required-" + var9;
         if (var0.contains(var7)) {
            putRequirement(var3, var9, var0.getInt(var7));
         }
      }

      return var3;
   }

   private static void putRequirement(Map<String, Integer> var0, String var1, int var2) {
      if (var2 > 0) {
         String var3 = normalizeId(var1);
         if (var3.equals("DEFENSE")) {
            var3 = "DEFENCE";
         }

         var0.put(var3, Math.min(200, var2));
      }
   }

   private static List<String> readRequiredQuests(ConfigurationSection var0, ConfigurationSection var1) {
      LinkedHashSet<String> var2 = new LinkedHashSet<>();
      if (var1 != null) {
         var2.addAll(var1.getStringList("quests"));
         String var3 = var1.getString("quest", "");
         if (!var3.isBlank()) {
            var2.add(var3);
         }
      }

      var2.addAll(var0.getStringList("required-quests"));
      String var4 = var0.getString("required-quest", "");
      if (!var4.isBlank()) {
         var2.add(var4);
      }

      return var2.stream().map(ItemTemplate::normalizeId).filter((var0x) -> !var0x.isBlank()).toList();
   }

   private static String readUpgradeTemplate(ConfigurationSection var0) {
      if (var0.isString("upgrade-template")) {
         return var0.getString("upgrade-template", "weapon-default");
      } else if (var0.isString("upgrade_template")) {
         return var0.getString("upgrade_template", "weapon-default");
      } else {
         return var0.isString("upgrade") ? var0.getString("upgrade", "weapon-default") : "weapon-default";
      }
   }

   private static int readGemSockets(ConfigurationSection var0) {
      if (var0.isInt("gem-sockets")) {
         return var0.getInt("gem-sockets", 0);
      } else if (var0.isInt("gem_sockets")) {
         return var0.getInt("gem_sockets", 0);
      } else {
         List var1 = var0.getStringList("gem-sockets");
         if (!var1.isEmpty()) {
            return var1.size();
         } else {
            var1 = var0.getStringList("gem_sockets");
            if (!var1.isEmpty()) {
               return var1.size();
            } else {
               ConfigurationSection var2 = var0.getConfigurationSection("gem-sockets");
               return var2 == null ? 0 : var2.getInt("amount", var2.getInt("max", Math.max(0, var2.getKeys(false).size())));
            }
         }
      }
   }

   private static Material parseMaterial(String var0) {
      if (var0 != null && !var0.isBlank()) {
         String var1 = var0.split(":", 2)[0].trim().toUpperCase(Locale.ROOT);
         Material var2 = Material.matchMaterial(var1);
         return var2 != null && var2 != Material.AIR ? var2 : Material.IRON_SWORD;
      } else {
         return Material.IRON_SWORD;
      }
   }

   private static String humanize(String var0) {
      ArrayList var1 = new ArrayList();

      for(String var5 : var0.toLowerCase(Locale.ROOT).split("[_-]+")) {
         if (!var5.isBlank()) {
            String var10001 = var5.substring(0, 1).toUpperCase(Locale.ROOT);
            var1.add(var10001 + var5.substring(1));
         }
      }

      return String.join(" ", var1);
   }

   private static String normalizeId(String var0) {
      return var0 == null ? "" : var0.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
   }

   static record MajorIdentificationData(String id, String displayName, List<String> description) {
      MajorIdentificationData(String id, String displayName, List<String> description) {
         id = ItemTemplate.normalizeId(id);
         displayName = displayName == null ? "" : displayName.trim();
         description = List.copyOf(description == null ? List.of() : description);
         this.id = id;
         this.displayName = displayName;
         this.description = description;
      }

      static MajorIdentificationData empty() {
         return new MajorIdentificationData("", "", List.of());
      }

      static MajorIdentificationData from(ConfigurationSection var0) {
         ConfigurationSection var1 = var0.getConfigurationSection("major-identification");
         if (var1 == null) {
            var1 = var0.getConfigurationSection("major_id");
         }

         if (var1 == null) {
            String var3 = var0.getString("major-id", "");
            return var3.isBlank() ? empty() : new MajorIdentificationData(var3, var3, List.of());
         } else {
            String var2 = var1.getString("id", "");
            return var2.isBlank() ? empty() : new MajorIdentificationData(var2, var1.getString("name", ItemTemplate.humanize(var2)), var1.getStringList("description"));
         }
      }

      boolean enabled() {
         return !this.id.isBlank();
      }
   }

   static record AbilityData(String trigger, String type, double chance, double power) {
      static AbilityData empty() {
         return new AbilityData("", "", (double)0.0F, (double)0.0F);
      }

      boolean enabled() {
         return !this.type.isBlank();
      }

      static AbilityData from(ConfigurationSection var0) {
         ConfigurationSection var1 = var0.getConfigurationSection("ability");
         if (var1 == null) {
            return empty();
         } else {
            ConfigurationSection var2 = var1.getConfigurationSection("on-hit");
            if (var2 == null) {
               var2 = var1.getConfigurationSection("on_hit");
            }

            if (var2 == null) {
               return empty();
            } else {
               String var3 = var2.getString("type", "").toUpperCase(Locale.ROOT);
               double var4 = ScaledValue.sanitize(var2.getDouble("chance", (double)100.0F), (double)100.0F);
               ScaledValue var6 = var2.isConfigurationSection("damage") ? ScaledValue.fromSection(var2.getConfigurationSection("damage")) : null;
               double var7 = var6 == null ? var2.getDouble("power", var2.getDouble("damage", (double)0.0F)) : var6.base();
               return new AbilityData("ON_HIT", var3, Math.max((double)0.0F, Math.min((double)100.0F, var4)), ScaledValue.sanitize(var7, (double)50000.0F));
            }
         }
      }
   }
}
