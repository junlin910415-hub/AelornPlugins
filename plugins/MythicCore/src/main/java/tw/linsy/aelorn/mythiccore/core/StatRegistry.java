package tw.linsy.aelorn.mythiccore.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;

public final class StatRegistry {
   private static final List<String> BUILTIN_STATS = List.of("ATTACK_DAMAGE", "WEAPON_DAMAGE", "MAGIC_DAMAGE", "SKILL_DAMAGE", "ABILITY_DAMAGE", "ATTACK_SPEED", "CRITICAL_STRIKE_CHANCE", "CRITICAL_STRIKE_RATING", "CRITICAL_STRIKE_POWER", "SKILL_CRITICAL_STRIKE_CHANCE", "SKILL_CRITICAL_STRIKE_POWER", "DEFENSE", "ARMOR", "ARMOR_TOUGHNESS", "ARMOR_PENETRATION", "ARMOR_PENETRATION_PERCENT", "MAGIC_PENETRATION", "MAGIC_PENETRATION_PERCENT", "ELEMENTAL_PENETRATION", "ELEMENTAL_PENETRATION_PERCENT", "MAGIC_RESISTANCE", "ELEMENTAL_RESISTANCE", "RESILIENCE", "MAX_HEALTH", "HEALTH_REGENERATION", "MOVEMENT_SPEED", "MANA", "MAX_MANA", "MAX_STAMINA", "MANA_REGENERATION", "STAMINA_REGENERATION", "MANA_COST", "STAMINA_COST", "COOLDOWN_REDUCTION", "DODGE_RATING", "PARRY_RATING", "BLOCK_RATING", "BLOCK_POWER", "DAMAGE_REDUCTION", "PROJECTILE_DAMAGE", "PHYSICAL_DAMAGE", "KNOCKBACK", "KNOCKBACK_RESISTANCE", "BLUNT_POWER", "BLUNT_RATING", "LIFE_STEAL", "REQUIRED_LEVEL", "STRENGTH", "DEXTERITY", "INTELLIGENCE", "WISDOM", "VITALITY", "ALL_DAMAGE", "PVE_DAMAGE", "PVP_DAMAGE", "FIRE_DAMAGE", "ICE_DAMAGE", "THUNDER_DAMAGE", "LIGHTNING_DAMAGE", "WIND_DAMAGE", "EARTH_DAMAGE", "WATER_DAMAGE", "DARKNESS_DAMAGE", "SHADOW_DAMAGE", "LIGHTNESS_DAMAGE", "HOLY_DAMAGE", "ARCANE_DAMAGE", "NATURE_DAMAGE", "FIRE_RESISTANCE", "ICE_RESISTANCE", "THUNDER_RESISTANCE", "LIGHTNING_RESISTANCE", "WIND_RESISTANCE", "EARTH_RESISTANCE", "WATER_RESISTANCE", "DARKNESS_RESISTANCE", "SHADOW_RESISTANCE", "LIGHTNESS_RESISTANCE", "HOLY_RESISTANCE", "ARCANE_RESISTANCE", "NATURE_RESISTANCE", "VENDOR_VALUE", "BUY_PRICE", "SELL_PRICE");
   private static final Map<String, String> STAT_ALIASES = Map.ofEntries(Map.entry("LIGHTNING_DAMAGE", "THUNDER_DAMAGE"), Map.entry("LIGHTNING_RESISTANCE", "THUNDER_RESISTANCE"), Map.entry("HOLY_DAMAGE", "LIGHTNESS_DAMAGE"), Map.entry("HOLY_RESISTANCE", "LIGHTNESS_RESISTANCE"), Map.entry("SHADOW_DAMAGE", "DARKNESS_DAMAGE"), Map.entry("SHADOW_RESISTANCE", "DARKNESS_RESISTANCE"), Map.entry("HP", "MAX_HEALTH"), Map.entry("HEALTH", "MAX_HEALTH"), Map.entry("MP", "MAX_MANA"), Map.entry("CRIT_CHANCE", "CRITICAL_STRIKE_CHANCE"), Map.entry("CRIT_POWER", "CRITICAL_STRIKE_POWER"), Map.entry("CRIT_DAMAGE", "CRITICAL_STRIKE_POWER"), Map.entry("LIFESTEAL", "LIFE_STEAL"));
   private static final Map<String, String> RPG_STAT_ALIASES = Map.of("HEALTH", "MAX_HEALTH", "HP", "MAX_HEALTH", "MANA", "MAX_MANA", "MP", "MAX_MANA", "ATTACK", "ATTACK_DAMAGE", "POWER", "ATTACK_DAMAGE", "RESISTANCE", "MAGIC_RESISTANCE", "SPEED", "MOVEMENT_SPEED");
   private static final Map<String, String> CLASS_ALIASES = Map.ofEntries(Map.entry("WARRIOR", "VANGUARD"), Map.entry("KNIGHT", "VANGUARD"), Map.entry("MAGE", "ARCANIST"), Map.entry("WIZARD", "ARCANIST"), Map.entry("MAGICIAN", "ARCANIST"), Map.entry("SORCERER", "ARCANIST"), Map.entry("ARCHER", "RANGER"), Map.entry("HUNTER", "RANGER"), Map.entry("ASSASSIN", "SHADOWBLADE"), Map.entry("ROGUE", "SHADOWBLADE"), Map.entry("SHAMAN", "WARDEN"), Map.entry("PRIEST", "WARDEN"));
   private static final Map<String, String> TIER_ALIASES = Map.ofEntries(Map.entry("NOVICE", "STARTER"), Map.entry("TEST", "STARTER"), Map.entry("STARTER_TRAINING", "STARTER"), Map.entry("NORMAL", "COMMON"), Map.entry("UNCOMMON", "COMMON"), Map.entry("VERY_RARE", "EPIC"), Map.entry("MYTHIC", "VAST"), Map.entry("MYTHICAL", "VAST"));
   private static final int CANONICAL_CACHE_LIMIT = 2048;
   private static final double EPSILON = 1.0E-6;
   private final Set<String> knownStats = ConcurrentHashMap.newKeySet();
   private final ConcurrentHashMap<String, String> canonicalCache = new ConcurrentHashMap();
   private volatile Map<String, Double> baseStats = Map.of();
   private volatile Map<String, StatClamp> clamps = Map.of();
   private volatile Map<String, Map<Integer, Map<String, Double>>> setBonuses = Map.of();
   private volatile Limits limits = new Limits((double)100000.0F, (double)500.0F, (double)20000.0F);

