package tw.linsy.aelorn.mythiccore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import tw.linsy.aelorn.mythiccore.api.ArchetypeProfile;
import tw.linsy.aelorn.mythiccore.api.ClassProfile;
import tw.linsy.aelorn.mythiccore.api.ClassStageProfile;
import tw.linsy.aelorn.mythiccore.api.ElementProfile;
import tw.linsy.aelorn.mythiccore.api.MythicCoreApi;
import tw.linsy.aelorn.mythiccore.api.PlayerClassState;
import tw.linsy.aelorn.mythiccore.api.SkillCastResult;
import tw.linsy.aelorn.mythiccore.api.SkillProfile;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;
import tw.linsy.aelorn.mythiccore.api.TradeQuote;
import tw.linsy.aelorn.mythiccore.api.combat.AttackCadenceApi;
import tw.linsy.aelorn.mythiccore.api.combat.AttackCadenceProfile;
import tw.linsy.aelorn.mythiccore.api.combat.AttackTimeline;
import tw.linsy.aelorn.mythiccore.core.AttackCadenceService;
import tw.linsy.aelorn.mythiccore.core.CombatEngine;
import tw.linsy.aelorn.mythiccore.core.ItemDataService;
import tw.linsy.aelorn.mythiccore.core.PlayerClassStateService;
import tw.linsy.aelorn.mythiccore.core.StatRegistry;
import tw.linsy.aelorn.mythiccore.core.StatSnapshotService;

public final class MythicCorePlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter, MythicCoreApi, AttackCadenceApi {
   private static final String RPG_ABILITY_EVENT = "com.xuzhihuanjing.rpgcore.api.event.RpgAbilityCastEvent";
   private final StatRegistry statRegistry = new StatRegistry();
   private ItemDataService itemDataService;
   private PlayerClassStateService classStateService;
   private StatSnapshotService snapshotService;
   private CombatEngine combatEngine;
   private AttackCadenceService attackCadenceService;
   private volatile Map<String, ElementProfile> elements = Map.of();
   private volatile Map<String, ClassProfile> classProfiles = Map.of();
   private volatile Map<String, List<ClassStageProfile>> classStages = Map.of();
   private volatile Map<String, SkillProfile> skillProfiles = Map.of();
   private volatile Map<String, Double> itemTypeScalars = Map.of();
   private volatile Map<String, Double> itemStatSoftCaps = Map.of();
   private volatile Map<String, Map<String, Double>> classItemScalars = Map.of();
   private volatile Map<String, Double> tierValueBase = Map.of();
   private volatile boolean rpgAbilityEventHooked;
   private volatile int maxItemLevel = 1000;
   private volatile double elementSoftCapDefault = (double)120.0F;
   private volatile double itemDefaultSoftCap = (double)80.0F;
   private volatile double itemLevelSoftCapGrowth = 1.6;
   private volatile double itemValueLevelFactor = 0.065;
   private volatile double itemValueQualityFactor = 0.012;
   private volatile double itemValueScoreFactor = 0.45;
   private volatile double itemValueSellRate = 0.36;
   private volatile double itemValueMinBuy = (double)5.0F;
   private volatile double itemValueMaxBuy = (double)1.0E7F;
   private volatile String itemValueCurrency = "coins";

   public MythicCorePlugin() {
   }

   public void onEnable() {
      this.saveDefaultConfig();
      this.mergeBundledDefaults();
      this.itemDataService = new ItemDataService(this, this.statRegistry);
      this.classStateService = new PlayerClassStateService(this, this::classStageAtLevel);
      this.snapshotService = new StatSnapshotService(this.statRegistry, this.itemDataService, this.classStateService);
      this.combatEngine = new CombatEngine(this.snapshotService, this.itemDataService, this.classStateService, this::elementProfiles);
      this.attackCadenceService = new AttackCadenceService(this);
      this.reloadCore();
      Bukkit.getPluginManager().registerEvents(this, this);
      this.tryHookRpgAbilityEvent();
      Collection var10000 = Bukkit.getOnlinePlayers();
      PlayerClassStateService var10001 = this.classStateService;
      Objects.requireNonNull(var10001);
      var10000.forEach(var10001::loadProfileAsync);
      this.getServer().getServicesManager().register(MythicCoreApi.class, this, this, ServicePriority.Highest);
      this.getServer().getServicesManager().register(AttackCadenceApi.class, this, this, ServicePriority.Highest);
      this.getCommand("mythiccore").setExecutor(this);
      this.getCommand("mythiccore").setTabCompleter(this);
      int var1 = this.runSelfTest(Bukkit.getConsoleSender());
      if (var1 == 0) {
         this.getLogger().info("MythicCore 啟動自我檢查通過。");
      } else {
         this.getLogger().severe("MythicCore 啟動自我檢查發現 " + var1 + " 項問題。");
      }

      Logger var2 = this.getLogger();
      int var3 = this.statRegistry.knownStats().size();
      var2.info("MythicCore enabled with " + var3 + " known stats and " + this.attackCadenceProfiles().size() + " attack cadences.");
   }

   public void onDisable() {
      this.getServer().getServicesManager().unregister(MythicCoreApi.class, this);
      this.getServer().getServicesManager().unregister(AttackCadenceApi.class, this);
      if (this.classStateService != null) {
         this.classStateService.clear();
      }

      if (this.combatEngine != null) {
         this.combatEngine.clearCooldowns();
      }

      if (this.snapshotService != null) {
         this.snapshotService.clearCache();
      }

   }

   private void mergeBundledDefaults() {
      File var1 = new File(this.getDataFolder(), "config.yml");

      try {
         InputStream var2 = this.getResource("config.yml");

         label68: {
            try {
               if (var2 != null && var1.isFile()) {
                  YamlConfiguration var3 = YamlConfiguration.loadConfiguration(new InputStreamReader(var2, StandardCharsets.UTF_8));
                  YamlConfiguration var4 = YamlConfiguration.loadConfiguration(var1);
                  int var5 = 0;

                  for(Map.Entry var7 : var3.getValues(true).entrySet()) {
                     if (!(var7.getValue() instanceof ConfigurationSection) && !var4.contains((String)var7.getKey())) {
                        var4.set((String)var7.getKey(), var7.getValue());
                        ++var5;
                     }
                  }

                  if (var5 > 0) {
                     var4.save(var1);
                     this.getLogger().info("MythicCore 設定已補入 " + var5 + " 個新版本欄位，既有數值保持不變。");
                  }
                  break label68;
               }
            } catch (Throwable var9) {
               if (var2 != null) {
                  try {
                     var2.close();
                  } catch (Throwable var8) {
                     var9.addSuppressed(var8);
                  }
               }

               throw var9;
            }

            if (var2 != null) {
               var2.close();
            }

            return;
         }

         if (var2 != null) {
            var2.close();
         }

      } catch (IOException var10) {
         throw new IllegalStateException("無法升級 MythicCore config.yml", var10);
      }
   }