   public StatRegistry() {
   }

   public void load(FileConfiguration var1) {
      Limits var2 = new Limits(Math.max((double)1.0F, var1.getDouble("safety.max-stat-value", (double)100000.0F)), Math.max((double)1.0F, var1.getDouble("safety.max-percent-value", (double)500.0F)), Math.max((double)1.0F, var1.getDouble("safety.max-rating-value", (double)20000.0F)));
      LinkedHashMap var3 = new LinkedHashMap();
      ConfigurationSection var4 = var1.getConfigurationSection("stats.clamps");
      if (var4 != null) {
         for(String var6 : var4.getKeys(false)) {
            var3.put(StatSnapshot.normalize(var6), StatRegistry.StatClamp.parse(var1.getString("stats.clamps." + var6, "")));
         }
      }

      LinkedHashMap var10 = new LinkedHashMap();
      ConfigurationSection var11 = var1.getConfigurationSection("stats.base");
      if (var11 != null) {
         for(String var8 : var11.getKeys(false)) {
            var10.put(StatSnapshot.normalize(var8), var11.getDouble(var8));
         }
      }

      ConcurrentHashMap.KeySetView var12 = ConcurrentHashMap.newKeySet();
      BUILTIN_STATS.forEach((var1x) -> var12.add(StatSnapshot.normalize(var1x)));
      var12.addAll(var10.keySet());
      var12.addAll(var3.keySet());

      for(String var9 : var1.getStringList("stats.known")) {
         if (var9 != null && !var9.isBlank()) {
            var12.add(StatSnapshot.normalize(var9));
         }
      }

      this.limits = var2;
      this.clamps = Map.copyOf(var3);
      this.canonicalCache.clear();
      LinkedHashMap var14 = new LinkedHashMap();
      var10.forEach((var2x, var3x) -> var14.put(var2x, this.sanitize(var2x, var3x)));
      this.baseStats = Map.copyOf(var14);
      this.knownStats.clear();
      this.knownStats.addAll(var12);
   }

   public void registerKnownStats(Iterable<String> var1) {
      if (var1 != null) {
         for(String var3 : var1) {
            if (var3 != null && !var3.isBlank()) {
               this.knownStats.add(this.canonical(var3));
            }
         }

      }
   }

   public void registerKnownStat(String var1) {
      if (var1 != null && !var1.isBlank()) {
         this.knownStats.add(var1);
      }

   }

   public void registerSetBonuses(Map<String, Map<Integer, Map<String, Double>>> var1) {
      if (var1 != null && !var1.isEmpty()) {
         LinkedHashMap var2 = new LinkedHashMap();

         for(Map.Entry var4 : var1.entrySet()) {
            LinkedHashMap var5 = new LinkedHashMap();

            for(Map.Entry var7 : ((Map)var4.getValue()).entrySet()) {
               LinkedHashMap var8 = new LinkedHashMap();

               for(Map.Entry var10 : ((Map)var7.getValue()).entrySet()) {
                  String var11 = this.canonical((String)var10.getKey());
                  double var12 = this.sanitize(var11, var10.getValue() == null ? (double)0.0F : (Double)var10.getValue());
                  if (Math.abs(var12) > 1.0E-6) {
                     var8.put(var11, var12);
                  }

                  this.knownStats.add(var11);
               }

               var5.put((Integer)var7.getKey(), Map.copyOf(var8));
            }

            var2.put(StatSnapshot.normalize((String)var4.getKey()), Map.copyOf(var5));
         }

         this.setBonuses = Map.copyOf(var2);
      } else {
         this.setBonuses = Map.of();
      }
   }