   private void reloadCore() {
      this.reloadConfig();
      this.statRegistry.load(this.getConfig());
      this.itemDataService.reload(this.getConfig());
      this.classStateService.reload(this.getConfig());
      this.snapshotService.reload(this.getConfig());
      this.combatEngine.reload(this.getConfig());
      this.attackCadenceService.reload();
      this.maxItemLevel = this.itemDataService.maxItemLevel();
      this.elementSoftCapDefault = Math.max((double)1.0F, this.getConfig().getDouble("balance.elements.default-soft-cap", (double)120.0F));
      this.itemDefaultSoftCap = Math.max((double)1.0F, this.getConfig().getDouble("balance.items.default-stat-soft-cap", (double)80.0F));
      this.itemLevelSoftCapGrowth = Math.max((double)0.0F, this.getConfig().getDouble("balance.items.level-soft-cap-growth", 1.6));
      this.itemValueCurrency = safe(this.getConfig().getString("economy.currency", "coins"));
      this.itemValueLevelFactor = Math.max((double)0.0F, this.getConfig().getDouble("economy.level-factor", 0.065));
      this.itemValueQualityFactor = Math.max((double)0.0F, this.getConfig().getDouble("economy.quality-factor", 0.012));
      this.itemValueScoreFactor = Math.max((double)0.0F, this.getConfig().getDouble("economy.score-factor", 0.45));
      this.itemValueSellRate = clamp01(this.getConfig().getDouble("economy.sell-rate", 0.36));
      this.itemValueMinBuy = Math.max((double)0.0F, this.getConfig().getDouble("economy.min-buy", (double)5.0F));
      this.itemValueMaxBuy = Math.max(this.itemValueMinBuy, this.getConfig().getDouble("economy.max-buy", (double)1.0E7F));
      this.loadElements();
      this.loadClassProfiles();
      this.loadSkillProfiles();
      this.importRpgCoreData();
      this.loadItemBalance();
      this.loadEconomyTiers();
   }

   private void loadElements() {
      LinkedHashMap var1 = new LinkedHashMap();
      ConfigurationSection var2 = this.getConfig().getConfigurationSection("balance.elements.types");
      if (var2 != null) {
         for(String var4 : var2.getKeys(false)) {
            ConfigurationSection var5 = var2.getConfigurationSection(var4);
            if (var5 != null) {
               String var6 = StatSnapshot.normalize(var4);
               List var7 = this.normalizedList(var5, "damage-stats");
               if (var7.isEmpty()) {
                  var7 = List.of(var6 + "_DAMAGE");
               }

               List var8 = this.normalizedList(var5, "resistance-stats");
               if (var8.isEmpty()) {
                  var8 = List.of(var6 + "_RESISTANCE", "ELEMENTAL_RESISTANCE");
               }

               ElementProfile var9 = new ElementProfile(var6, var5.getString("display-name", humanize(var6)), var7, var8, var5.getDouble("damage-multiplier", (double)1.0F), var5.getDouble("soft-cap", this.elementSoftCapDefault), var5.getDouble("skill-multiplier", (double)1.0F));
               var1.put(var9.id(), var9);
               this.statRegistry.registerKnownStats(var9.damageStats());
               this.statRegistry.registerKnownStats(var9.resistanceStats());
            }
         }
      }

      if (var1.isEmpty()) {
         for(String var11 : List.of("FIRE", "ICE", "THUNDER", "WIND", "EARTH", "WATER", "DARKNESS", "LIGHTNESS", "ARCANE", "NATURE")) {
            ElementProfile var12 = new ElementProfile(var11, humanize(var11), List.of(var11 + "_DAMAGE"), List.of(var11 + "_RESISTANCE", "ELEMENTAL_RESISTANCE"), (double)1.0F, this.elementSoftCapDefault, (double)1.0F);
            var1.put(var12.id(), var12);
            this.statRegistry.registerKnownStats(var12.damageStats());
            this.statRegistry.registerKnownStats(var12.resistanceStats());
         }
      }

      this.elements = Map.copyOf(var1);
   }

   private void loadClassProfiles() {
      LinkedHashMap var1 = new LinkedHashMap();
      LinkedHashMap var2 = new LinkedHashMap();
      ConfigurationSection var3 = this.getConfig().getConfigurationSection("classes");
      if (var3 != null) {
         for(String var5 : var3.getKeys(false)) {
            ConfigurationSection var6 = var3.getConfigurationSection(var5);
            if (var6 != null) {
               Map var7 = this.readStatMap(var6.getConfigurationSection("base-stats"), true);
               Map var8 = this.readStatMap(var6.getConfigurationSection("scaling"), true);
               Map var9 = this.readIntegerMap(var6.getConfigurationSection("ratings"));
               List var10 = this.readArchetypes(var6.getConfigurationSection("archetypes"));
               ClassProfile var11 = new ClassProfile(var5, var6.getString("display-name", humanize(var5)), var6.getString("second-job-name", ""), this.readOrderedStringList(var6, "advanced-jobs"), var6.getString("role", ""), var6.getString("weapon", ""), var7, var8, var9, var10);
               var1.put(var11.id(), var11);
               var2.put(var11.id(), this.readClassStages(var6.getConfigurationSection("stages")));
               this.statRegistry.registerKnownStats(var7.keySet());
               this.statRegistry.registerKnownStats(var8.keySet());
            }
         }
      }

      this.classProfiles = Map.copyOf(var1);
      this.classStages = Map.copyOf(var2);
   }

   private List<ClassStageProfile> readClassStages(ConfigurationSection var1) {
      if (var1 == null) {
         return List.of();
      } else {
         ArrayList var2 = new ArrayList();

         for(String var4 : var1.getKeys(false)) {
            ConfigurationSection var5 = var1.getConfigurationSection(var4);
            if (var5 != null) {
               LinkedHashMap var6 = new LinkedHashMap();

               for(String var8 : List.of("health", "mana", "attack", "defense", "resistance", "speed", "basic-attack", "damage-taken")) {
                  var6.put(StatSnapshot.normalize(var8), Math.max(0.1, Math.min((double)3.0F, var5.getDouble(var8 + "-multiplier", (double)1.0F))));
               }

               var2.add(new ClassStageProfile(var4, stripFormatting(var5.getString("display-name", humanize(var4))), var5.getInt("minimum-level", 1), var6, var5.getInt("bonus-ability-points", 0)));
            }
         }

         var2.sort(Comparator.comparingInt(ClassStageProfile::minimumLevel));
         return List.copyOf(var2);
      }
   }

   private List<ArchetypeProfile> readArchetypes(ConfigurationSection var1) {
      if (var1 == null) {
         return List.of();
      } else {
         ArrayList var2 = new ArrayList();

         for(String var4 : var1.getKeys(false)) {
            ConfigurationSection var5 = var1.getConfigurationSection(var4);
            if (var5 != null) {
               var2.add(new ArchetypeProfile(var4, var5.getString("display-name", humanize(var4)), var5.getString("role", ""), var5.getString("description", "")));
            }
         }

         return var2;
      }
   }

   private void loadSkillProfiles() {
      LinkedHashMap var1 = new LinkedHashMap();
      ConfigurationSection var2 = this.getConfig().getConfigurationSection("skills");
      if (var2 == null) {
         var2 = this.getConfig().getConfigurationSection("abilities");
      }

      if (var2 != null) {
         for(String var4 : var2.getKeys(false)) {
            ConfigurationSection var5 = var2.getConfigurationSection(var4);
            if (var5 != null) {
               SkillProfile var6 = new SkillProfile(var4, var5.getString("class", ""), var5.getString("archetype", var5.getString("branch", "")), var5.getString("display-name", humanize(var4)), var5.getString("element", "PHYSICAL"), var5.getString("effect", ""), this.readCombo(var5), var5.getInt("required-level", var5.getInt("level", 1)), var5.getDouble("mana", var5.getDouble("mana-cost", (double)0.0F)), var5.getDouble("cooldown-seconds", var5.getDouble("cooldown", (double)0.0F)), var5.getDouble("coefficient", (double)0.0F), var5.getDouble("flat-power", var5.getDouble("flat", (double)0.0F)), var5.getDouble("radius", (double)0.0F), var5.getDouble("range", (double)0.0F), var5.getInt("duration-ticks", var5.getInt("duration", 0)), var5.getInt("max-hits", var5.getInt("hits", 1)), var5.getString("description", ""));
               var1.put(var6.id(), var6);
            }
         }
      }

      this.skillProfiles = new LinkedHashMap(var1);
   }

   private void importRpgCoreData() {
      if (this.getConfig().getBoolean("integration.rpgcore.enabled", true)) {
         String var1 = this.getConfig().getString("integration.rpgcore.data-folder", "RPGCore");
         File var2 = new File(this.getDataFolder().getParentFile(), var1 != null && !var1.isBlank() ? var1 : "RPGCore");
         if (var2.isDirectory()) {
            if (this.getConfig().getBoolean("integration.rpgcore.import-classes", true)) {
               this.importRpgCoreClasses(new File(var2, "classes.yml"));
            }

            if (this.getConfig().getBoolean("integration.rpgcore.import-abilities", true)) {
               this.importRpgCoreAbilities(new File(var2, "abilities.yml"));
            }

         }
      }
   }

   private void tryHookRpgAbilityEvent() {
      if (!this.rpgAbilityEventHooked && this.getConfig().getBoolean("integration.rpgcore.player-profile-sync", true)) {
         Plugin var1 = Bukkit.getPluginManager().getPlugin("RPGCore");
         if (var1 != null) {
            try {
               Class var2 = Class.forName("com.xuzhihuanjing.rpgcore.api.event.RpgAbilityCastEvent", false, var1.getClass().getClassLoader()).asSubclass(Event.class);
               Listener var3 = new Listener() {
               };
               Bukkit.getPluginManager().registerEvent(var2, var3, EventPriority.MONITOR, (var1x, var2x) -> this.classStateService.captureFromEvent(var2x), this, false);
               this.rpgAbilityEventHooked = true;
               this.getLogger().info("已掛接 RPGCore 即時角色事件，職業與等級限制將使用目前角色。");
            } catch (RuntimeException | ReflectiveOperationException | LinkageError var4) {
               this.getLogger().warning("RPGCore 即時角色事件尚未可用，將使用安全的角色資料快取：" + var4.getClass().getSimpleName());
            }

         }
      }
   }