   public String canonical(String var1) {
      if (var1 != null && !var1.isEmpty()) {
         String var2 = (String)this.canonicalCache.get(var1);
         if (var2 != null) {
            return var2;
         } else {
            String var3 = StatSnapshot.normalize(var1);
            String var4 = (String)STAT_ALIASES.getOrDefault(var3, var3);
            if (this.canonicalCache.size() < 2048) {
               this.canonicalCache.putIfAbsent(var1, var4);
            }

            return var4;
         }
      } else {
         return "";
      }
   }

   public String rpgStatAlias(String var1) {
      String var2 = StatSnapshot.normalize(var1);
      String var3 = (String)RPG_STAT_ALIASES.get(var2);
      return var3 != null ? var3 : this.canonical(var2);
   }

   public static String normalizeClassId(String var0) {
      String var1 = StatSnapshot.normalize(var0);
      return (String)CLASS_ALIASES.getOrDefault(var1, var1);
   }

   public static String normalizeTierId(String var0) {
      String var1 = StatSnapshot.normalize(var0);
      String var2 = (String)TIER_ALIASES.get(var1);
      if (var2 != null) {
         return var2;
      } else {
         return var1.isBlank() ? "COMMON" : var1;
      }
   }

   public double sanitize(String var1, double var2) {
      if (!Double.isFinite(var2)) {
         return (double)0.0F;
      } else {
         StatClamp var4 = (StatClamp)this.clamps.get(var1);
         double var5 = var4 == null ? var2 : var4.clamp(var2);
         double var7 = this.limitFor(var1);
         return Math.max(-var7, Math.min(var7, var5));
      }
   }

   public double limitFor(String var1) {
      Limits var2 = this.limits;
      if (!var1.endsWith("_CHANCE") && !var1.endsWith("_POWER") && !var1.endsWith("_PERCENT") && !var1.endsWith("_REDUCTION") && !var1.equals("LIFE_STEAL")) {
         if (var1.endsWith("_RATING")) {
            return var2.maxRating();
         } else {
            return var1.equals("MOVEMENT_SPEED") ? (double)2.0F : var2.maxStat();
         }
      } else {
         return var2.maxPercent();
      }
   }

   public void mergeInto(Map<String, Double> var1, Map<String, Double> var2) {
      for(Map.Entry var4 : var2.entrySet()) {
         String var5 = this.canonical((String)var4.getKey());
         double var6 = this.sanitize(var5, var4.getValue() == null ? (double)0.0F : (Double)var4.getValue());
         if (Math.abs(var6) > 1.0E-6) {
            var1.merge(var5, var6, (var2x, var3) -> this.sanitize(var5, var2x + var3));
         }
      }

   }

   public void sanitizeAll(Map<String, Double> var1) {
      for(Map.Entry var3 : List.copyOf(var1.entrySet())) {
         String var4 = this.canonical((String)var3.getKey());
         double var5 = this.sanitize(var4, var3.getValue() == null ? (double)0.0F : (Double)var3.getValue());
         var1.remove(var3.getKey());
         if (Math.abs(var5) > 1.0E-6) {
            var1.put(var4, var5);
         }
      }

   }

   public Set<String> knownStats() {
      return this.knownStats;
   }

   public Map<String, Double> baseStats() {
      return this.baseStats;
   }

   public Map<String, StatClamp> clamps() {
      return this.clamps;
   }

   public Map<Integer, Map<String, Double>> setBonusesFor(String var1) {
      return (Map)this.setBonuses.getOrDefault(var1, Map.of());
   }

   public Map<String, Map<Integer, Map<String, Double>>> setBonuses() {
      return this.setBonuses;
   }

   public static record StatClamp(Double minimum, Double maximum) {
      public double clamp(double var1) {
         if (!Double.isFinite(var1)) {
            return (double)0.0F;
         } else {
            double var3 = var1;
            if (this.minimum != null) {
               var3 = Math.max(this.minimum, var1);
            }

            if (this.maximum != null) {
               var3 = Math.min(this.maximum, var3);
            }

            return var3;
         }
      }

      public static StatClamp parse(String var0) {
         if (var0 != null && var0.contains("=")) {
            String[] var1 = var0.split("=", 2);
            return new StatClamp(parseNullable(var1[0]), parseNullable(var1[1]));
         } else {
            return new StatClamp((Double)null, (Double)null);
         }
      }

      private static Double parseNullable(String var0) {
         if (var0 != null && !var0.trim().isEmpty()) {
            try {
               double var1 = Double.parseDouble(var0.trim());
               return Double.isFinite(var1) ? var1 : null;
            } catch (NumberFormatException var3) {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   private static record Limits(double maxStat, double maxPercent, double maxRating) {
   }
}