   private void importRpgCoreClasses(File var1) {
      if (var1.isFile()) {
         YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);
         ConfigurationSection var3 = var2.getConfigurationSection("classes");
         if (var3 != null) {
            LinkedHashMap var4 = new LinkedHashMap(this.classProfiles);
            LinkedHashMap var5 = new LinkedHashMap(this.classStages);

            for(String var7 : var3.getKeys(false)) {
               ConfigurationSection var8 = var3.getConfigurationSection(var7);
               if (var8 != null) {
                  String var9 = StatRegistry.normalizeClassId(var7);
                  ClassProfile var10 = (ClassProfile)var4.get(var9);
                  Map var11 = this.readStatMap(var8.getConfigurationSection("base-stats"), true);
                  ConfigurationSection var12 = var8.getConfigurationSection("base-stats");
                  if (var12 != null && var12.contains("speed")) {
                     double var13 = var12.getDouble("speed", (double)100.0F);
                     var11.put("MOVEMENT_SPEED", var13 > (double)2.0F ? Math.max(-0.8, Math.min((double)1.5F, (var13 - (double)100.0F) / (double)100.0F)) : var13);
                  }

                  Map var16 = this.readIntegerMap(var8.getConfigurationSection("ratings"));
                  List var14 = this.readArchetypes(var8.getConfigurationSection("archetypes"));
                  ClassProfile var15 = new ClassProfile(var9, stripFormatting(var8.getString("display-name", var10 == null ? humanize(var9) : var10.displayName())), var10 == null ? "" : var10.secondJobName(), var10 == null ? List.of() : var10.advancedJobs(), var8.getString("role", var10 == null ? "" : var10.role()), var8.getString("weapon", var10 == null ? "" : var10.weapon()), var11.isEmpty() && var10 != null ? var10.baseStats() : var11, var10 == null ? Map.of() : var10.scaling(), var16.isEmpty() && var10 != null ? var10.ratings() : var16, var14.isEmpty() && var10 != null ? var10.archetypes() : var14);
                  var4.put(var9, var15);
                  var5.put(var9, this.mergeClassStages((List)var5.getOrDefault(var9, List.of()), this.readClassStages(var8.getConfigurationSection("stages"))));
                  this.statRegistry.registerKnownStats(var15.baseStats().keySet());
               }
            }

            this.classProfiles = Map.copyOf(var4);
            this.classStages = Map.copyOf(var5);
         }
      }
   }

   private List<ClassStageProfile> mergeClassStages(List<ClassStageProfile> var1, List<ClassStageProfile> var2) {
      if (var2.isEmpty()) {
         return var1;
      } else if (!var1.isEmpty() && this.getConfig().getBoolean("integration.rpgcore.prefer-mythiccore-stage-names", true)) {
         ArrayList var3 = new ArrayList();
         int var4 = Math.max(var1.size(), var2.size());

         for(int var5 = 0; var5 < var4; ++var5) {
            ClassStageProfile var6 = var5 < var1.size() ? (ClassStageProfile)var1.get(var5) : (ClassStageProfile)var2.get(var5);
            ClassStageProfile var7 = var5 < var2.size() ? (ClassStageProfile)var2.get(var5) : (ClassStageProfile)var1.get(var5);
            var3.add(new ClassStageProfile(var6.id(), var6.displayName(), var7.minimumLevel(), var7.multipliers(), var7.bonusAbilityPoints()));
         }

         var3.sort(Comparator.comparingInt(ClassStageProfile::minimumLevel));
         return List.copyOf(var3);
      } else {
         return var2;
      }
   }

   private void importRpgCoreAbilities(File var1) {
      if (var1.isFile()) {
         YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);
         ConfigurationSection var3 = var2.getConfigurationSection("abilities");
         if (var3 != null) {
            double var4 = Math.max(0.1, Math.min((double)1.0F, this.getConfig().getDouble("integration.rpgcore.ability-coefficient-scale", 0.55)));
            LinkedHashMap var6 = new LinkedHashMap(this.skillProfiles);

            for(String var8 : var3.getKeys(false)) {
               ConfigurationSection var9 = var3.getConfigurationSection(var8);
               String var10 = StatSnapshot.normalize(var8);
               if (var9 != null && !var6.containsKey(var10)) {
                  String var11 = var9.getString("effect", "");
                  String var12 = StatRegistry.normalizeClassId(var9.getString("class", ""));
                  var6.put(var10, new SkillProfile(var10, var12, this.inferArchetype(var12, var11), stripFormatting(var9.getString("display-name", humanize(var10))), this.inferElement(var12, var11), var11, this.readCombo(var9), var9.getInt("required-level", 1), var9.getDouble("mana", (double)0.0F), var9.getDouble("cooldown-seconds", (double)0.0F), var9.getDouble("coefficient", (double)0.0F) * var4, var9.getDouble("flat-power", (double)0.0F) * var4, var9.getDouble("radius", (double)0.0F), var9.getDouble("range", (double)0.0F), var9.getInt("duration-ticks", 0), this.inferredHitCount(var11), var9.getString("description", "")));
               }
            }

            this.skillProfiles = var6;
         }
      }
   }

   private String inferArchetype(String var1, String var2) {
      String var3 = StatSnapshot.normalize(var2);
      String var10000;
      switch (StatRegistry.normalizeClassId(var1)) {
         case "VANGUARD" -> var10000 = var3.contains("BULWARK") ? "BULWARK" : (var3.contains("CRY") ? "COMMANDER" : "ONSLAUGHT");
         case "ARCANIST" -> var10000 = var3.contains("RESTORE") ? "MYSTIC" : (var3.contains("BLINK") ? "CHRONOMANCY" : "ELEMENTAL");
         case "RANGER" -> var10000 = !var3.contains("GUARD") && !var3.contains("BACKSTEP") ? (var3.contains("EXPLOSIVE") ? "TRAPPER" : "MARKSMAN") : "WINDRUNNER";
         case "SHADOWBLADE" -> var10000 = var3.contains("SMOKE") ? "VENOM" : (var3.contains("DASH") ? "MIRAGE" : "DUELIST");
         case "WARDEN" -> var10000 = var3.contains("RENEWAL") ? "RESONANCE" : (var3.contains("FIELD") ? "GUARDIAN" : "TEMPEST");
         default -> var10000 = "";
      }

      return var10000;
   }

   private String inferElement(String var1, String var2) {
      String var3 = StatSnapshot.normalize(var2);
      if (var3.contains("FROST")) {
         return "ICE";
      } else if (!var3.contains("EXPLOSIVE") && !var3.contains("WAR_CRY")) {
         if (!var3.contains("STORM") && !var3.contains("LIGHTNING")) {
            if (!var3.contains("RESTORE") && !var3.contains("RENEWAL")) {
               if (!var3.contains("SHADOW") && !var3.contains("SMOKE")) {
                  if (!var3.contains("GROUND") && !var3.contains("FIELD")) {
                     return StatRegistry.normalizeClassId(var1).equals("ARCANIST") ? "ARCANE" : "PHYSICAL";
                  } else {
                     return "EARTH";
                  }
               } else {
                  return "DARKNESS";
               }
            } else {
               return "LIGHTNESS";
            }
         } else {
            return "THUNDER";
         }
      } else {
         return "FIRE";
      }
   }

   private int inferredHitCount(String var1) {
      String var2 = StatSnapshot.normalize(var1);
      if (!var2.contains("MULTI_HIT") && !var2.contains("RAPID_VOLLEY")) {
         if (var2.contains("FIELD")) {
            return 6;
         } else {
            return !var2.contains("NOVA") && !var2.contains("SLAM") && !var2.contains("CRY") ? 1 : 4;
         }
      } else {
         return 5;
      }
   }

   private void loadItemBalance() {
      LinkedHashMap var1 = new LinkedHashMap();
      ConfigurationSection var2 = this.getConfig().getConfigurationSection("balance.items.type-scalar");
      if (var2 != null) {
         for(String var4 : var2.getKeys(false)) {
            var1.put(StatSnapshot.normalize(var4), Math.max(0.1, Math.min((double)3.0F, var2.getDouble(var4, (double)1.0F))));
         }
      }

      LinkedHashMap var13 = new LinkedHashMap();
      ConfigurationSection var14 = this.getConfig().getConfigurationSection("balance.items.stat-soft-caps");
      if (var14 != null) {
         for(String var6 : var14.getKeys(false)) {
            String var7 = this.statRegistry.canonical(var6);
            var13.put(var7, Math.max((double)1.0F, var14.getDouble(var6, this.itemDefaultSoftCap)));
            this.statRegistry.registerKnownStat(var7);
         }
      }

      LinkedHashMap var15 = new LinkedHashMap();
      ConfigurationSection var16 = this.getConfig().getConfigurationSection("balance.items.class-scalar");
      if (var16 != null) {
         for(String var8 : var16.getKeys(false)) {
            ConfigurationSection var9 = var16.getConfigurationSection(var8);
            if (var9 != null) {
               LinkedHashMap var10 = new LinkedHashMap();

               for(String var12 : var9.getKeys(false)) {
                  var10.put(StatSnapshot.normalize(var12), Math.max(0.1, Math.min((double)3.0F, var9.getDouble(var12, (double)1.0F))));
               }

               var15.put(StatRegistry.normalizeClassId(var8), Map.copyOf(var10));
            }
         }
      }

      this.itemTypeScalars = Map.copyOf(var1);
      this.itemStatSoftCaps = Map.copyOf(var13);
      this.classItemScalars = Map.copyOf(var15);
   }

   private void loadEconomyTiers() {
      LinkedHashMap var1 = new LinkedHashMap();
      ConfigurationSection var2 = this.getConfig().getConfigurationSection("economy.tiers");
      if (var2 != null) {
         for(String var4 : var2.getKeys(false)) {
            var1.put(StatSnapshot.normalize(var4), Math.max((double)0.0F, var2.getDouble(var4)));
         }
      }

      if (var1.isEmpty()) {
         var1.put("STARTER", (double)0.0F);
         var1.put("COMMON", (double)20.0F);
         var1.put("RARE", (double)70.0F);
         var1.put("EPIC", (double)180.0F);
         var1.put("LEGENDARY", (double)520.0F);
         var1.put("VAST", (double)1400.0F);
      }

      this.tierValueBase = Map.copyOf(var1);
   }

   public void registerKnownStats(Iterable<String> var1) {
      this.statRegistry.registerKnownStats(var1);
   }

   public void registerSetBonuses(Map<String, Map<Integer, Map<String, Double>>> var1) {
      this.statRegistry.registerSetBonuses(var1);
   }

   public void writeItemData(ItemMeta var1, String var2, String var3, String var4, int var5, Map<String, Double> var6) {
      this.itemDataService.writeItemData(var1, var2, var3, var4, var5, var6);
   }

   public void writeItemTags(ItemMeta var1, Map<String, String> var2) {
      this.itemDataService.writeItemTags(var1, var2);
   }

   public Map<String, Double> readItemStats(ItemStack var1) {
      return this.itemDataService.readItemStats(var1);
   }

   public String readItemTag(ItemStack var1, String var2) {
      return this.itemDataService.readItemTag(var1, var2);
   }

   public String readItemId(ItemStack var1) {
      return this.itemDataService.readItemId(var1);
   }

   public String readItemType(ItemStack var1) {
      return this.itemDataService.readItemType(var1);
   }

   public int readItemLevel(ItemStack var1) {
      return this.itemDataService.readItemLevel(var1);
   }

   public StatSnapshot snapshot(LivingEntity var1) {
      return this.snapshotService.snapshot(var1);
   }

   public double calculateAttackDamage(LivingEntity var1, LivingEntity var2, double var3) {
      return this.combatEngine.calculateAttackDamage(var1, var2, var3);
   }

   public Map<String, ElementProfile> elementProfiles() {
      return Collections.unmodifiableMap(this.elements);
   }

   public Map<String, ClassProfile> classProfiles() {
      return Collections.unmodifiableMap(this.classProfiles);
   }

   public ClassProfile classProfile(String var1) {
      return (ClassProfile)this.classProfiles.get(StatRegistry.normalizeClassId(var1));
   }

   public Map<String, List<ClassStageProfile>> classStages() {
      LinkedHashMap var1 = new LinkedHashMap();
      this.classStages.forEach((var1x, var2) -> var1.put(var1x, List.copyOf(var2)));
      return Collections.unmodifiableMap(var1);
   }

   public List<ClassStageProfile> classStages(String var1) {
      return (List)this.classStages.getOrDefault(StatRegistry.normalizeClassId(var1), List.of());
   }

   public ClassStageProfile classStageAtLevel(String var1, int var2) {
      int var3 = Math.max(1, var2);
      ClassStageProfile var4 = null;

      for(ClassStageProfile var6 : this.classStages(var1)) {
         if (var6.minimumLevel() <= var3) {
            var4 = var6;
         }
      }

      return var4;
   }

   public PlayerClassState playerClassState(UUID var1) {
      return this.classStateService.state(var1);
   }

   public List<SkillProfile> skillProfiles() {
      return List.copyOf(this.skillProfiles.values());
   }

   public SkillProfile skillProfile(String var1) {
      return (SkillProfile)this.skillProfiles.get(StatSnapshot.normalize(var1));
   }

   public List<SkillProfile> skillsForClass(String var1) {
      String var2 = StatRegistry.normalizeClassId(var1);
      return this.skillProfiles.values().stream().filter((var1x) -> var1x.classId().equals(var2)).toList();
   }

   public SkillProfile matchSkillCombo(String var1, String var2, int var3, List<String> var4) {
      if (var4 != null && !var4.isEmpty()) {
         String var5 = StatRegistry.normalizeClassId(var1);
         String var6 = StatSnapshot.normalize(var2);
         List var7 = var4.stream().filter((var0) -> var0 != null && !var0.isBlank()).map(StatSnapshot::normalize).toList();
         return (SkillProfile)this.skillProfiles.values().stream().filter((var1x) -> var1x.classId().equals(var5)).filter((var1x) -> var6.isBlank() || var1x.archetypeId().isBlank() || var1x.archetypeId().equals(var6)).filter((var1x) -> var1x.requiredLevel() <= Math.max(1, var3)).filter((var2x) -> this.comboEndsWith(var7, var2x.combo())).sorted(Comparator.comparingInt(SkillProfile::requiredLevel).reversed().thenComparing(Comparator.comparingInt((var0) -> var0.combo().size()).reversed()).thenComparing(SkillProfile::id)).findFirst().orElse((Object)null);
      } else {
         return null;
      }
   }

   public SkillCastResult calculateSkill(String var1, int var2, int var3, StatSnapshot var4, StatSnapshot var5) {
      SkillProfile var6 = this.skillProfile(var1);
      if (var6 == null) {
         return SkillCastResult.denied(var1, "找不到技能");
      } else {
         int var7 = Math.max(1, Math.min(this.maxItemLevel, var2));
         if (var7 < var6.requiredLevel()) {
            return SkillCastResult.denied(var6.id(), "角色等級不足");
         } else {
            StatSnapshot var8 = var4 == null ? new StatSnapshot(Map.of()) : var4;
            StatSnapshot var9 = var5 == null ? new StatSnapshot(Map.of()) : var5;
            ElementProfile var10 = (ElementProfile)this.elements.get(var6.elementId());
            if (var10 == null) {
               var10 = (ElementProfile)this.elements.get("PHYSICAL");
            }

            double var11 = var6.elementId().equals("PHYSICAL") ? var8.get("ATTACK_DAMAGE") + var8.get("WEAPON_DAMAGE") + var8.get("PHYSICAL_DAMAGE") * 0.45 : var8.get("MAGIC_DAMAGE") + var8.get("ABILITY_DAMAGE") + var8.get("INTELLIGENCE") * 0.2;
            double var13 = var8.get("SKILL_DAMAGE") + var8.get("ABILITY_DAMAGE") * 0.55;
            double var15 = var10 == null ? (double)1.0F : var10.skillMultiplier() * ((double)1.0F + this.combatEngine.balancedElementDamage(var8, var10.id()) / Math.max((double)180.0F, var10.softCap() * (double)3.0F));
            ClassStageProfile var17 = this.classStageAtLevel(var6.classId(), var7);
            if (var17 != null) {
               var11 *= var17.multiplier("ATTACK");
            }

            double var18 = var6.balancedPower(var7, var3, var11, var13, var15);
            CombatEngine.Settings var20 = this.combatEngine.settings();
            if (var10 != null) {
               double var21 = (double)0.0F;

               for(String var24 : var10.resistanceStats()) {
                  var21 += var9.get(var24);
               }

               double var31 = var8.get("ELEMENTAL_PENETRATION");
               double var25 = Math.max((double)0.0F, var21 - var31);
               double var27 = Math.min(var20.elementResistanceCap(), var25 / (var25 + var20.resistanceK()));
               var18 *= (double)1.0F - var27;
            }

            int var30 = Math.max(1, Math.min(12, var6.maxHits()));
            double var22 = Math.min(var20.skillMaxTotalDamage(), var18 * (double)var30);
            var18 = var22 / (double)var30;
            double var32 = Math.min(0.6, Math.max((double)0.0F, var8.get("MANA_COST")) / (double)100.0F);
            double var26 = Math.min(0.55, Math.max((double)0.0F, var8.get("COOLDOWN_REDUCTION")) / (double)100.0F);
            return new SkillCastResult(true, "", var6.id(), var6.elementId(), var3, var30, var18, var22, var6.manaCost() * ((double)1.0F - var32), var6.cooldownSeconds() * ((double)1.0F - var26));
         }
      }
   }

   private boolean comboEndsWith(List<String> var1, List<String> var2) {
      if (var2 != null && !var2.isEmpty() && var1.size() >= var2.size()) {
         int var3 = var1.size() - var2.size();

         for(int var4 = 0; var4 < var2.size(); ++var4) {
            if (!StatSnapshot.normalize((String)var2.get(var4)).equals(var1.get(var3 + var4))) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public double balancedElementDamage(StatSnapshot var1, String var2) {
      return this.combatEngine.balancedElementDamage(var1, var2);
   }

   public Map<String, Double> normalizeItemStats(Map<String, Double> var1, String var2, String var3, int var4) {
      LinkedHashMap var5 = new LinkedHashMap();
      if (var1 != null && !var1.isEmpty()) {
         int var6 = Math.max(1, Math.min(this.maxItemLevel, var4));
         double var7 = (Double)this.itemTypeScalars.getOrDefault(StatSnapshot.normalize(var2), (double)1.0F);
         String var9 = StatRegistry.normalizeClassId(var3);

         for(Map.Entry var11 : var1.entrySet()) {
            if (var11.getKey() != null && var11.getValue() != null) {
               String var12 = this.statRegistry.canonical((String)var11.getKey());
               double var13 = (Double)var11.getValue();
               if (var12.equals("CRITICAL_STRIKE_POWER") || var12.equals("SKILL_CRITICAL_STRIKE_POWER")) {
                  var13 = var13 > (double)100.0F ? var13 - (double)100.0F : var13;
               }

               if (this.scalesAsItemPower(var12)) {
                  var13 *= var7;
                  var13 *= this.classItemScalar(var9, var12);
                  var13 = signedSoftCap(var13, this.itemSoftCap(var12, var6));
               }

               var13 = this.statRegistry.sanitize(var12, var13);
               if (Math.abs(var13) > 1.0E-6) {
                  var5.merge(var12, var13, (var2x, var3x) -> this.statRegistry.sanitize(var12, var2x + var3x));
                  this.statRegistry.registerKnownStat(var12);
               }
            }
         }

         return var5;
      } else {
         return var5;
      }
   }

   public TradeQuote tradeQuote(String var1, int var2, int var3, double var4) {
      double var6 = this.itemValue(var1, var2, var3, var4, false);
      double var8 = this.itemValue(var1, var2, var3, var4, true);
      return new TradeQuote(var6, var8, this.itemValueCurrency, "tier-level-quality-score-v1");
   }

   public double itemValue(String var1, int var2, int var3, double var4, boolean var6) {
      String var7 = StatRegistry.normalizeTierId(var1);
      double var8 = (Double)this.tierValueBase.getOrDefault(var7, (Double)this.tierValueBase.getOrDefault("COMMON", (double)20.0F));
      if (var8 <= (double)0.0F) {
         return (double)0.0F;
      } else {
         double var10 = (double)Math.max(1, Math.min(this.maxItemLevel, var2));
         double var12 = (double)Math.max(1, Math.min(100, var3));
         double var14 = Math.max((double)0.0F, Math.min((double)1000000.0F, var4));
         double var16 = (double)1.0F + Math.pow(var10, 0.82) * this.itemValueLevelFactor;
         double var18 = 0.72 + var12 * this.itemValueQualityFactor;
         double var20 = (double)1.0F + Math.sqrt(var14) * this.itemValueScoreFactor / (double)10.0F;
         double var22 = Math.max(this.itemValueMinBuy, Math.min(this.itemValueMaxBuy, var8 * var16 * var18 * var20));
         return (double)Math.round((var6 ? var22 * this.itemValueSellRate : var22) * (double)100.0F) / (double)100.0F;
      }
   }

   public Map<String, AttackCadenceProfile> attackCadenceProfiles() {
      return this.attackCadenceService.profiles();
   }

   public AttackCadenceProfile attackCadenceProfile(String var1) {
      return this.attackCadenceService.profile(var1);
   }

   public AttackTimeline calculateAttackTimeline(String var1, int var2, double var3) {
      return this.attackCadenceService.timeline(var1, var2, var3);
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageByEntityEvent var1) {
      this.combatEngine.handleDamage(var1);
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      this.classStateService.loadProfileAsync(var1.getPlayer());
      if (this.getConfig().getBoolean("profile.welcome-summary", true) && var1.getPlayer().isOp()) {
         var1.getPlayer().sendMessage(Component.text("MythicCore 已載入：遞減收益公式、套裝、詞綴、觸發能力待命。", NamedTextColor.DARK_AQUA));
      }

   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      UUID var2 = var1.getPlayer().getUniqueId();
      this.classStateService.remove(var2);
      this.combatEngine.removeCooldown(var2);
      this.snapshotService.invalidate(var2);
   }

   @EventHandler
   public void onPluginEnable(PluginEnableEvent var1) {
      if (var1.getPlugin().getName().equalsIgnoreCase("RPGCore")) {
         this.tryHookRpgAbilityEvent();
         Collection var10000 = Bukkit.getOnlinePlayers();
         PlayerClassStateService var10001 = this.classStateService;
         Objects.requireNonNull(var10001);
         var10000.forEach(var10001::loadProfileAsync);
      }

   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      switch (var4.length > 0 ? var4[0].toLowerCase(Locale.ROOT) : "") {
         case "reload":
            if (!var1.hasPermission("mythiccore.admin")) {
               var1.sendMessage(Component.text("你沒有權限重載 MythicCore。", NamedTextColor.RED));
               return true;
            }

            this.reloadCore();
            var1.sendMessage(Component.text("MythicCore 已重新載入。", NamedTextColor.GREEN));
            return true;
         case "stats":
            Player var41;
            if (var4.length > 1) {
               var41 = Bukkit.getPlayerExact(var4[1]);
            } else if (var1 instanceof Player) {
               Player var28 = (Player)var1;
               var41 = var28;
            } else {
               var41 = null;
            }

            Player var21 = var41;
            if (var21 == null) {
               var1.sendMessage(Component.text("找不到玩家。", NamedTextColor.RED));
               return true;
            }

            StatSnapshot var29 = this.snapshot(var21);
            var1.sendMessage(Component.text("MythicCore 屬性快照：" + var21.getName(), NamedTextColor.AQUA));
            PlayerClassState var35 = this.playerClassState(var21.getUniqueId());
            if (var35 != null) {
               String var48 = var35.characterName();
               var1.sendMessage(Component.text("角色：" + var48 + " | " + var35.classId() + " | " + (var35.stageDisplayName().isBlank() ? "未轉職" : var35.stageDisplayName()) + " | Lv." + var35.level(), NamedTextColor.GOLD));
            }

            var29.asMap().forEach((var1x, var2x) -> {
               if (Math.abs(var2x) > 1.0E-6) {
                  var1.sendMessage(Component.text(" - " + var1x + ": " + format(var2x), NamedTextColor.GRAY));
               }

            });
            return true;
         case "classes":
            var1.sendMessage(Component.text("MythicCore 職業樹：", NamedTextColor.AQUA));

            for(ClassProfile var27 : this.classProfiles.values()) {
               String var34 = (String)this.classStages(var27.id()).stream().map((var0) -> {
                  String var10000 = var0.displayName();
                  return var10000 + " Lv." + var0.minimumLevel();
               }).collect(Collectors.joining(" -> "));
               String var40 = var27.advancedJobs().isEmpty() ? (String)var27.archetypes().stream().map(ArchetypeProfile::displayName).collect(Collectors.joining(" / ")) : String.join(" / ", var27.advancedJobs());
               var1.sendMessage(Component.text(" - " + var34 + " | 專精：" + var40, NamedTextColor.GRAY));
            }

            return true;
         case "skills":
            String var19 = var4.length > 1 ? StatSnapshot.normalize(var4[1]) : "";
            List var26 = this.skillProfiles.values().stream().filter((var1x) -> var19.isBlank() || var1x.classId().equals(var19)).limit(40L).toList();
            var1.sendMessage(Component.text("MythicCore 技能資料：" + var26.size() + " 筆", NamedTextColor.AQUA));

            for(SkillProfile var39 : var26) {
               String var47 = var39.displayName();
               var1.sendMessage(Component.text(" - " + var47 + " Lv." + var39.requiredLevel() + " " + var39.elementId() + " 係數 " + format(var39.coefficient()) + " 冷卻 " + format(var39.cooldownSeconds()) + "s", NamedTextColor.GRAY));
            }

            return true;
         case "elements":
            var1.sendMessage(Component.text("MythicCore 元素：", NamedTextColor.AQUA));

            for(ElementProfile var25 : this.elements.values()) {
               String var46 = var25.displayName();
               var1.sendMessage(Component.text(" - " + var46 + " 軟上限 " + format(var25.softCap()) + " 倍率 " + format(var25.damageMultiplier()), NamedTextColor.GRAY));
            }

            return true;
         case "cadence":
            String var17 = var4.length > 1 ? var4[1] : "";
            if (!var17.isBlank()) {
               AttackCadenceProfile var24 = this.attackCadenceProfile(var17);
               if (var24 == null) {
                  var1.sendMessage(Component.text("找不到攻擊節奏：" + var17, NamedTextColor.RED));
                  return true;
               }

               double var32 = var4.length > 2 ? parseDouble(var4[2], (double)0.0F) : (double)0.0F;
               AttackTimeline var12 = var24.timeline(1, var32);
               var1.sendMessage(Component.text(var24.displayName() + "（" + var24.id() + "）", NamedTextColor.GOLD));
               int var44 = var12.windupTicks();
               var1.sendMessage(Component.text("前搖 " + var44 + "t | 命中 " + var12.activeTicks() + "t | 收招 " + var12.recoveryTicks() + "t | 合計 " + var12.totalTicks() + "t", NamedTextColor.GRAY));
               String var45 = format(var12.damageMultiplier());
               var1.sendMessage(Component.text("傷害倍率 " + var45 + " | 距離倍率 " + format(var12.rangeMultiplier()) + " | 最大連段 " + var12.maximumComboSteps(), NamedTextColor.GRAY));
               return true;
            }

            var1.sendMessage(Component.text("MythicCore 攻擊節奏：", NamedTextColor.AQUA));

            for(AttackCadenceProfile var31 : this.attackCadenceProfiles().values()) {
               AttackTimeline var38 = var31.timeline(1, (double)0.0F);
               String var43 = var31.displayName();
               var1.sendMessage(Component.text(" - " + var43 + "：" + var38.windupTicks() + "/" + var38.activeTicks() + "/" + var38.recoveryTicks() + " tick", NamedTextColor.GRAY));
            }

            return true;
         case "economy":
            String var16 = var4.length > 1 ? var4[1] : "RARE";
            int var22 = var4.length > 2 ? (int)parseDouble(var4[2], (double)30.0F) : 30;
            int var30 = var4.length > 3 ? (int)parseDouble(var4[3], (double)80.0F) : 80;
            double var37 = var4.length > 4 ? parseDouble(var4[4], (double)120.0F) : (double)120.0F;
            TradeQuote var13 = this.tradeQuote(var16, var22, var30, var37);
            String var42 = StatRegistry.normalizeTierId(var16);
            var1.sendMessage(Component.text("估價：" + var42 + " Lv." + var22 + " 品質 " + var30 + "% 評分 " + format(var37) + " -> 買入 " + format(var13.buyPrice()) + " / 售出 " + format(var13.sellPrice()) + " " + var13.currency(), NamedTextColor.GOLD));
            return true;
         case "combo":
            if (var4.length < 6) {
               var1.sendMessage(Component.text("/" + var3 + " combo <職業> <專精|any> <等級> <RIGHT|LEFT> <RIGHT|LEFT> ...", NamedTextColor.YELLOW));
               return true;
            }

            SkillProfile var15 = this.matchSkillCombo(var4[1], var4[2].equalsIgnoreCase("any") ? "" : var4[2], (int)parseDouble(var4[3], (double)1.0F), List.of(var4).subList(4, var4.length));
            var1.sendMessage(var15 == null ? Component.text("這組輸入沒有符合的技能。", NamedTextColor.RED) : Component.text("連技符合：" + var15.displayName() + "（" + var15.id() + "）", NamedTextColor.GREEN));
            return true;
         case "simulate":
            if (var4.length < 2) {
               var1.sendMessage(Component.text("/" + var3 + " simulate <技能ID> [角色等級] [技能等級]", NamedTextColor.YELLOW));
               return true;
            } else {
               int var14 = var4.length > 2 ? (int)parseDouble(var4[2], (double)30.0F) : 30;
               int var9 = var4.length > 3 ? (int)parseDouble(var4[3], (double)1.0F) : 1;
               StatSnapshot var10000;
               if (var1 instanceof Player) {
                  Player var11 = (Player)var1;
                  var10000 = this.snapshot(var11);
               } else {
                  var10000 = new StatSnapshot(Map.of("ATTACK_DAMAGE", (double)25.0F, "MAGIC_DAMAGE", (double)25.0F, "SKILL_DAMAGE", (double)12.0F));
               }

               StatSnapshot var10 = var10000;
               SkillCastResult var36 = this.calculateSkill(var4[1], var14, var9, var10, new StatSnapshot(Map.of()));
               if (!var36.allowed()) {
                  var1.sendMessage(Component.text("模擬失敗：" + var36.reason(), NamedTextColor.RED));
                  return true;
               }

               String var10001 = format(var36.damagePerHit());
               var1.sendMessage(Component.text("技能模擬：每段 " + var10001 + " × " + var36.hits() + " = " + format(var36.totalDamage()) + "，魔力 " + format(var36.manaCost()) + "，冷卻 " + format(var36.cooldownSeconds()) + " 秒", NamedTextColor.AQUA));
               return true;
            }
         case "selftest":
            int var8 = this.runSelfTest(var1);
            var1.sendMessage(Component.text(var8 == 0 ? "MythicCore 自我檢查通過。" : "MythicCore 自我檢查發現 " + var8 + " 項問題。", var8 == 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
            return true;
         default:
            var1.sendMessage(Component.text("/" + var3 + " stats [玩家] | classes | skills [職業] | elements | cadence | economy | combo | simulate | selftest | reload", NamedTextColor.YELLOW));
            return true;
      }
   }

   private int runSelfTest(CommandSender var1) {
      ArrayList var2 = new ArrayList();
      if (this.classProfiles.size() < 5) {
         var2.add("職業少於 5 種");
      }

      this.classProfiles.keySet().forEach((var2x) -> {
         if (this.classStages(var2x).size() < 3) {
            var2.add(var2x + " 未完成三階轉職");
         }

      });

      for(SkillProfile var4 : this.skillProfiles.values()) {
         if (!this.classProfiles.containsKey(var4.classId())) {
            var2.add(var4.id() + " 的職業不存在");
         }

         if (!this.elements.containsKey(var4.elementId())) {
            var2.add(var4.id() + " 的元素不存在");
         }

         if (var4.combo().isEmpty()) {
            var2.add(var4.id() + " 沒有連技輸入");
         }

         if (var4.coefficient() > 1.65) {
            var2.add(var4.id() + " 的係數過高");
         }
      }

      TradeQuote var10 = this.tradeQuote("COMMON", 20, 70, (double)100.0F);
      if (var10.buyPrice() <= (double)0.0F || var10.sellPrice() >= var10.buyPrice()) {
         var2.add("交易價格公式異常");
      }

      if (this.attackCadenceProfiles().size() < 9) {
         var2.add("攻擊節奏少於 9 種");
      }

      for(AttackCadenceProfile var5 : this.attackCadenceProfiles().values()) {
         AttackTimeline var6 = var5.timeline(1, (double)0.0F);
         AttackTimeline var7 = var5.timeline(1, (double)5000.0F);
         int var8 = Math.max(1, (int)Math.round((double)var5.windupTicks() * var5.minimumTimingScale()));
         int var9 = Math.max(1, (int)Math.round((double)var5.recoveryTicks() * var5.minimumTimingScale()));
         if (var6.totalTicks() < 5 || var6.totalTicks() > 80) {
            var2.add(var5.id() + " 的攻擊時間不合理");
         }

         if (var7.activeTicks() != var6.activeTicks() || var7.windupTicks() < var8 || var7.recoveryTicks() < var9) {
            var2.add(var5.id() + " 的攻速下限失效");
         }
      }

      var2.stream().limit(20L).forEach((var1x) -> var1.sendMessage(Component.text(" - " + var1x, NamedTextColor.RED)));
      int var10001 = this.classProfiles.size();
      var1.sendMessage(Component.text("檢查：職業 " + var10001 + "、技能 " + this.skillProfiles.size() + "、元素 " + this.elements.size() + "、攻擊節奏 " + this.attackCadenceProfiles().size() + "。", NamedTextColor.GRAY));
      return var2.size();
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var4.length == 1) {
         return List.of("stats", "classes", "skills", "elements", "cadence", "economy", "combo", "simulate", "selftest", "reload").stream().filter((var1x) -> var1x.startsWith(var4[0].toLowerCase(Locale.ROOT))).toList();
      } else if (var4.length == 2 && var4[0].equalsIgnoreCase("skills")) {
         return this.classProfiles.keySet().stream().filter((var1x) -> var1x.toLowerCase(Locale.ROOT).startsWith(var4[1].toLowerCase(Locale.ROOT))).toList();
      } else if (var4.length == 2 && var4[0].equalsIgnoreCase("cadence")) {
         String var6 = var4[1].toLowerCase(Locale.ROOT);
         return this.attackCadenceProfiles().keySet().stream().filter((var1x) -> var1x.startsWith(var6)).toList();
      } else if (var4.length != 2 || !var4[0].equalsIgnoreCase("simulate") && !var4[0].equalsIgnoreCase("combo")) {
         return var4.length >= 5 && var4[0].equalsIgnoreCase("combo") ? List.of("RIGHT", "LEFT") : List.of();
      } else {
         String var5 = StatSnapshot.normalize(var4[1]);
         return var4[0].equalsIgnoreCase("simulate") ? this.skillProfiles.keySet().stream().filter((var1x) -> var1x.startsWith(var5)).toList() : this.classProfiles.keySet().stream().filter((var1x) -> var1x.startsWith(var5)).toList();
      }
   }

   private boolean scalesAsItemPower(String var1) {
      String var2 = this.statRegistry.canonical(var1);
      return !var2.equals("REQUIRED_LEVEL") && !var2.equals("MANA_COST") && !var2.equals("STAMINA_COST") && !var2.equals("VENDOR_VALUE") && !var2.equals("BUY_PRICE") && !var2.equals("SELL_PRICE");
   }

   private double itemSoftCap(String var1, int var2) {
      double var3 = (Double)this.itemStatSoftCaps.getOrDefault(this.statRegistry.canonical(var1), this.itemDefaultSoftCap);
      double var5 = Math.pow((double)Math.max(1, var2), 0.72) * this.itemLevelSoftCapGrowth;
      return Math.max((double)1.0F, var3 + var5);
   }

   private double classItemScalar(String var1, String var2) {
      if (var1 != null && !var1.isBlank()) {
         Map var3 = (Map)this.classItemScalars.get(StatRegistry.normalizeClassId(var1));
         if (var3 != null && !var3.isEmpty()) {
            String var4 = this.statRegistry.canonical(var2);
            Double var5 = (Double)var3.get(var4);
            return var5 != null ? var5 : (Double)var3.getOrDefault(this.itemStatLane(var4), (double)1.0F);
         } else {
            return (double)1.0F;
         }
      } else {
         return (double)1.0F;
      }
   }

   private String itemStatLane(String var1) {
      String var2 = this.statRegistry.canonical(var1);
      if (var2.endsWith("_DAMAGE")) {
         if (!var2.equals("MAGIC_DAMAGE") && !var2.equals("SKILL_DAMAGE") && !var2.equals("ABILITY_DAMAGE") && !var2.equals("ARCANE_DAMAGE")) {
            String var10000;
            switch (var2) {
               case "FIRE_DAMAGE":
               case "ICE_DAMAGE":
               case "THUNDER_DAMAGE":
               case "WIND_DAMAGE":
               case "EARTH_DAMAGE":
               case "WATER_DAMAGE":
               case "DARKNESS_DAMAGE":
               case "LIGHTNESS_DAMAGE":
                  var10000 = "ELEMENTAL";
                  break;
               default:
                  var10000 = "OFFENSE";
            }

            return var10000;
         } else {
            return "MAGIC";
         }
      } else if (!var2.startsWith("CRITICAL_") && !var2.startsWith("SKILL_CRITICAL_")) {
         if (!var2.endsWith("_RESISTANCE") && !var2.equals("DEFENSE") && !var2.equals("ARMOR") && !var2.equals("ARMOR_TOUGHNESS") && !var2.equals("BLOCK_POWER") && !var2.equals("BLOCK_RATING") && !var2.equals("DODGE_RATING") && !var2.equals("PARRY_RATING")) {
            if (!var2.equals("MAX_HEALTH") && !var2.equals("HEALTH_REGENERATION") && !var2.equals("LIFE_STEAL") && !var2.equals("RESILIENCE") && !var2.equals("VITALITY")) {
               if (!var2.equals("MAX_MANA") && !var2.equals("MAX_STAMINA") && !var2.equals("MANA_REGENERATION") && !var2.equals("STAMINA_REGENERATION") && !var2.equals("COOLDOWN_REDUCTION") && !var2.equals("MANA_COST") && !var2.equals("STAMINA_COST") && !var2.equals("WISDOM") && !var2.equals("INTELLIGENCE")) {
                  return !var2.equals("MOVEMENT_SPEED") && !var2.equals("RANGE") && !var2.equals("ATTACK_SPEED") ? "UTILITY" : "MOBILITY";
               } else {
                  return "RESOURCE";
               }
            } else {
               return "SUSTAIN";
            }
         } else {
            return "DEFENSE";
         }
      } else {
         return "CRITICAL";
      }
   }

   private static double signedSoftCap(double var0, double var2) {
      return Math.signum(var0) * softCap(Math.abs(var0), var2);
   }

   private static double softCap(double var0, double var2) {
      double var4 = Math.max((double)1.0F, var2);
      double var6 = Math.max((double)0.0F, var0);
      return var4 * var6 / (var6 + var4);
   }

   private List<String> normalizedList(ConfigurationSection var1, String var2) {
      List var10000;
      if (var1 == null) {
         var10000 = List.of();
      } else {
         Stream var3 = var1.getStringList(var2).stream();
         StatRegistry var10001 = this.statRegistry;
         Objects.requireNonNull(var10001);
         var10000 = var3.map(var10001::canonical).filter((var0) -> !var0.isBlank()).distinct().toList();
      }

      return var10000;
   }

   private List<String> readCombo(ConfigurationSection var1) {
      List var2 = var1.getStringList("combo");
      if (var2.isEmpty()) {
         var2 = var1.getStringList("input");
      }

      if (var2.isEmpty()) {
         String var3 = var1.getString("combo", "");
         if (var3 != null && !var3.isBlank()) {
            var2 = List.of(var3.split("[,;>\\s]+"));
         }
      }

      return var2;
   }

   private Map<String, Double> readStatMap(ConfigurationSection var1, boolean var2) {
      LinkedHashMap var3 = new LinkedHashMap();
      if (var1 == null) {
         return var3;
      } else {
         for(String var5 : var1.getKeys(false)) {
            String var6 = var2 ? this.statRegistry.rpgStatAlias(var5) : this.statRegistry.canonical(var5);
            double var7 = var1.getDouble(var5);
            if (Math.abs(var7) > 1.0E-6) {
               var3.put(var6, this.statRegistry.sanitize(var6, var7));
            }
         }

         return var3;
      }
   }

   private Map<String, Integer> readIntegerMap(ConfigurationSection var1) {
      LinkedHashMap var2 = new LinkedHashMap();
      if (var1 == null) {
         return var2;
      } else {
         for(String var4 : var1.getKeys(false)) {
            var2.put(StatSnapshot.normalize(var4), var1.getInt(var4));
         }

         return var2;
      }
   }

   private List<String> readOrderedStringList(ConfigurationSection var1, String var2) {
      if (var1 == null) {
         return List.of();
      } else if (var1.isString(var2)) {
         String var9 = var1.getString(var2, "");
         if (var9 != null && !var9.isBlank()) {
            ArrayList var4 = new ArrayList();

            for(String var8 : var9.split("[;,/]+")) {
               if (!var8.isBlank()) {
                  var4.add(var8.trim());
               }
            }

            return var4;
         } else {
            return List.of();
         }
      } else {
         List var3 = var1.getStringList(var2);
         return var3 == null ? List.of() : var3.stream().map((var0) -> var0 == null ? "" : var0.trim()).filter((var0) -> !var0.isBlank()).toList();
      }
   }

   private static String humanize(String var0) {
      String var1 = StatSnapshot.normalize(var0).toLowerCase(Locale.ROOT);
      ArrayList var2 = new ArrayList();

      for(String var6 : var1.split("_+")) {
         if (!var6.isBlank()) {
            String var10001 = var6.substring(0, 1).toUpperCase(Locale.ROOT);
            var2.add(var10001 + var6.substring(1));
         }
      }

      return var2.isEmpty() ? "" : String.join(" ", var2);
   }

   private static String stripFormatting(String var0) {
      return var0 == null ? "" : var0.replaceAll("<[^>]+>", "").replaceAll("(?i)[&§][0-9A-FK-ORX]", "").trim();
   }

   private static String safe(String var0) {
      return var0 == null ? "" : var0.trim();
   }

   private static double clamp01(double var0) {
      return Math.max((double)0.0F, Math.min((double)1.0F, var0));
   }

   private static double parseDouble(String var0, double var1) {
      if (var0 != null && !var0.isBlank()) {
         try {
            double var3 = Double.parseDouble(var0.trim());
            return Double.isFinite(var3) ? var3 : var1;
         } catch (NumberFormatException var5) {
            return var1;
         }
      } else {
         return var1;
      }
   }

   private static String format(double var0) {
      return String.format(Locale.ROOT, "%.2f", var0);
   }
}
