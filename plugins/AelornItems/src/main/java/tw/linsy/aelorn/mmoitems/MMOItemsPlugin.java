package tw.linsy.aelorn.mmoitems;

import com.xuzhihuanjing.rpgcore.equipment.EquipmentRequirementReport;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentRequirementReport.Kind;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tw.linsy.aelorn.mmoitems.api.MMOItemsApi;
import tw.linsy.aelorn.mmoitems.config.TextBundle;
import tw.linsy.aelorn.mmoitems.config.ThresholdLadder;
import tw.linsy.aelorn.mmoitems.forge.ReforgeRules;
import tw.linsy.aelorn.mmoitems.forge.StatCatalogLoader;
import tw.linsy.aelorn.mmoitems.forge.StatFormula;
import tw.linsy.aelorn.mmoitems.lore.LoreText;
import tw.linsy.aelorn.mmoitems.lore.RpgCoreViewerBridge;
import tw.linsy.aelorn.mmoitems.lore.WynnLoreStyle;
import tw.linsy.aelorn.mmoitems.tooltip.HotkeyCooldown;
import tw.linsy.aelorn.mythiccore.api.MythicCoreApi;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;
import tw.linsy.aelorn.mythiccore.api.TradeQuote;

public final class MMOItemsPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
   private static final int INVENTORY_SIZE = 54;
   private static final int[] BROWSER_CONTENT_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
   private static final int[] CATEGORY_SLOTS = new int[]{19, 20, 21, 22, 23, 24, 25};
   private static final Set<WeaponStatCatalog.Category> OFFENSE_CATEGORIES;
   private static final Set<WeaponStatCatalog.Category> SURVIVAL_CATEGORIES;
   private static final int PAGE_SIZE;
   private static final LegacyComponentSerializer LEGACY_AMPERSAND;
   private static final LegacyComponentSerializer LEGACY_SECTION;
   private static final MiniMessage MINI_MESSAGE;
   private final Map<String, ItemTemplate> templates = new ConcurrentHashMap();
   private final Map<String, TierProfile> tiers = new ConcurrentHashMap();
   private final Map<String, ItemTypeProfile> itemTypes = new ConcurrentHashMap();
   private final Map<String, TooltipProfile> tooltips = new ConcurrentHashMap();
   private final Map<String, AffixTemplate> affixes = new ConcurrentHashMap();
   private final Map<String, SetBonusProfile> sets = new ConcurrentHashMap();
   private final Map<String, ItemPowerTemplate> gems = new ConcurrentHashMap();
   private volatile ReforgeRules reforgeRules;
   private final Map<String, ItemPowerTemplate> runes;
   private final Map<String, UpgradeProfile> upgrades;
   private final Map<String, Double> scoreWeights;
   private final Map<UUID, ItemDisplay> activePreviews;
   private static final long HOTKEY_COOLDOWN_NANOS = 120000000L;
   private final HotkeyCooldown browserHotkey;
   private final HotkeyCooldown tooltipHotkey;
   private final Map<UUID, Long> requirementWarningNanos;
   private MythicCoreApi api;
   private final LoreText loreText;
   private final ThresholdLadder attackSpeedTiers;
   private final TextBundle abilityDisplays;
   private final TextBundle classDisplays;
   private Set<String> properNameTiers;
   private final TextBundle tierAliases;
   private final TextBundle messages;
   private BrowserCategoryLoader browserCategoryLoader;
   private static final List<String> DEFAULT_HELP_LINES;
   private String defaultTierId;
   private RpgCoreViewerBridge rpgCore;
   private ItemRequirementService requirementService;
   private GoldCurrencyService currencyService;
   private MMOShopService shopService;
   private MMOCraftingService craftingService;
   private MMOForgeService forgeService;
   private MythicDropService mythicDropService;
   private ItemEditorService editorService;
   private MMOValidationService validationService;
   private MMOItemsApi publicApi;
   private static final List<BrowserCategory> DEFAULT_BROWSER_CATEGORIES;

   public MMOItemsPlugin() {
      this.reforgeRules = ReforgeRules.LEGACY_DEFAULT;
      this.runes = new ConcurrentHashMap();
      this.upgrades = new ConcurrentHashMap();
      this.scoreWeights = new ConcurrentHashMap();
      this.activePreviews = new ConcurrentHashMap();
      this.browserHotkey = new HotkeyCooldown(120000000L);
      this.tooltipHotkey = new HotkeyCooldown(120000000L);
      this.requirementWarningNanos = new ConcurrentHashMap();
      this.loreText = new LoreText();
      this.attackSpeedTiers = new ThresholdLadder(List.of(new ThresholdLadder.Tier(1.8, "快速"), new ThresholdLadder.Tier(1.35, "普通"), new ThresholdLadder.Tier(0.85, "慢速"), new ThresholdLadder.Tier(Double.NEGATIVE_INFINITY, "沉重")));
      this.abilityDisplays = new TextBundle();
      this.classDisplays = new TextBundle();
      this.properNameTiers = Set.of();
      this.tierAliases = new TextBundle();
      this.messages = new TextBundle();
      this.defaultTierId = "COMMON";
   }

   public void onEnable() {
      this.saveDefaultConfig();
      this.migrateConfiguration();
      this.ensureDefaultFiles();
      if (!this.hookCore()) {
         this.getLogger().severe("MythicCore API is missing. Disabling MMOItems.");
         Bukkit.getPluginManager().disablePlugin(this);
      } else {
         this.requirementService = new ItemRequirementService(this.api);
         this.reloadAll();
         Bukkit.getPluginManager().registerEvents(this, this);
         this.getCommand("mmoitems").setExecutor(this);
         this.getCommand("mmoitems").setTabCompleter(this);
         this.getCommand("updateitem").setExecutor(this);
         this.initializeExpansionServices();
         this.publicApi = new MMOItemsApiProvider(this, this.api);
         Bukkit.getServicesManager().register(MMOItemsApi.class, this.publicApi, this, ServicePriority.Normal);
         MMOValidationService.ValidationReport var1 = this.validationService.validate();
         if (var1.passed()) {
            this.getLogger().info("MMOItems 啟動資料檢查通過：" + var1.summary() + "。");
         } else {
            this.getLogger().severe("MMOItems 啟動資料檢查失敗：" + var1.summary() + "。");
            var1.errors().stream().limit(20L).forEach((var1x) -> this.getLogger().severe(" - " + var1x));
         }

         Logger var2 = this.getLogger();
         int var3 = this.templates.size();
         var2.info("MMOItems remake enabled with " + var3 + " templates, " + this.affixes.size() + " affixes and " + this.tiers.size() + " tiers.");
      }

   }

   public void onDisable() {
      Bukkit.getServicesManager().unregisterAll(this);
      this.publicApi = null;
      this.requirementService = null;
      this.requirementWarningNanos.clear();
      if (this.forgeService != null) {
         this.forgeService.shutdown();
      }

      (new ArrayList<>(this.activePreviews.keySet())).forEach(this::removePreview);
   }

   private void initializeExpansionServices() {
      this.currencyService = new GoldCurrencyService(this, this.api);
      this.shopService = new MMOShopService(this, this.api, this.currencyService);
      this.craftingService = new MMOCraftingService(this, this.api, this.currencyService);
      this.forgeService = new MMOForgeService(this, this.api, this.currencyService);
      this.editorService = new ItemEditorService(this, this.api);
      this.mythicDropService = new MythicDropService(this);
      this.validationService = new MMOValidationService(this, this.api);
   }

   private boolean hookCore() {
      RegisteredServiceProvider var1 = Bukkit.getServicesManager().getRegistration(MythicCoreApi.class);
      if (var1 == null) {
         return false;
      } else {
         this.api = (MythicCoreApi)var1.getProvider();
         this.rpgCore = new RpgCoreViewerBridge(this.getServer(), this.getLogger());
         return this.api != null;
      }
   }

   private void ensureDefaultFiles() {
      this.saveResourceIfMissing("items/sword.yml");
      this.saveResourceIfMissing("items/spear.yml");
      this.saveResourceIfMissing("items/greatsword.yml");
      this.saveResourceIfMissing("items/axe.yml");
      this.saveResourceIfMissing("items/dagger.yml");
      this.saveResourceIfMissing("items/bow.yml");
      this.saveResourceIfMissing("items/staff.yml");
      this.saveResourceIfMissing("items/catalyst.yml");
      this.saveResourceIfMissing("items/helmet.yml");
      this.saveResourceIfMissing("items/chestplate.yml");
      this.saveResourceIfMissing("items/leggings.yml");
      this.saveResourceIfMissing("items/boots.yml");
      this.saveResourceIfMissing("items/material.yml");
      this.saveResourceIfMissing("items/food.yml");
      this.saveResourceIfMissing("items/gem_stone.yml");
      this.saveResourceIfMissing("items/upgrade_stone.yml");
      this.saveResourceIfMissing("item-tiers.yml");
      this.saveResourceIfMissing("item-types.yml");
      this.saveResourceIfMissing("modifiers.yml");
      this.saveResourceIfMissing("item-sets.yml");
      this.saveResourceIfMissing("gems.yml");
      this.saveResourceIfMissing("stats.yml");
      this.saveResourceIfMissing("messages.yml");
      this.saveResourceIfMissing("runes.yml");
      this.saveResourceIfMissing("upgrade-templates.yml");
      this.saveResourceIfMissing("tooltips/mmorpg_tooltips.yml");
      this.saveResourceIfMissing("shops.yml");
      this.saveResourceIfMissing("recipes.yml");
      this.saveResourceIfMissing("mythic-drops.yml");
      this.saveResourceIfMissing("物品開發指南.md");
      this.mergeBundledRootSections("items/upgrade_stone.yml");
   }

   private void mergeBundledRootSections(String var1) {
      File var2 = new File(this.getDataFolder(), var1);
      if (var2.isFile()) {
         try {
            InputStream var3 = this.getResource(var1);

            label77: {
               try {
                  if (var3 != null) {
                     YamlConfiguration var4 = YamlConfiguration.loadConfiguration(new InputStreamReader(var3, StandardCharsets.UTF_8));
                     YamlConfiguration var5 = YamlConfiguration.loadConfiguration(var2);
                     boolean var6 = false;

                     for(String var8 : var4.getKeys(false)) {
                        if (!var5.contains(var8)) {
                           ConfigurationSection var9 = var4.getConfigurationSection(var8);
                           if (var9 == null) {
                              var5.set(var8, var4.get(var8));
                           } else {
                              var9.getValues(true).forEach((var2x, var3x) -> {
                                 if (!(var3x instanceof ConfigurationSection)) {
                                    var5.set(var8 + "." + var2x, var3x);
                                 }

                              });
                           }

                           var6 = true;
                        }
                     }

                     if (var6) {
                        var5.save(var2);
                     }
                     break label77;
                  }
               } catch (Throwable var11) {
                  if (var3 != null) {
                     try {
                        var3.close();
                     } catch (Throwable var10) {
                        var11.addSuppressed(var10);
                     }
                  }

                  throw var11;
               }

               if (var3 != null) {
                  var3.close();
               }

               return;
            }

            if (var3 != null) {
               var3.close();
            }

            return;
         } catch (IOException var12) {
            this.getLogger().warning("合併預設資料失敗 " + var1 + "：" + var12.getMessage());
         }
      }

   }

   private void migrateConfiguration() {
      File var1 = new File(this.getDataFolder(), "config.yml");
      YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);
      int var3 = var2.getInt("config-version", 1);
      if (var3 < 6) {
         if (var2.getInt("browser.resource-pack-background.width", 205) == 205) {
            var2.set("browser.resource-pack-background.width", 256);
         }

         var2.set("browser.resource-pack-background.enabled", false);
         var2.set("browser.vanilla-layout.enabled", true);
         var2.set("browser.vanilla-layout.fill-border", true);
         var2.set("sockets.default-per-tier.STARTER", 0);
         var2.set("sockets.default-per-tier.COMMON", 1);
         var2.set("sockets.default-per-tier.RARE", 2);
         var2.set("sockets.default-per-tier.EPIC", 2);
         var2.set("sockets.default-per-tier.LEGENDARY", 3);
         var2.set("sockets.default-per-tier.VAST", 4);
         var2.set("sockets.default-per-tier.UNCOMMON", (Object)null);
         var2.set("sockets.default-per-tier.MYTHIC", (Object)null);
         var2.set("tooltips.multi-page.enabled", true);
         var2.set("tooltips.multi-page.equipment-pages", 5);
         var2.set("tooltips.multi-page.other-pages", 3);
         var2.set("tooltips.custom-style.enabled", true);
         var2.set("tooltips.custom-style.namespace", "mmoitems");
         var2.set("forge.base-success-chance", var2.getDouble("forge.base-success-chance", 0.92));
         var2.set("forge.level-penalty", var2.getDouble("forge.level-penalty", 0.045));
         var2.set("forge.catalyst-bonus", var2.getDouble("forge.catalyst-bonus", 0.1));
         var2.set("forge.pity-per-failure", var2.getDouble("forge.pity-per-failure", 0.035));
         var2.set("forge.base-cost", var2.getDouble("forge.base-cost", (double)12.0F));
         var2.set("forge.level-cost-factor", var2.getDouble("forge.level-cost-factor", (double)3.0F));
         var2.set("forge.item-value-factor", var2.getDouble("forge.item-value-factor", 0.025));
         var2.set("mythic-drops.enabled", var2.getBoolean("mythic-drops.enabled", true));
         var2.set("config-version", 6);

         try {
            var2.save(var1);
            this.reloadConfig();
            this.getLogger().info("MMOItems 設定已升級至版本 6，啟用 GUI 編輯、合成、強化、交易與 MythicMobs 掉落。");
         } catch (IOException var6) {
            throw new IllegalStateException("無法升級 MMOItems config.yml", var6);
         }
      }

      if (var2.getInt("config-version", 1) < 7) {
         var2.set("gui.internal-textures.enabled", var2.getBoolean("gui.internal-textures.enabled", true));
         var2.set("tooltips.multi-page.enabled", true);
         var2.set("tooltips.multi-page.equipment-pages", 5);
         var2.set("tooltips.multi-page.other-pages", 3);
         var2.set("config-version", 7);

         try {
            var2.save(var1);
            this.reloadConfig();
            this.getLogger().info("MMOItems 設定已升級至版本 7：啟用單一資源包 GUI 與雙向物品翻頁。");
         } catch (IOException var5) {
            throw new IllegalStateException("無法升級 MMOItems config.yml 至版本 7", var5);
         }
      }

   }

   private void reloadAll() {
      this.reloadConfig();
      int var1 = this.messages.load(this.readYaml("messages.yml"));
      if (var1 > 0) {
         this.getLogger().info("已從 messages.yml 套用 " + var1 + " 條訊息覆寫。");
      }

      this.loadScoreWeights();
      this.reforgeRules = ReforgeRules.from(this.getConfig().getConfigurationSection("reforge"));
      StatCatalogLoader var2 = new StatCatalogLoader();
      ConfigurationSection var3 = this.readYaml("stats.yml");
      int var4 = var2.load(var3);
      ConfigurationSection var5 = var3 == null ? null : var3.getConfigurationSection("lore");
      int var6 = this.loreText.load(var5);
      if (var6 > 0) {
         this.getLogger().info("已從 stats.yml 套用 " + var6 + " 條 Lore 文字覆寫。");
      }

      this.attackSpeedTiers.load(var5 == null ? null : var5.getList("attack-speed-tiers"));
      ConfigurationSection var7 = var5 == null ? null : var5.getConfigurationSection("displays");
      this.abilityDisplays.load(var7 == null ? null : var7.getConfigurationSection("ability"));
      this.classDisplays.load(var7 == null ? null : var7.getConfigurationSection("class"));
      if (WeaponStatCatalog.applyCatalog(var2)) {
         this.getLogger().info("已從 stats.yml 載入 " + var4 + " 條武器屬性。");
      } else {
         this.getLogger().warning("stats.yml 無法載入，沿用內建屬性目錄。");
      }

      this.loadTiers();
      this.loadItemTypes();
      this.loadTooltips();
      this.loadAffixes();
      this.loadSets();
      this.loadPowers("gems.yml", this.gems);
      this.loadPowers("runes.yml", this.runes);
      this.loadUpgrades();
      this.loadItems();
      LinkedHashSet var8 = new LinkedHashSet();
      this.templates.values().forEach((var1x) -> var8.addAll(var1x.statKeys()));
      this.affixes.values().forEach((var1x) -> var8.addAll(var1x.statKeys()));
      this.gems.values().forEach((var1x) -> var8.addAll(var1x.statKeys()));
      this.runes.values().forEach((var1x) -> var8.addAll(var1x.statKeys()));
      this.upgrades.values().forEach((var1x) -> var8.addAll(var1x.statKeys()));
      var8.addAll(WeaponStatCatalog.knownKeys());
      this.api.registerKnownStats(var8);
      this.api.registerSetBonuses((Map)this.sets.values().stream().collect(Collectors.toMap(SetBonusProfile::id, SetBonusProfile::bonuses, (var0, var1x) -> var0, LinkedHashMap::new)));
   }

   private void loadTiers() {
      this.tiers.clear();
      File var1 = new File(this.getDataFolder(), "item-tiers.yml");
      YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);

      for(String var4 : var2.getKeys(false)) {
         if (!"aliases".equalsIgnoreCase(var4) && !"default-tier".equalsIgnoreCase(var4) && !"proper-name-tiers".equalsIgnoreCase(var4)) {
            ConfigurationSection var5 = var2.getConfigurationSection(var4);
            if (var5 != null) {
               TierProfile var6 = TierProfile.from(var4, var5);
               this.tiers.put(var6.id(), var6);
            }
         }
      }

      this.tierAliases.load(var2.getConfigurationSection("aliases"));
      this.defaultTierId = var2.getString("default-tier", "COMMON").trim().toUpperCase(Locale.ROOT);
      this.properNameTiers = (Set)var2.getStringList("proper-name-tiers").stream().map((var0) -> var0.trim().toUpperCase(Locale.ROOT)).filter((var0) -> !var0.isEmpty()).collect(Collectors.toUnmodifiableSet());
      if (this.tiers.isEmpty()) {
         this.tiers.put("COMMON", new TierProfile("COMMON", "&f凡品", "", DeconstructionProfile.empty(), (double)1.0F, 45, 78, 1, (double)1.0F, (StatFormula)null));
      }

   }

   private void loadItemTypes() {
      this.itemTypes.clear();
      File var1 = new File(this.getDataFolder(), "item-types.yml");
      YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);
      if (this.browserCategoryLoader == null) {
         this.browserCategoryLoader = new BrowserCategoryLoader(DEFAULT_BROWSER_CATEGORIES, this.getLogger());
      }

      this.browserCategoryLoader.load(var2.getList("browser-categories"));

      for(String var4 : var2.getKeys(false)) {
         if (!"browser-categories".equalsIgnoreCase(var4)) {
            ConfigurationSection var5 = var2.getConfigurationSection(var4);
            if (var5 != null) {
               ItemTypeProfile var6 = ItemTypeProfile.from(var4, var5);
               this.itemTypes.put(var6.id(), var6);
            }
         }
      }

   }

   private void loadTooltips() {
      this.tooltips.clear();
      File var2 = new File(this.getDataFolder(), "tooltips");
      File[] var1;
      if (var2.isDirectory() && (var1 = var2.listFiles((var0, var1x) -> var1x.endsWith(".yml") || var1x.endsWith(".yaml"))) != null) {
         for(File var6 : var1) {
            YamlConfiguration var7 = YamlConfiguration.loadConfiguration(var6);

            for(String var9 : var7.getKeys(false)) {
               ConfigurationSection var10 = var7.getConfigurationSection(var9);
               if (var10 != null) {
                  TooltipProfile var11 = TooltipProfile.from(var9, var10);
                  this.tooltips.put(var11.id(), var11);
               }
            }
         }
      }

   }

   private void loadAffixes() {
      this.affixes.clear();
      File var1 = new File(this.getDataFolder(), "modifiers.yml");
      YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);

      for(String var4 : var2.getKeys(false)) {
         ConfigurationSection var5 = var2.getConfigurationSection(var4);
         if (var5 != null && var5.isConfigurationSection("stats")) {
            AffixTemplate var6 = AffixTemplate.from(var4, var5);
            this.affixes.put(var6.id(), var6);
         }
      }

   }

   private void loadSets() {
      this.sets.clear();
      File var1 = new File(this.getDataFolder(), "item-sets.yml");
      YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);

      for(String var4 : var2.getKeys(false)) {
         ConfigurationSection var5 = var2.getConfigurationSection(var4);
         if (var5 != null) {
            SetBonusProfile var6 = SetBonusProfile.from(var4, var5);
            this.sets.put(var6.id(), var6);
         }
      }

   }

   private void loadPowers(String var1, Map<String, ItemPowerTemplate> var2) {
      var2.clear();
      File var3 = new File(this.getDataFolder(), var1);
      YamlConfiguration var4 = YamlConfiguration.loadConfiguration(var3);

      for(String var6 : var4.getKeys(false)) {
         ConfigurationSection var7 = var4.getConfigurationSection(var6);
         if (var7 != null) {
            ItemPowerTemplate var8 = ItemPowerTemplate.from(var6, var7);
            var2.put(var8.id(), var8);
         }
      }

   }

   private void loadUpgrades() {
      this.upgrades.clear();
      File var1 = new File(this.getDataFolder(), "upgrade-templates.yml");
      YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);

      for(String var4 : var2.getKeys(false)) {
         ConfigurationSection var5 = var2.getConfigurationSection(var4);
         if (var5 != null) {
            UpgradeProfile var6 = UpgradeProfile.from(var4, var5);
            this.upgrades.put(var6.id(), var6);
         }
      }

   }

   private void loadScoreWeights() {
      this.scoreWeights.clear();
      ConfigurationSection var1 = this.getConfig().getConfigurationSection("score.weights");
      if (var1 != null) {
         for(String var3 : var1.getKeys(false)) {
            this.scoreWeights.put(StatSnapshot.normalize(var3), var1.getDouble(var3));
         }
      }

   }

   private void loadItems() {
      this.templates.clear();
      this.loadFolder(new File(this.getDataFolder(), "items"), false);
      if (this.getConfig().getBoolean("compatibility.import-legacy-item-folder", true)) {
         this.loadFolder(new File(this.getDataFolder(), "item"), true);
      }

   }

   private void loadFolder(File var1, boolean var2) {
      File[] var3;
      if (var1 != null && var1.isDirectory() && (var3 = var1.listFiles((var0, var1x) -> var1x.endsWith(".yml") || var1x.endsWith(".yaml"))) != null) {
         for(File var7 : var3) {
            this.loadFile(var7, var2);
         }
      }

   }

   private void loadFile(File var1, boolean var2) {
      String var3 = stripExtension(var1.getName()).toUpperCase(Locale.ROOT);
      YamlConfiguration var4 = YamlConfiguration.loadConfiguration(var1);

      for(String var6 : var4.getKeys(false)) {
         ConfigurationSection var9 = var4.getConfigurationSection(var6);
         ItemTemplate var7;
         String var8;
         if (var9 != null && (!this.templates.containsKey(var8 = key((var7 = ItemTemplate.fromSection(var3, var6, var9)).type(), var7.id())) || !var2)) {
            this.templates.put(var8, var7);
         }
      }

   }

   private ItemStack createItem(ItemTemplate var1, int var2, int var3, String var4, Integer var5, int var6, long var7, List<String> var9, List<String> var10, String var11) {
      int var12 = this.safeLevel(var2);
      int var13 = this.safeAmount(var3);
      Random var14 = new Random(var7);
      TierProfile var15 = this.tier(this.resolveTierId(var1, var4));
      ItemTypeProfile var16 = this.itemType(var1);
      int var17 = var5 == null ? var15.rollQuality(var14) : Math.max(1, Math.min(100, var5));
      List<AffixTemplate> var18 = var9 == null ? this.rollAffixes(var1, var15, var12, var14) : var9.stream().map((var1x) -> (AffixTemplate)this.affixes.get(var1x.toUpperCase(Locale.ROOT))).filter(Objects::nonNull).toList();
      List<ItemPowerTemplate> var19 = this.resolvePowers(this.gems, var10, var12, this.gemSlots(var1, var15));
      ItemPowerTemplate var20 = this.resolvePower(this.runes, var11, var12);
      Map var21 = var1.statsAtLevel(var12, new Random(var7 ^ 52235093L));

      for(AffixTemplate var23 : var18) {
         merge(var21, var23.statsAtLevel(var12, new Random(var7 ^ (long)var23.id().hashCode())));
      }

      this.scaleStats(var21, var15, var17);
      this.applyUpgrade(var21, var1, var6);

      for(ItemPowerTemplate var30 : var19) {
         merge(var21, var30.statsAtLevel(var12, new Random(var7 ^ (long)var30.id().hashCode())));
      }

      if (var20 != null) {
         merge(var21, var20.statsAtLevel(var12, new Random(var7 ^ (long)var20.id().hashCode())));
      }

      this.sanitizeStats(var21);
      LinkedHashMap var29 = new LinkedHashMap(this.api.normalizeItemStats(var21, var1.type(), var1.requiredClass(), var12));
      this.sanitizeStats(var29);
      ItemStack var31 = new ItemStack(var1.material() == null ? Material.IRON_SWORD : var1.material(), var13);
      ItemMeta var24 = var31.getItemMeta();
      var24.displayName(color(this.displayName(var1, var18, var6)));
      if (var1.customModelData() > 0) {
         CustomModelDataComponent var25 = var24.getCustomModelDataComponent();
         var25.setFloats(List.of((float)var1.customModelData()));
         var24.setCustomModelDataComponent(var25);
      }

      if (!var1.itemModel().isBlank()) {
         NamespacedKey var32 = NamespacedKey.fromString(var1.itemModel(), this);
         if (var32 != null) {
            var24.setItemModel(var32);
         } else {
            Logger var26 = this.getLogger();
            String var27 = var1.itemModel();
            var26.warning("忽略無效的 item-model: " + var27 + " (" + var1.type() + "." + var1.id() + ")");
         }
      }

      var24.setUnbreakable(var1.unbreakable());
      var24.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE});
      ItemTemplate.AbilityData var33 = this.resolveAbility(var1, var18, var19, var20);
      this.api.writeItemData(var24, this.getName(), var1.type(), var1.id(), var12, var29);
      this.api.writeItemTags(var24, this.buildTags(var1, var16, var15, var17, var6, var7, var18, var19, var20, var33, var12, var29));
      this.applyTooltipStyle(var24, var15);
      List var34 = this.buildLorePages(var1, var16, var15, var17, var6, var18, var19, var20, var33, var12, var29, "", List.of());
      this.api.writeItemTags(var24, Map.of("tooltip_page", "0", "tooltip_pages", Integer.toString(var34.size())));
      var24.lore((List)var34.getFirst());
      var31.setItemMeta(var24);
      return var31;
   }

   private Map<String, String> buildTags(ItemTemplate var1, ItemTypeProfile var2, TierProfile var3, int var4, int var5, long var6, List<AffixTemplate> var8, List<ItemPowerTemplate> var9, ItemPowerTemplate var10, ItemTemplate.AbilityData var11, int var12, Map<String, Double> var13) {
      LinkedHashMap var14 = new LinkedHashMap();
      ScoreBreakdown var15 = this.scoreBreakdown(var13);
      TradeQuote var16 = this.api.tradeQuote(var3.id(), var12, var4, var15.total());
      var14.put("tier", var3.id());
      var14.put("tier_name", plainLegacy(var3.display()));
      var14.put("type_name", plainText(this.typeDisplay(var1, var2)));
      var14.put("browser_category", this.browserCategoryFor(var1.type()).id());
      var14.put("quality", Integer.toString(var4));
      var14.put("upgrade_level", Integer.toString(var5));
      var14.put("instance_seed", Long.toString(var6));
      var14.put("affixes", (String)var8.stream().map(AffixTemplate::id).collect(Collectors.joining(",")));
      var14.put("gem_slots", Integer.toString(this.gemSlots(var1, var3)));
      var14.put("gems", (String)var9.stream().map(ItemPowerTemplate::id).collect(Collectors.joining(",")));
      var14.put("rune", var10 == null ? "" : var10.id());
      var14.put("upgrade_template", var1.upgradeTemplate());
      var14.put("item_score", format(var15.total()));
      var14.put("score_grade", var15.grade());
      var14.put("score_offense", format(var15.offense()));
      var14.put("score_defense", format(var15.defense()));
      var14.put("score_resource", format(var15.resource()));
      var14.put("score_utility", format(var15.utility()));
      var14.put("buy_price", format(var16.buyPrice()));
      var14.put("sell_price", format(var16.sellPrice()));
      var14.put("currency", var16.currency());
      var14.put("generated_at", Instant.now().toString());
      var14.put("tooltip", this.tooltipId(var1, var3, var2));
      var14.put("can_deconstruct", Boolean.toString(this.canDeconstruct(var3)));
      if (!var2.damageTypes().isEmpty()) {
         var14.put("damage_types", String.join(",", var2.damageTypes()));
      }

      if (!var1.displayedType().isBlank()) {
         var14.put("displayed_type", plainText(var1.displayedType()));
      }

      if (var1.requiredLevel() > 0) {
         var14.put("required_level", Integer.toString(var1.requiredLevel()));
      }

      if (!var1.setId().isBlank()) {
         var14.put("set_id", var1.setId());
      }

      if (!var1.requiredClass().isBlank()) {
         var14.put("required_class", var1.requiredClass());
      }

      var1.requiredSkills().forEach((var1x, var2x) -> var14.put("required_" + var1x.toLowerCase(Locale.ROOT), Integer.toString(var2x)));
      if (!var1.requiredQuests().isEmpty()) {
         var14.put("required_quests", String.join(",", var1.requiredQuests()));
      }

      if (var1.majorIdentification().enabled()) {
         var14.put("major_identification", var1.majorIdentification().id());
         var14.put("major_identification_name", plainText(var1.majorIdentification().displayName()));
      } else {
         var8.stream().filter(AffixTemplate::major).findFirst().ifPresent((var2x) -> {
            var14.put("major_identification", var2x.id());
            var14.put("major_identification_name", plainText(this.affixDisplay(var2x)));
         });
      }

      if (var11.enabled()) {
         var14.put("ability_on_hit", var11.type());
         var14.put("ability_chance", Double.toString(var11.chance()));
         var14.put("ability_power", Double.toString(var11.power()));
      }

      return var14;
   }

   private List<List<Component>> buildLorePages(ItemTemplate var1, ItemTypeProfile var2, TierProfile var3, int var4, int var5, List<AffixTemplate> var6, List<ItemPowerTemplate> var7, ItemPowerTemplate var8, ItemTemplate.AbilityData var9, int var10, Map<String, Double> var11, String var12, List<EquipmentRequirementReport.Entry> var13) {
      int var15 = this.tooltipPageCount(var1);
      ArrayList var16 = new ArrayList(var15);
      ScoreBreakdown var17 = this.scoreBreakdown(var11);
      List var18 = this.beginLorePage(var1, var2, var3, this.loreText.page("overview", "物品總覽"));

      for(String var20 : var1.lore()) {
         var18.add(color(var20));
      }

      if (!var1.lore().isEmpty()) {
         var18.add(Component.empty());
      }

      if (this.isWeapon(var1)) {
         this.addWeaponSummary(var18, var1, var11);
      } else if (this.isEquipment(var1)) {
         this.addEquipmentSummary(var18, var11);
      }

      var18.add(color(this.loreText.format("level-line", "&7等級：&f{level}    &7品鑑：&f{quality}%", "level", var10, "quality", var4)));
      if (var5 > 0) {
         var18.add(color(this.loreText.format("upgrade-line", "&7強化等級：&e+{upgrade}", "upgrade", var5)));
      }

      TradeQuote var14;
      if ((var14 = this.api.tradeQuote(var3.id(), var10, var4, var17.total())).buyPrice() > 1.0E-6) {
         var18.add(color(this.loreText.format("trade-quote-line", "&7商店估價：&f購買 {buy} / 售出 {sell} {currency}", "buy", format(var14.buyPrice()), "sell", format(var14.sellPrice()), "currency", var14.currency())));
      }

      var18.add(Component.empty());
      this.addRequirementLines(var18, var1, var13);
      var18.add(Component.empty());
      var18.add(color(this.loreText.format("score-line", "&e裝備評分：&f{score} &8[&f{grade}&8]", "score", format(var17.total()), "grade", var17.grade())));
      var16.add(this.finishLorePage(var18, 0, var15));
      return this.buildRemainingLorePages(var16, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var15, var17);
   }

   private void addRequirementLines(List<Component> var1, ItemTemplate var2, List<EquipmentRequirementReport.Entry> var3) {
      var1.add(color(WynnLoreStyle.requirement(this.requirementOf(var3, Kind.CLASS, (String)null, this.loreText.get("requirement-class", "職業"), this.classRequirementDisplay(var2.requiredClass())))));
      var1.add(color(WynnLoreStyle.requirement(this.requirementOf(var3, Kind.LEVEL, (String)null, this.loreText.get("requirement-level", "戰鬥等級"), Integer.toString(Math.max(1, var2.requiredLevel()))))));
      var2.requiredSkills().forEach((var3x, var4) -> {
         String var5 = requirementSkillDisplay(var3x);
         var1.add(color(WynnLoreStyle.requirement(this.requirementOf(var3, Kind.SKILL, var5, var5, Integer.toString(var4)))));
      });

      for(String var5 : var2.requiredQuests()) {
         var1.add(color(WynnLoreStyle.requirement(this.requirementOf(var3, Kind.QUEST, var5, this.loreText.get("requirement-quest", "任務"), var5))));
      }

   }

   private WynnLoreStyle.Requirement requirementOf(List<EquipmentRequirementReport.Entry> var1, EquipmentRequirementReport.Kind var2, String var3, String var4, String var5) {
      for(EquipmentRequirementReport.Entry var7 : var1) {
         if (var7.kind() == var2 && (var3 == null || var3.equalsIgnoreCase(var7.label()) || var3.equalsIgnoreCase(var7.requirement()))) {
            return WynnLoreStyle.Requirement.checked(var4, var5, var7.met());
         }
      }

      return WynnLoreStyle.Requirement.of(var4, var5);
   }

   private List<List<Component>> buildRemainingLorePages(List<List<Component>> var1, ItemTemplate var2, ItemTypeProfile var3, TierProfile var4, int var5, int var6, List<AffixTemplate> var7, List<ItemPowerTemplate> var8, ItemPowerTemplate var9, ItemTemplate.AbilityData var10, int var11, Map<String, Double> var12, String var13, int var14, ScoreBreakdown var15) {
      List var16 = this.beginLorePage(var2, var3, var4, var14 == 3 ? this.loreText.page("attributes", "屬性與功能") : this.loreText.page("offense", "攻擊與元素"));
      var16.add(color(this.loreText.get("score-breakdown-title", "&6評分構成")));
      var16.add(color(this.loreText.format("score-breakdown-combat", "&7傷害：&f{offense}    &7生存：&f{defense}", "offense", format(var15.offense()), "defense", format(var15.defense()))));
      var16.add(color(this.loreText.format("score-breakdown-support", "&7資源：&f{resource}    &7輔助：&f{utility}", "resource", format(var15.resource()), "utility", format(var15.utility()))));
      var16.add(Component.empty());
      int var17 = var16.size();
      this.addCategorizedStats(var16, var12, OFFENSE_CATEGORIES);
      if (var16.size() == var17) {
         var16.add(color(this.loreText.get("empty-offense", "&8此物品沒有攻擊屬性。")));
      }

      if (var14 == 3) {
         var16.add(Component.empty());
         this.addCategorizedStats(var16, var12, SURVIVAL_CATEGORIES);
         this.addAbilityDetails(var16, var2, var4, var7, var10);
         this.addSocketDetails(var16, var2, var4, var8, var9, var13);
      }

      var1.add(this.finishLorePage(var16, 1, var14));
      if (var14 == 5) {
         List var18 = this.beginLorePage(var2, var3, var4, this.loreText.page("survival", "生存與資源"));
         int var19 = var18.size();
         this.addCategorizedStats(var18, var12, SURVIVAL_CATEGORIES);
         if (var18.size() == var19) {
            var18.add(color(this.loreText.get("empty-survival", "&8此物品沒有生存或資源屬性。")));
         }

         var1.add(this.finishLorePage(var18, 2, var14));
         List var20 = this.beginLorePage(var2, var3, var4, this.loreText.page("abilities", "能力、鑲嵌與限制"));
         this.addAbilityDetails(var20, var2, var4, var7, var10);
         var20.add(Component.empty());
         this.addSocketDetails(var20, var2, var4, var8, var9, var13);
         var1.add(this.finishLorePage(var20, 3, var14));
      }

      int var22 = var14 - 1;
      List var23 = this.beginLorePage(var2, var3, var4, this.loreText.page("story", "物品故事"));
      if (var2.story().isEmpty()) {
         var23.add(color(this.loreText.get("empty-story", "&8尚未有人記錄這件物品的來歷。")));
      } else {
         for(String var21 : var2.story()) {
            var23.add(color(var21));
         }
      }

      var23.add(Component.empty());
      var23.add(color(this.loreText.format("catalogue-line", "&8典藏編號：{type}.{id}", "type", var2.type(), "id", var2.id())));
      var1.add(this.finishLorePage(var23, var22, var14));
      return List.copyOf(var1);
   }

   private List<Component> beginLorePage(ItemTemplate var1, ItemTypeProfile var2, TierProfile var3, String var4) {
      ArrayList var5 = new ArrayList();
      var5.add(color(this.loreText.format("page-header", "{tier} &8｜ &f{type}", "tier", var3.display(), "type", this.typeDisplay(var1, var2))));
      var5.add(color(this.loreText.format("page-title", "&f{title}", "title", var4)));
      var5.add(color(WynnLoreStyle.divider()));
      return var5;
   }

   private List<Component> finishLorePage(List<Component> var1, int var2, int var3) {
      var1.add(color(WynnLoreStyle.divider()));
      TextComponent var4 = Component.empty();

      for(int var5 = 0; var5 < var3; ++var5) {
         var4 = (TextComponent)var4.append(Component.text(var5 == var2 ? "●" : "•", var5 == var2 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));
         if (var5 + 1 < var3) {
            var4 = (TextComponent)var4.append(Component.space());
         }
      }

      var4 = (TextComponent)((TextComponent)((TextComponent)var4.append(Component.text("   "))).append(RpgGuiTitle.keyF(this))).append(Component.text(this.loreText.get("page-hint", " 翻頁"), NamedTextColor.GRAY));
      var1.add(noItalic(var4));
      return var1.stream().map(MMOItemsPlugin::noItalic).toList();
   }

   private void addWeaponSummary(List<Component> var1, ItemTemplate var2, Map<String, Double> var3) {
      double var4 = this.displayedWeaponDamage(var3);
      double var6 = this.displayedHitsPerSecond(var2, var3);
      if (!(var4 <= 1.0E-6)) {
         var1.add(color(this.loreText.format("weapon-dps", "&f{dps} &7每秒傷害", "dps", compact(var4 * var6))));
         var1.add(color(this.loreText.format("weapon-attack-speed", "&7攻擊速度：&f{label} &8(&f每秒 {rate} 次&8)", "label", this.attackSpeedLabel(var6), "rate", compact(var6))));
         var1.add(color(this.loreText.format("weapon-damage-range", "&6傷害範圍：&f{range}", "range", damageRange(var4))));
         var1.add(Component.empty());
      }

   }

   private void addEquipmentSummary(List<Component> var1, Map<String, Double> var2) {
      double var3 = positiveStat(var2, "MAX_HEALTH");
      double var5 = positiveStat(var2, "DEFENSE") + positiveStat(var2, "ARMOR");
      double var7 = positiveStat(var2, "MAGIC_RESISTANCE") + positiveStat(var2, "ELEMENTAL_RESISTANCE");
      if (var3 > 1.0E-6) {
         var1.add(color(this.loreText.format("equipment-health", "&f+{value} &7生命", "value", compact(var3))));
      }

      if (var5 > 1.0E-6) {
         var1.add(color(this.loreText.format("equipment-defense", "&f+{value} &7防禦", "value", compact(var5))));
      }

      if (var7 > 1.0E-6) {
         var1.add(color(this.loreText.format("equipment-resistance", "&f+{value} &7抗性", "value", compact(var7))));
      }

      if (var3 > 1.0E-6 || var5 > 1.0E-6 || var7 > 1.0E-6) {
         var1.add(Component.empty());
      }

   }

   private void addAbilityDetails(List<Component> var1, ItemTemplate var2, TierProfile var3, List<AffixTemplate> var4, ItemTemplate.AbilityData var5) {
      if (!var4.isEmpty()) {
         Stream var6 = var4.stream().map(this::affixDisplay);
         var1.add(color(this.loreText.format("affix-list", "&d附加能力：&f{affixes}", "affixes", var6.collect(Collectors.joining(this.loreText.get("affix-separator", "、"))))));
         var4.stream().filter(AffixTemplate::major).findFirst().ifPresent((var2x) -> var1.add(color(this.loreText.format("affix-major", "&c核心詞條：&f{affix}", "affix", this.affixDisplay(var2x)))));
      } else {
         var1.add(color(this.loreText.get("empty-affixes", "&8沒有額外詞綴。")));
      }

      if (var2.majorIdentification().enabled()) {
         var1.add(Component.empty());
         var1.add(color(this.loreText.format("major-identification", "&c核心特性：&f{name}", "name", var2.majorIdentification().displayName())));

         for(String var7 : var2.majorIdentification().description()) {
            var1.add(color(this.loreText.format("major-identification-line", "&7{line}", "line", var7)));
         }
      }

      if (var5.enabled()) {
         var1.add(Component.empty());
         var1.add(color(this.loreText.format("ability-name", "&e特殊能力：&f{name}", "name", this.translateAbility(var5.type()))));
         var1.add(color(this.loreText.format("ability-chance", "&7觸發機率：&f{chance}%", "chance", format(var5.chance()))));
         if (Math.abs(var5.power()) > 1.0E-6) {
            var1.add(color(this.loreText.format("ability-power", "&7能力強度：&f{power}", "power", format(var5.power()))));
         }
      }

      if (!var2.setId().isBlank()) {
         var1.add(Component.empty());
         var1.add(color(this.loreText.format("set-name", "&b所屬套裝：&f{set}", "set", var2.setId())));
      }

      var1.add(color(this.canDeconstruct(var3) ? this.loreText.get("deconstruct-yes", "&a可拆解：&f是") : this.loreText.get("deconstruct-no", "&8可拆解：否")));
   }

   private void addSocketDetails(List<Component> var1, ItemTemplate var2, TierProfile var3, List<ItemPowerTemplate> var4, ItemPowerTemplate var5, String var6) {
      int var7 = this.gemSlots(var2, var3);
      var1.add(color(this.loreText.format("gem-slots", "&3寶石孔：&f{used}&7 / &f{total}", "used", var4.size(), "total", var7)));
      if (var4.isEmpty()) {
         var1.add(color(var7 > 0 ? this.loreText.get("gem-empty", "&8尚未鑲嵌寶石。") : this.loreText.get("gem-unsupported", "&8這件物品沒有寶石孔。")));
      } else {
         var4.forEach((var2x) -> var1.add(color(this.loreText.format("gem-entry", "&b◆ {gem}", "gem", var2x.display()))));
      }

      var1.add(Component.empty());
      var1.add(color(this.loreText.format("rune-slots", "&6符文孔：&f{used}&7 / &f{total}", "used", var5 == null ? 0 : 1, "total", var2.runeSlots())));
      var1.add(color(var5 == null ? this.loreText.get("rune-empty", "&8尚未裝配符文。") : this.loreText.format("rune-entry", "&6◆ {rune}", "rune", var5.display())));
      var1.add(Component.empty());
      if (var6 != null && !var6.isBlank()) {
         var1.add(color(this.loreText.format("soulbound", "&c靈魂綁定：&f{owner}", "owner", var6)));
      } else {
         var1.add(color(this.loreText.get("tradeable", "&a交易狀態：&f可交易")));
      }

   }

   private int tooltipPageCount(ItemTemplate var1) {
      if (!this.getConfig().getBoolean("tooltips.multi-page.enabled", true)) {
         return 3;
      } else if (var1.tooltipPages() > 0) {
         return var1.tooltipPages();
      } else {
         String var2 = this.browserCategoryFor(var1.type()).id().equals("OTHER") ? "tooltips.multi-page.other-pages" : "tooltips.multi-page.equipment-pages";
         return this.getConfig().getInt(var2, var2.endsWith("other-pages") ? 3 : 5) >= 5 ? 5 : 3;
      }
   }

   private ItemTemplate.AbilityData resolveAbility(ItemTemplate var1, List<AffixTemplate> var2, List<ItemPowerTemplate> var3, ItemPowerTemplate var4) {
      return (ItemTemplate.AbilityData)var2.stream().map(AffixTemplate::ability).filter(ItemTemplate.AbilityData::enabled).findFirst().orElseGet(() -> (ItemTemplate.AbilityData)var3.stream().map(ItemPowerTemplate::ability).filter(ItemTemplate.AbilityData::enabled).findFirst().orElse(var4 != null && var4.ability().enabled() ? var4.ability() : var1.ability()));
   }

   private void applyTooltipStyle(ItemMeta var1, TierProfile var2) {
      if (this.getConfig().getBoolean("tooltips.custom-style.enabled", true)) {
         String var5 = this.getConfig().getString("tooltips.custom-style.namespace", "mmoitems");
         if (var5 == null || var5.isBlank()) {
            var5 = "mmoitems";
         }

         String var10000 = var5.toLowerCase(Locale.ROOT);
         NamespacedKey var4;
         if ((var4 = NamespacedKey.fromString(var10000 + ":" + this.canonicalTierId(var2.id()).toLowerCase(Locale.ROOT))) != null) {
            var1.setTooltipStyle(var4);
         }
      }

   }

   private String affixDisplay(AffixTemplate var1) {
      String var2 = plainText(var1.prefix());
      String var3 = plainText(var1.suffix());
      String var4 = (var2 + (!var2.isBlank() && !var3.isBlank() ? " " : "") + var3).trim();
      return var4.isBlank() ? var1.id() : var4;
   }

   private void addCategorizedStats(List<Component> var1, Map<String, Double> var2, Set<WeaponStatCatalog.Category> var3) {
      LinkedHashMap<String, Double> var4 = new LinkedHashMap<>();

      for(Map.Entry var6 : var2.entrySet()) {
         String var7 = WeaponStatCatalog.normalize((String)var6.getKey());
         Double var8 = (Double)var6.getValue();
         if (!var7.isBlank() && !WeaponStatCatalog.hiddenInLore(var7) && var8 != null && Double.isFinite(var8) && !(Math.abs(var8) <= 1.0E-6)) {
            var4.merge(var7, var8, Double::sum);
         }
      }

      if (!var4.isEmpty()) {
         for(WeaponStatCatalog.CategoryInfo var14 : WeaponStatCatalog.orderedCategories()) {
            WeaponStatCatalog.Category var15 = WeaponStatCatalog.categoryOf(var14.id());
            if (var15 != null && var3.contains(var15)) {
               boolean var16 = false;

               for(String var10 : WeaponStatCatalog.knownKeys()) {
                  Double var11 = (Double)var4.get(var10);
                  if (var11 != null) {
                     WeaponStatCatalog.Info var12 = WeaponStatCatalog.find(var10).orElse(null);
                     if (var12 != null && var12.category() == var15) {
                        if (!var16) {
                           var1.add(color(WynnLoreStyle.categoryHeader(var14.color(), var14.icon(), var14.displayName())));
                           var16 = true;
                        }

                        var1.add(color(WynnLoreStyle.identification(new WynnLoreStyle.Identification(var12.displayName(), var11, var12.suffix()))));
                     }
                  }
               }
            }
         }

      }
   }

   private TooltipProfile tooltip(ItemTemplate var1, TierProfile var2, ItemTypeProfile var3) {
      String var4 = this.tooltipId(var1, var2, var3);
      if (var4.isBlank()) {
         return null;
      } else {
         TooltipProfile var5 = (TooltipProfile)this.tooltips.get(var4.toUpperCase(Locale.ROOT));
         return var5 != null && var5.enabled() ? var5 : null;
      }
   }

   private String tooltipId(ItemTemplate var1, TierProfile var2, ItemTypeProfile var3) {
      if (!var1.tooltip().isBlank()) {
         return var1.tooltip().toUpperCase(Locale.ROOT);
      } else if (var2.tooltip() != null && !var2.tooltip().isBlank()) {
         return var2.tooltip().toUpperCase(Locale.ROOT);
      } else {
         return var3.hasTooltip() ? var3.tooltip().toUpperCase(Locale.ROOT) : "";
      }
   }

   private String typeDisplay(ItemTemplate var1, ItemTypeProfile var2) {
      return !var1.displayedType().isBlank() ? var1.displayedType() : var2.displayName();
   }

   private boolean isWeapon(ItemTemplate var1) {
      return this.browserCategoryFor(var1.type()).id().equals("WEAPONS");
   }

   private boolean isEquipment(ItemTemplate var1) {
      return this.browserCategoryFor(var1.type()).id().equals("EQUIPMENT");
   }

   private double displayedWeaponDamage(Map<String, Double> var1) {
      double var2 = positiveStat(var1, "ATTACK_DAMAGE") + positiveStat(var1, "WEAPON_DAMAGE") + positiveStat(var1, "MAIN_ATTACK_DAMAGE") + positiveStat(var1, "MAGIC_DAMAGE") + positiveStat(var1, "FIRE_DAMAGE") + positiveStat(var1, "ICE_DAMAGE") + positiveStat(var1, "THUNDER_DAMAGE") + positiveStat(var1, "WIND_DAMAGE") + positiveStat(var1, "EARTH_DAMAGE") + positiveStat(var1, "WATER_DAMAGE") + positiveStat(var1, "DARKNESS_DAMAGE") + positiveStat(var1, "LIGHTNESS_DAMAGE") + positiveStat(var1, "ARCANE_DAMAGE") + positiveStat(var1, "NATURE_DAMAGE");
      double var4 = positiveStat(var1, "MAIN_ATTACK_DAMAGE_PERCENT") + positiveStat(var1, "ALL_DAMAGE") + positiveStat(var1, "PROJECTILE_DAMAGE") + positiveStat(var1, "PHYSICAL_DAMAGE");
      return Math.max((double)0.0F, var2 * ((double)1.0F + var4 / (double)100.0F));
   }

   private double displayedHitsPerSecond(ItemTemplate var1, Map<String, Double> var2) {
      double var7 = this.defaultHitsPerSecond(var1.type());
      double var9 = var7 * ((double)1.0F + (Double)var2.getOrDefault("ATTACK_SPEED", (double)0.0F) / (double)100.0F) + (Double)var2.getOrDefault("ATTACK_SPEED_TIER", (double)0.0F) * 0.15;
      return !Double.isFinite(var9) ? (double)1.0F : Math.max(0.35, Math.min((double)6.0F, var9));
   }

   private double defaultHitsPerSecond(String var1) {
      double var10000;
      switch (StatSnapshot.normalize(var1)) {
         case "DAGGER":
         case "KATANA":
         case "GAUNTLET":
            var10000 = 1.9;
            break;
         case "SWORD":
         case "LONG_SWORD":
         case "THRUSTING_SWORD":
            var10000 = (double)1.5F;
            break;
         case "BOW":
         case "GREATBOW":
         case "CROSSBOW":
         case "MUSKET":
            var10000 = 1.2;
            break;
         case "STAFF":
         case "GREATSTAFF":
         case "WAND":
         case "TOME":
         case "CATALYST":
         case "MAIN_CATALYST":
         case "OFF_CATALYST":
         case "LUTE":
            var10000 = 1.15;
            break;
         case "SPEAR":
         case "LANCE":
         case "HALBERD":
         case "WHIP":
            var10000 = 1.05;
            break;
         case "AXE":
         case "HAMMER":
            var10000 = 0.95;
            break;
         case "GREATSWORD":
         case "GREATAXE":
         case "GREATHAMMER":
            var10000 = (double)0.75F;
            break;
         default:
            var10000 = (double)1.0F;
      }

      return var10000;
   }

   private static double positiveStat(Map<String, Double> var0, String var1) {
      double var2 = (Double)var0.getOrDefault(StatSnapshot.normalize(var1), (double)0.0F);
      return Double.isFinite(var2) ? Math.max((double)0.0F, var2) : (double)0.0F;
   }

   private String attackSpeedLabel(double var1) {
      return this.attackSpeedTiers.label(var1);
   }

   private static String damageRange(double var0) {
      int var2 = Math.max(1, (int)Math.floor(var0 * 0.78));
      int var3 = Math.max(var2, (int)Math.ceil(var0 * 1.22));
      return var2 + "-" + var3;
   }

   private String classRequirementDisplay(String var1) {
      if (var1 != null && !var1.isBlank()) {
         ArrayList var2 = new ArrayList();

         for(String var6 : var1.split("[,;/|]+")) {
            String var7 = this.translateClassName(var6.trim());
            if (!var7.isBlank()) {
               var2.add(var7);
            }
         }

         return var2.isEmpty() ? this.translateClassName(var1) : String.join(this.loreText.get("class-separator", " / "), var2);
      } else {
         return this.loreText.get("class-any", "不限職業");
      }
   }

   private static String requirementSkillDisplay(String var0) {
      return (String)WeaponStatCatalog.find(var0).map(WeaponStatCatalog.Info::displayName).orElse(var0);
   }

   private String translateClassName(String var1) {
      String var2 = StatSnapshot.normalize(var1);
      return this.classDisplays.get(var2, var1 == null ? "" : var1);
   }

   private void scaleStats(Map<String, Double> var1, TierProfile var2, int var3) {
      double var4 = 0.9 + (double)var3 / (double)100.0F * 0.2;
      double var6 = var2.statMultiplier() * var4;

      for(Map.Entry var9 : new ArrayList<>(var1.entrySet())) {
         if (scalesWithPower((String)var9.getKey())) {
            var1.put((String)var9.getKey(), this.safeStatValue((String)var9.getKey(), (Double)var9.getValue() * var6));
         }
      }

   }

   private void applyUpgrade(Map<String, Double> var1, ItemTemplate var2, int var3) {
      if (var3 > 0) {
         UpgradeProfile var4 = (UpgradeProfile)this.upgrades.get(var2.upgradeTemplate());
         if (var4 != null) {
            var4.apply(var1, var3);
         } else {
            double var5 = (double)1.0F + (double)var3 * this.getConfig().getDouble("upgrade.positive-stat-growth", 0.04);

            for(Map.Entry var8 : new ArrayList<>(var1.entrySet())) {
               if (scalesWithPower((String)var8.getKey())) {
                  var1.put((String)var8.getKey(), this.safeStatValue((String)var8.getKey(), (Double)var8.getValue() * var5));
               }
            }
         }
      }

   }

   private List<ItemPowerTemplate> resolvePowers(Map<String, ItemPowerTemplate> var1, List<String> var2, int var3, int var4) {
      if (var2 != null && !var2.isEmpty() && var4 > 0) {
         ArrayList var5 = new ArrayList();

         for(String var7 : var2) {
            if (var5.size() >= var4) {
               break;
            }

            ItemPowerTemplate var8 = (ItemPowerTemplate)var1.get(var7.toUpperCase(Locale.ROOT));
            if (var8 != null && var8.matches(var3)) {
               var5.add(var8);
            }
         }

         return var5;
      } else {
         return List.of();
      }
   }

   private ItemPowerTemplate resolvePower(Map<String, ItemPowerTemplate> var1, String var2, int var3) {
      if (var2 != null && !var2.isBlank()) {
         ItemPowerTemplate var4 = (ItemPowerTemplate)var1.get(var2.toUpperCase(Locale.ROOT));
         return var4 != null && var4.matches(var3) ? var4 : null;
      } else {
         return null;
      }
   }

   private int gemSlots(ItemTemplate var1, TierProfile var2) {
      return var1.gemSockets() > 0 ? var1.gemSockets() : Math.max(0, this.getConfig().getInt("sockets.default-per-tier." + var2.id(), this.getConfig().getInt("sockets.default", 1)));
   }

   private ScoreBreakdown scoreBreakdown(Map<String, Double> var1) {
      double var2 = (double)0.0F;
      double var4 = (double)0.0F;
      double var6 = (double)0.0F;
      double var8 = (double)0.0F;

      for(Map.Entry var11 : var1.entrySet()) {
         String var12 = StatSnapshot.normalize((String)var11.getKey());
         double var13 = var11.getValue() == null ? (double)0.0F : this.safeStatValue(var12, (Double)var11.getValue());
         if (!(Math.abs(var13) <= 1.0E-6)) {
            double var15 = Math.max((double)0.0F, var13);
            switch (var12) {
               case "ATTACK_DAMAGE":
                  var2 += var15 * 1.18;
                  break;
               case "WEAPON_DAMAGE":
               case "MAGIC_DAMAGE":
               case "SKILL_DAMAGE":
               case "PHYSICAL_DAMAGE":
               case "PROJECTILE_DAMAGE":
               case "PVE_DAMAGE":
               case "PVP_DAMAGE":
                  var2 += var15 * 0.62;
                  break;
               case "FIRE_DAMAGE":
               case "ICE_DAMAGE":
               case "THUNDER_DAMAGE":
               case "LIGHTNING_DAMAGE":
               case "WIND_DAMAGE":
               case "EARTH_DAMAGE":
               case "WATER_DAMAGE":
               case "DARKNESS_DAMAGE":
               case "SHADOW_DAMAGE":
               case "LIGHTNESS_DAMAGE":
               case "HOLY_DAMAGE":
               case "ARCANE_DAMAGE":
                  var2 += var15 * 0.78;
                  break;
               case "CRITICAL_STRIKE_CHANCE":
               case "SKILL_CRITICAL_STRIKE_CHANCE":
                  var2 += var15 * 1.45;
                  break;
               case "CRITICAL_STRIKE_POWER":
               case "SKILL_CRITICAL_STRIKE_POWER":
                  var2 += var15 * 0.38;
                  break;
               case "ATTACK_SPEED":
                  var2 += var15 * (double)11.0F;
                  break;
               case "ARROW_VELOCITY":
               case "RANGE":
                  var2 += var15 * 0.85;
                  break;
               case "DEFENSE":
                  var4 += var15 * 0.82;
                  break;
               case "ARMOR":
                  var4 += var15 * 1.05;
                  break;
               case "ARMOR_TOUGHNESS":
                  var4 += var15 * (double)1.25F;
                  break;
               case "MAX_HEALTH":
                  var4 += var15 * 0.42;
                  break;
               case "MAGIC_RESISTANCE":
               case "ELEMENTAL_RESISTANCE":
               case "DAMAGE_REDUCTION":
               case "FIRE_DAMAGE_REDUCTION":
               case "MAGIC_DAMAGE_REDUCTION":
               case "PROJECTILE_DAMAGE_REDUCTION":
               case "PHYSICAL_DAMAGE_REDUCTION":
               case "PVE_DAMAGE_REDUCTION":
               case "PVP_DAMAGE_REDUCTION":
                  var4 += var15 * 0.74;
                  break;
               case "BLOCK_POWER":
               case "BLOCK_RATING":
               case "DODGE_RATING":
               case "PARRY_RATING":
                  var4 += var15 * 0.92;
                  break;
               case "KNOCKBACK_RESISTANCE":
                  var4 += var15 * 0.55;
                  break;
               case "MAX_MANA":
               case "MAX_STAMINA":
                  var6 += var15 * 0.33;
                  break;
               case "HEALTH_REGENERATION":
               case "MANA_REGENERATION":
               case "STAMINA_REGENERATION":
                  var6 += var15 * 0.72;
                  break;
               case "COOLDOWN_REDUCTION":
                  var6 += var15 * 1.05;
                  break;
               case "MANA_COST":
               case "STAMINA_COST":
                  var6 -= var15 * 0.18;
                  break;
               case "LIFE_STEAL":
               case "SPELL_VAMPIRISM":
                  var8 += var15 * (double)3.25F;
                  break;
               case "MOVEMENT_SPEED":
                  var8 += var15 * (double)8.0F;
                  break;
               case "KNOCKBACK":
               case "BLUNT_POWER":
               case "BLUNT_RATING":
                  var8 += var15 * 0.65;
                  break;
               case "STRENGTH":
               case "DEXTERITY":
               case "INTELLIGENCE":
               case "WISDOM":
               case "VITALITY":
               case "RESILIENCE":
                  var8 += var15 * 0.82;
                  break;
               default:
                  var8 += var15 * (Double)this.scoreWeights.getOrDefault(var12, 0.3);
            }
         }
      }

      return new ScoreBreakdown(var2, var4, Math.max((double)0.0F, var6), var8);
   }

   private List<AffixTemplate> rollAffixes(ItemTemplate var1, TierProfile var2, int var3, Random var4) {
      ArrayList<AffixTemplate> var7 = new ArrayList<>(this.affixes.values().stream().filter((var3x) -> var3x.matches(var3, var1, var2)).filter((var0) -> var0.chance() > (double)0.0F).sorted(Comparator.comparing(AffixTemplate::id)).toList());
      if (var2.usesAffixBudget()) {
         double var16 = var2.rollAffixCapacity(var3, var4);
         int var17 = var2.maxAffixes() > 0 ? var2.maxAffixes() : var7.size();
         return AffixBudget.select(var7, var16, var17, var4);
      } else {
         ArrayList var8 = new ArrayList();
         LinkedHashSet var9 = new LinkedHashSet();
         boolean var10 = false;

         double var5;
         while(var8.size() < var2.maxAffixes() && !var7.isEmpty() && (var5 = var7.stream().mapToDouble(AffixTemplate::chance).sum()) > (double)0.0F && Double.isFinite(var5)) {
            double var11 = var4.nextDouble(var5);
            AffixTemplate var13 = (AffixTemplate)var7.getLast();

            for(AffixTemplate var15 : var7) {
               if ((var11 -= var15.chance()) <= (double)0.0F) {
                  var13 = var15;
                  break;
               }
            }

            var7.remove(var13);
            if (!var9.contains(var13.group()) && (!var10 || !var13.major())) {
               var8.add(var13);
               var9.add(var13.group());
               var10 |= var13.major();
            }
         }

         return List.copyOf(var8);
      }
   }

   private TierProfile tier(String var1) {
      if (this.tiers.isEmpty()) {
         return new TierProfile("COMMON", "&f凡品", "", DeconstructionProfile.empty(), (double)1.0F, 45, 78, 1, (double)1.0F, (StatFormula)null);
      } else {
         String var2 = this.canonicalTierId(var1);
         return (TierProfile)this.tiers.getOrDefault(var2, (TierProfile)this.tiers.getOrDefault("COMMON", (TierProfile)this.tiers.values().iterator().next()));
      }
   }

   private String resolveTierId(ItemTemplate var1, String var2) {
      String var4 = this.canonicalTierId(var1.tier());
      String var3 = var2 != null && !var2.isBlank() ? this.canonicalTierId(var2) : var4;
      if (var4.equals("STARTER")) {
         return "STARTER";
      } else {
         return var3.equals("STARTER") ? var4 : var3;
      }
   }

   private ItemTypeProfile itemType(ItemTemplate var1) {
      ItemTypeProfile var2 = (ItemTypeProfile)this.itemTypes.get(var1.type());
      return var2 == null ? ItemTypeProfile.fallback(var1.type(), var1.material()) : var2;
   }

   private List<BrowserCategory> browserCategories() {
      return this.browserCategoryLoader.categories();
   }

   private BrowserCategory browserCategoryFor(String var1) {
      String var2 = var1 == null ? "" : var1.toUpperCase(Locale.ROOT);
      ItemTypeProfile var3 = (ItemTypeProfile)this.itemTypes.get(var2);
      if (var3 != null && var3.category() != null && !var3.category().isBlank()) {
         for(BrowserCategory var5 : this.browserCategories()) {
            if (var5.id().equalsIgnoreCase(var3.category())) {
               return var5;
            }
         }
      }

      for(BrowserCategory var7 : this.browserCategories()) {
         if (var7.matches(var2)) {
            return var7;
         }
      }

      return (BrowserCategory)this.browserCategories().get(2);
   }

   private BrowserCategory browserCategoryById(String var1) {
      for(BrowserCategory var3 : this.browserCategories()) {
         if (var3.id().equalsIgnoreCase(var1) || plainText(var3.display()).equalsIgnoreCase(var1)) {
            return var3;
         }
      }

      return null;
   }

   private boolean canDeconstruct(TierProfile var1) {
      return this.getConfig().getBoolean("deconstruct.enabled", true) && !var1.id().equals("STARTER") && (var1.canDeconstruct() || this.getConfig().getBoolean("deconstruct.fallback-material", true));
   }

   private String defaultBrowserTier() {
      if (this.tiers.containsKey("RARE")) {
         return "RARE";
      } else {
         return this.tiers.isEmpty() ? "COMMON" : (String)this.tiers.keySet().iterator().next();
      }
   }

   private String normalizeTierId(String var1) {
      if (var1 != null && var1.equalsIgnoreCase("RANDOM")) {
         return this.randomTier().id();
      } else {
         String var2 = this.canonicalTierId(var1);
         return this.tiers.containsKey(var2) ? var2 : this.defaultBrowserTier();
      }
   }

   private String canonicalTierId(String var1) {
      if (var1 != null && !var1.isBlank()) {
         String var2 = var1.trim().toUpperCase(Locale.ROOT);
         return this.tierAliases.get(var2, var2);
      } else {
         return this.defaultTierId;
      }
   }

   private TierProfile randomTier() {
      double var1 = this.tiers.values().stream().mapToDouble(TierProfile::chance).sum();
      double var3 = ThreadLocalRandom.current().nextDouble() * Math.max(1.0E-4, var1);
      double var5 = (double)0.0F;

      for(TierProfile var8 : this.tiers.values()) {
         if (var3 <= (var5 += var8.chance())) {
            return var8;
         }
      }

      return this.tier("COMMON");
   }

   private void openCategoryBrowser(Player var1, int var2) {
      List var4 = this.browserCategories().stream().filter((var1x) -> !this.visibleTypes(var1x.id()).isEmpty()).toList();
      int var5 = this.maxPage(var4.size(), CATEGORY_SLOTS.length);
      BrowserHolder var6 = new BrowserHolder(MMOItemsPlugin.BrowserMode.CATEGORIES, "", "", "", this.defaultBrowserTier(), this.clampPage(var2, var5));
      Inventory var3;
      var6.inventory = var3 = Bukkit.createInventory(var6, 54, this.browserTitle(var1, this.messages.get("gui.browser.title-categories", "MMO 物品圖鑑｜主分類")));
      this.decorateBrowser(var3);
      var3.setItem(4, this.button(Material.BOOK, this.messages.get("gui.browser.header-categories", "&fMMO 物品圖鑑"), List.of(this.messages.get("gui.browser.header-categories-lore1", "&7請先選擇主分類。"), this.messages.get("gui.browser.header-categories-lore2", "&8武器、裝備、其他會分開顯示。"))));
      int var7 = var6.page * CATEGORY_SLOTS.length;

      for(int var8 = 0; var8 < CATEGORY_SLOTS.length && var7 + var8 < var4.size(); ++var8) {
         BrowserCategory var9 = (BrowserCategory)var4.get(var7 + var8);
         List var10 = this.visibleTypes(var9.id());
         long var11 = this.templates.values().stream().filter((var2x) -> this.browserCategoryFor(var2x.type()).id().equals(var9.id())).count();
         var3.setItem(CATEGORY_SLOTS[var8], this.button(var9.icon(), var9.display(), List.of(this.messages.format("gui.browser.category-description", "&7{description}", "description", var9.description()), this.messages.format("gui.browser.category-type-count", "&7物品種類：&f{count}", "count", var10.size()), this.messages.format("gui.browser.category-item-count", "&7可用物品：&f{count}", "count", var11), this.messages.get("gui.browser.category-hint", "&e左鍵：開啟分類"))));
      }

      this.setBrowserControls(var3, var6, var4.size(), var1);
      var1.openInventory(var3);
   }

   private void openTypeBrowser(Player var1, int var2) {
      this.openTypeBrowser(var1, "", var2);
   }

   private void openTypeBrowser(Player var1, String var2, int var3) {
      List var5 = this.visibleTypes(var2);
      int var6 = this.maxPage(var5.size());
      BrowserHolder var7 = new BrowserHolder(MMOItemsPlugin.BrowserMode.TYPES, var2 == null ? "" : var2, "", "", this.defaultBrowserTier(), this.clampPage(var3, var6));
      Inventory var4;
      var7.inventory = var4 = Bukkit.createInventory(var7, 54, this.browserTitle(var1, this.messages.get("gui.browser.title-types", "MMO 物品圖鑑｜種類")));
      this.decorateBrowser(var4);
      var4.setItem(4, this.button(Material.COMPASS, this.messages.get("gui.browser.header-types", "&f種類列表"), List.of(var7.category.isBlank() ? this.messages.get("gui.browser.types-all", "&7目前顯示全部種類。") : this.messages.format("gui.browser.types-filtered", "&7分類：&f{category}", "category", plainText(this.browserCategoryById(var7.category).display())), this.messages.get("gui.browser.header-types-lore", "&8選擇種類後會列出該種類的物品。"))));
      int var8 = var7.page * PAGE_SIZE;

      for(int var9 = 0; var9 < PAGE_SIZE && var8 + var9 < var5.size(); ++var9) {
         String var10 = (String)var5.get(var8 + var9);
         ItemTypeProfile var11 = (ItemTypeProfile)this.itemTypes.getOrDefault(var10, ItemTypeProfile.fallback(var10, Material.CHEST));
         long var12 = this.templates.values().stream().filter((var1x) -> var1x.type().equals(var10)).count();
         Material var14 = (Material)this.templates.values().stream().filter((var1x) -> var1x.type().equals(var10)).map(ItemTemplate::material).filter((var0) -> var0 != null && var0 != Material.AIR).findFirst().orElse(var11.icon());
         var4.setItem(BROWSER_CONTENT_SLOTS[var9], this.button(var14 == Material.CHEST ? var11.icon() : var14, var11.displayName(), List.of(this.messages.format("gui.browser.type-category", "&7所屬分類：&f{category}", "category", plainText(this.browserCategoryFor(var10).display())), this.messages.format("gui.browser.type-item-count", "&7可用物品：&f{count}", "count", var12), this.messages.get("gui.browser.type-hint", "&e左鍵：查看物品"), this.messages.get("gui.browser.type-hint-shift", "&6Shift 左鍵：使用隨機稀有度"))));
      }

      this.setBrowserControls(var4, var7, var5.size(), var1);
      var1.openInventory(var4);
   }

   private void openItemBrowser(Player var1, String var2, String var3, String var4, int var5) {
      String var7 = var2.toUpperCase(Locale.ROOT);
      String var8 = var3 == null ? "" : var3.toUpperCase(Locale.ROOT);
      String var9 = this.normalizeTierId(var4);
      boolean var10 = var9.equals("STARTER");
      List var11 = this.filteredTemplates(var7, var8).stream().filter((var2x) -> this.canonicalTierId(var2x.tier()).equals("STARTER") == var10).toList();
      int var12 = this.maxPage(var11.size());
      BrowserHolder var13 = new BrowserHolder(MMOItemsPlugin.BrowserMode.ITEMS, this.browserCategoryFor(var7).id(), var7, var8, var9, this.clampPage(var5, var12));
      ItemTypeProfile var14 = (ItemTypeProfile)this.itemTypes.getOrDefault(var7, ItemTypeProfile.fallback(var7, Material.CHEST));
      Inventory var6;
      var13.inventory = var6 = Bukkit.createInventory(var13, 54, this.browserTitle(var1, this.messages.format("gui.browser.title-items", "MMO 物品圖鑑｜{type}", "type", plainText(var14.displayName()))));
      this.decorateBrowser(var6);
      var6.setItem(4, this.button(var14.icon(), var14.displayName(), List.of(this.messages.format("gui.browser.items-tier", "&7目前階級：{tier}", "tier", this.tier(var9).display()), this.messages.format("gui.browser.items-count", "&7可用物品：&f{count}", "count", var11.size()), this.messages.get("gui.browser.items-hint", "&8左鍵領取樣品，右鍵查看 3D 預覽。"))));
      if (var11.isEmpty()) {
         var6.setItem(BROWSER_CONTENT_SLOTS[BROWSER_CONTENT_SLOTS.length / 2], this.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, this.messages.get("gui.browser.empty", "&7這個階級沒有可用物品"), List.of(this.messages.get("gui.browser.empty-hint", "&8按 &f切換階級 &8看看其他稀有度。"))));
      }

      int var15 = Math.max(1, var1.getLevel());
      int var16 = var13.page * PAGE_SIZE;

      for(int var17 = 0; var17 < PAGE_SIZE && var16 + var17 < var11.size(); ++var17) {
         ItemTemplate var18 = (ItemTemplate)var11.get(var16 + var17);
         ItemStack var19 = this.createItem(var18, var15, 1, var9, (Integer)null, 0, this.browserSeed(var18, var9, var15), (List)null, List.of(), "");
         ItemMeta var20 = var19.getItemMeta();
         ArrayList<Component> var21 = var20.hasLore() ? new ArrayList<>(var20.lore()) : new ArrayList<>();
         var21.add(Component.empty());
         var21.add(color(this.messages.get("gui.browser.item-actions", "&e圖鑑操作")));
         var21.add(color(this.messages.get("gui.browser.item-action-left", "&7左鍵：取得目前稀有度樣品")));
         var21.add(color(this.messages.get("gui.browser.item-action-shift-left", "&7Shift 左鍵：取得隨機稀有度物品")));
         var21.add(color(this.messages.get("gui.browser.item-action-right", "&b右鍵：查看旋轉 3D 預覽")));
         var21.add(color(this.messages.get("gui.browser.item-action-shift-right", "&7Shift 右鍵：取得重新鍛造的物品")));
         var21.add(Component.text("ID: " + var18.type() + "." + var18.id(), NamedTextColor.DARK_GRAY));
         var20.lore(var21.stream().map(MMOItemsPlugin::noItalic).toList());
         var19.setItemMeta(var20);
         var6.setItem(BROWSER_CONTENT_SLOTS[var17], var19);
      }

      this.setBrowserControls(var6, var13, var11.size(), var1);
      var1.openInventory(var6);
   }

   private void setBrowserControls(Inventory var1, BrowserHolder var2, int var3) {
      this.setBrowserControls(var1, var2, var3, (Player)null);
   }

   private void setBrowserControls(Inventory var1, BrowserHolder var2, int var3, Player var4) {
      int var5 = var2.mode == MMOItemsPlugin.BrowserMode.CATEGORIES ? CATEGORY_SLOTS.length : PAGE_SIZE;
      int var6 = this.maxPage(var3, var5) + 1;
      String var7 = this.messages.format("gui.browser.page-counter", "&7頁次：&f{page}&8 / &f{total}", "page", var2.page + 1, "total", var6);
      var1.setItem(45, this.button(Material.BARRIER, this.messages.get("gui.browser.close", "&c關閉"), List.of(this.messages.get("gui.browser.close-lore", "&8關閉物品圖鑑"))));
      var1.setItem(46, this.button(Material.ARROW, this.messages.get("gui.browser.previous-page", "&e上一頁"), List.of(var7, this.messages.get("gui.browser.previous-hint", "&8左鍵"))));
      var1.setItem(48, this.button(Material.COMPASS, this.messages.get("gui.browser.back", "&b返回上層"), List.of(this.messages.get("gui.browser.back-lore", "&8回到上一層分類"))));
      var1.setItem(49, this.button(Material.PAPER, this.messages.get("gui.browser.overview", "&f物品總覽"), List.of(this.messages.format("gui.browser.overview-total", "&7總物品數：&f{count}", "count", this.templates.size()), this.messages.format("gui.browser.overview-listed", "&7目前清單：&f{count}", "count", var3), this.messages.get("gui.browser.overview-hint", "&8F 鍵：下一頁（末頁繞回）"))));
      if (var2.mode == MMOItemsPlugin.BrowserMode.ITEMS) {
         var1.setItem(50, this.button(Material.NETHER_STAR, this.messages.get("gui.browser.tier-switch", "&6切換階級"), List.of(this.messages.format("gui.browser.tier-current", "&7目前：{tier}", "tier", this.tier(var2.tier).display()), this.messages.get("gui.browser.tier-hint", "&8左鍵下一個 / 右鍵上一個"))));
      } else {
         var1.setItem(50, this.button(Material.GRAY_DYE, this.messages.get("gui.browser.tier-locked", "&8階級切換"), List.of(this.messages.get("gui.browser.tier-locked-lore", "&8進入物品頁後可用"))));
      }

      var1.setItem(53, this.button(Material.ARROW, this.messages.get("gui.browser.next-page", "&e下一頁"), List.of(var7, this.messages.get("gui.browser.next-hint", "&8F 鍵或左鍵"))));
      if (var4 != null && var4.hasPermission("mmoitems.admin")) {
         var1.setItem(52, this.button(Material.COMMAND_BLOCK, this.messages.get("gui.browser.admin-panel", "&d管理面板"), List.of(this.messages.get("gui.browser.admin-panel-lore", "&7建立、複製與刪除物品模板"), this.messages.get("gui.browser.admin-panel-note", "&8僅管理員可見"))));
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onTooltipPageClick(InventoryClickEvent var1) {
      HumanEntity var2;
      if (var1.getClick() == ClickType.SWAP_OFFHAND && (var2 = var1.getWhoClicked()) instanceof Player) {
         Player var3 = (Player)var2;
         if (!(var1.getView().getTopInventory().getHolder() instanceof BrowserHolder)) {
            ItemStack var4 = var1.getCurrentItem();
            if (this.looksManaged(var4)) {
               var1.setCancelled(true);
               if (this.flipTooltipByHotkey(var3, var4)) {
                  var1.setCurrentItem(var4);
               }

            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onRequirementInteract(PlayerInteractEvent var1) {
      if (this.denyItemUse(var1.getPlayer(), var1.getItem(), true)) {
         var1.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onRequirementAttack(EntityDamageByEntityEvent var1) {
      Entity var3 = var1.getDamager();
      Player var2;
      if (var3 instanceof Player && this.denyItemUse(var2 = (Player)var3, var2.getInventory().getItemInMainHand(), true)) {
         var1.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onRequirementShoot(EntityShootBowEvent var1) {
      LivingEntity var3 = var1.getEntity();
      Player var2;
      if (var3 instanceof Player && this.denyItemUse(var2 = (Player)var3, var1.getBow(), true)) {
         var1.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onRequirementHeld(PlayerItemHeldEvent var1) {
      ItemStack var2 = var1.getPlayer().getInventory().getItem(var1.getNewSlot());
      this.denyItemUse(var1.getPlayer(), var2, false);
      this.refreshRequirementMarks(var1.getPlayer(), var2);
   }

   private void refreshRequirementMarks(Player var1, ItemStack var2) {
      if (var2 != null && var2.getType() != Material.AIR && var2.hasItemMeta()) {
         List<EquipmentRequirementReport.Entry> var3 = this.rpgCore.requirements(var1, var2);
         if (!var3.isEmpty()) {
            StringBuilder var4 = new StringBuilder(var3.size());

            for(EquipmentRequirementReport.Entry var6 : var3) {
               var4.append((char)(var6.met() ? '1' : '0'));
            }

            String var7 = var4.toString();
            if (!var7.equals(this.api.readItemTag(var2, "requirement_marks"))) {
               if (this.cycleTooltipPage(var2, 0, var1)) {
                  ItemMeta var8 = var2.getItemMeta();
                  this.api.writeItemTags(var8, Map.of("requirement_marks", var7));
                  var2.setItemMeta(var8);
               }

            }
         }
      }
   }

   private boolean denyItemUse(Player var1, ItemStack var2, boolean var3) {
      if (this.requirementService == null) {
         return false;
      } else {
         ItemRequirementService.Result var4 = this.requirementService.check(var1, var2);
         if (var4.usable()) {
            return false;
         } else {
            long var5 = System.nanoTime();
            Long var7 = (Long)this.requirementWarningNanos.put(var1.getUniqueId(), var5);
            if (var7 == null || var5 - var7 >= 750000000L) {
               var1.sendActionBar(color(this.messages.format("item.use-denied", "&c無法使用：&f{reason}", "reason", var4.message())));
            }

            return var3;
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onBrowserClick(InventoryClickEvent var1) {
      Inventory var2 = var1.getView().getTopInventory();
      InventoryHolder var3 = var2.getHolder();
      if (var3 instanceof BrowserHolder var4) {
         var1.setCancelled(true);
         HumanEntity var5 = var1.getWhoClicked();
         if (var5 instanceof Player var6) {
            if (var1.getClick() == ClickType.SWAP_OFFHAND) {
               this.flipBrowserByHotkey(var6, var4);
            } else if (var1.getClickedInventory() == var2 && var1.getRawSlot() >= 0 && var1.getRawSlot() < 54) {
               int var7 = var1.getRawSlot();
               if (this.isControlSlot(var7)) {
                  this.handleBrowserControl(var6, var4, var7, var1.getClick());
               } else if (var4.mode == MMOItemsPlugin.BrowserMode.CATEGORIES) {
                  int var8 = this.categorySlotIndex(var7);
                  if (var8 >= 0) {
                     this.handleCategoryClick(var6, var4, var8);
                  }
               } else {
                  int var9 = this.browserSlotIndex(var7);
                  if (var9 >= 0) {
                     if (var4.mode == MMOItemsPlugin.BrowserMode.TYPES) {
                        this.handleTypeClick(var6, var4, var9, var1.getClick());
                     } else {
                        this.handleItemClick(var6, var4, var9, var1.getClick());
                     }
                  }
               }
            }
         }
      }

   }

   private boolean cycleTooltipPage(ItemStack var1, int var2, Player var3) {
      if (var1 != null && var1.getType() != Material.AIR && var1.hasItemMeta()) {
         ItemTemplate var4 = (ItemTemplate)this.templates.get(key(this.api.readItemType(var1), this.api.readItemId(var1)));
         if (var4 == null) {
            return false;
         } else {
            TierProfile var5 = this.tier(this.api.readItemTag(var1, "tier"));
            ItemTypeProfile var6 = this.itemType(var4);
            int var7 = Math.max(1, this.api.readItemLevel(var1));
            int var8 = Math.max(1, Math.min(100, parseInt(this.api.readItemTag(var1, "quality"), 70)));
            int var9 = Math.max(0, parseInt(this.api.readItemTag(var1, "upgrade_level"), 0));
            List var10 = splitCsv(this.api.readItemTag(var1, "affixes")).stream().map((var1x) -> (AffixTemplate)this.affixes.get(var1x.toUpperCase(Locale.ROOT))).filter(Objects::nonNull).toList();
            List var11 = this.resolvePowers(this.gems, splitCsv(this.api.readItemTag(var1, "gems")), var7, this.gemSlots(var4, var5));
            ItemPowerTemplate var12 = this.resolvePower(this.runes, this.api.readItemTag(var1, "rune"), var7);
            ItemTemplate.AbilityData var13 = this.resolveAbility(var4, var10, var11, var12);
            List var14 = this.buildLorePages(var4, var6, var5, var8, var9, var10, var11, var12, var13, var7, this.api.readItemStats(var1), this.api.readItemTag(var1, "soulbound_name"), this.rpgCore.requirements(var3, var1));
            int var15 = Math.max(0, parseInt(this.api.readItemTag(var1, "tooltip_page"), 0));
            int var16 = var2 == 0 ? Math.min(var15, var14.size() - 1) : TooltipPagination.nextPage(var15, var14.size(), var2);
            ItemMeta var17 = var1.getItemMeta();
            var17.lore((List)var14.get(var16));
            this.applyTooltipStyle(var17, var5);
            this.api.writeItemTags(var17, Map.of("tooltip_page", Integer.toString(var16), "tooltip_pages", Integer.toString(var14.size())));
            var1.setItemMeta(var17);
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean acceptTooltipHotkey(Player var1) {
      return this.tooltipHotkey.claim(var1.getUniqueId());
   }

   private boolean flipTooltipByHotkey(Player var1, ItemStack var2) {
      if (this.acceptTooltipHotkey(var1) && this.cycleTooltipPage(var2, 1, var1)) {
         var1.playSound(var1.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.55F, 1.15F);
         return true;
      } else {
         return false;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onBrowserDrag(InventoryDragEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof BrowserHolder) {
         var1.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onBrowserSwapHands(PlayerSwapHandItemsEvent var1) {
      Player var2 = var1.getPlayer();
      Inventory var3 = var2.getOpenInventory().getTopInventory();
      InventoryHolder var4 = var3.getHolder();
      if (var4 instanceof BrowserHolder var5) {
         var1.setCancelled(true);
         this.flipBrowserByHotkey(var2, var5);
      } else {
         ItemStack var6 = var2.getInventory().getItemInMainHand();
         if (this.looksManaged(var6)) {
            var1.setCancelled(true);
            if (this.flipTooltipByHotkey(var2, var6)) {
               var2.getInventory().setItemInMainHand(var6);
            }
         }
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onSoulboundDrop(PlayerDropItemEvent var1) {
      if (this.shouldBlockSoulbound(var1.getPlayer(), var1.getItemDrop().getItemStack(), true)) {
         var1.setCancelled(true);
         var1.getPlayer().sendMessage(color(this.messages.get("item.soulbound-drop-blocked", "&c這件靈魂綁定裝備受到保護，無法丟棄。")));
      }

   }

   @EventHandler
   public void onPreviewOwnerQuit(PlayerQuitEvent var1) {
      this.removePreview(var1.getPlayer().getUniqueId());
      this.browserHotkey.forget(var1.getPlayer().getUniqueId());
      this.tooltipHotkey.forget(var1.getPlayer().getUniqueId());
      this.requirementWarningNanos.remove(var1.getPlayer().getUniqueId());
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onSoulboundPickup(EntityPickupItemEvent var1) {
      LivingEntity var3 = var1.getEntity();
      Player var2;
      if (var3 instanceof Player && this.shouldBlockSoulbound(var2 = (Player)var3, var1.getItem().getItemStack(), false)) {
         var1.setCancelled(true);
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onSoulboundInventoryMove(InventoryClickEvent var1) {
      Player var2;
      HumanEntity var3;
      if (!(var1.getInventory().getHolder() instanceof BrowserHolder) && (var3 = var1.getWhoClicked()) instanceof Player && (this.shouldBlockSoulbound(var2 = (Player)var3, var1.getCurrentItem(), false) || this.shouldBlockSoulbound(var2, var1.getCursor(), false))) {
         var1.setCancelled(true);
         var2.sendMessage(color(this.messages.get("item.soulbound-foreign-move", "&c你不能操作其他玩家的靈魂綁定裝備。")));
      }

   }

   private void handleBrowserControl(Player var1, BrowserHolder var2, int var3, ClickType var4) {
      int var5 = this.browserEntryCount(var2);
      switch (var3) {
         case 45:
            var1.closeInventory();
            break;
         case 46:
            this.reopenBrowser(var1, var2.withPage(var2.page - 1), var5);
         case 47:
         case 49:
         case 51:
         default:
            break;
         case 48:
            if (var2.mode == MMOItemsPlugin.BrowserMode.ITEMS) {
               this.openTypeBrowser(var1, var2.category, 0);
            } else {
               this.openCategoryBrowser(var1, 0);
            }
            break;
         case 50:
            if (var2.mode != MMOItemsPlugin.BrowserMode.ITEMS) {
               return;
            }

            String var6 = this.cycleTier(var2.tier, var4.isRightClick() ? -1 : 1);
            this.openItemBrowser(var1, var2.type.isBlank() ? this.firstType() : var2.type, var2.query, var6, var2.page);
            break;
         case 52:
            if (!var1.hasPermission("mmoitems.admin")) {
               return;
            }

            var1.playSound(var1.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6F, 1.35F);
            this.editorService.openIndex(var1, 0);
            break;
         case 53:
            this.reopenBrowser(var1, var2.withPage(var2.page + 1), var5);
      }

   }

   private void handleCategoryClick(Player var1, BrowserHolder var2, int var3) {
      int var4 = var2.page * CATEGORY_SLOTS.length + var3;
      List var5 = this.browserCategories().stream().filter((var1x) -> !this.visibleTypes(var1x.id()).isEmpty()).toList();
      if (var4 < var5.size()) {
         this.openTypeBrowser(var1, ((BrowserCategory)var5.get(var4)).id(), 0);
      }

   }

   private void handleTypeClick(Player var1, BrowserHolder var2, int var3, ClickType var4) {
      int var5 = var2.page * PAGE_SIZE + var3;
      List var6 = this.visibleTypes(var2.category);
      if (var5 < var6.size()) {
         this.openItemBrowser(var1, (String)var6.get(var5), "", var4.isShiftClick() ? this.randomTier().id() : this.defaultBrowserTier(), 0);
      }

   }

   private void handleItemClick(Player var1, BrowserHolder var2, int var3, ClickType var4) {
      int var5 = var2.page * PAGE_SIZE + var3;
      List var6 = this.filteredTemplates(var2.type, var2.query);
      if (var5 < var6.size()) {
         ItemTemplate var8 = (ItemTemplate)var6.get(var5);
         int var9 = Math.max(1, var1.getLevel());
         String var7 = var4.isShiftClick() ? this.randomTier().id() : var2.tier;
         if (var7.equalsIgnoreCase("RANDOM")) {
            var7 = this.randomTier().id();
         }

         long var11 = var4.isRightClick() ? ThreadLocalRandom.current().nextLong() : this.browserSeed(var8, var7, var9);
         ItemStack var13 = this.createItem(var8, var9, 1, var7, var4.isRightClick() ? null : 80, 0, var11, (List)null, List.of(), "");
         if (var4.isRightClick() && !var4.isShiftClick()) {
            var1.closeInventory();
            this.showThreeDimensionalPreview(var1, var13, var8);
         } else {
            var1.getInventory().addItem(new ItemStack[]{var13});
            var1.sendMessage(color(this.messages.format("gui.browser.granted", "&a物品圖鑑已給予 {item}（{tier}）", "item", plainText(var8.name()), "tier", var7)));
         }
      }

   }

   private void reopenBrowser(Player var1, BrowserHolder var2, int var3) {
      int var4 = var2.mode == MMOItemsPlugin.BrowserMode.CATEGORIES ? CATEGORY_SLOTS.length : PAGE_SIZE;
      int var5 = this.clampPage(var2.page, this.maxPage(var3, var4));
      switch (var2.mode.ordinal()) {
         case 0 -> this.openCategoryBrowser(var1, var5);
         case 1 -> this.openTypeBrowser(var1, var2.category, var5);
         case 2 -> this.openItemBrowser(var1, var2.type, var2.query, var2.tier, var5);
      }

   }

   private void flipBrowserByHotkey(Player var1, BrowserHolder var2) {
      if (this.browserHotkey.claim(var1.getUniqueId())) {
         int var3 = this.browserEntryCount(var2);
         int var4 = var2.mode == MMOItemsPlugin.BrowserMode.CATEGORIES ? CATEGORY_SLOTS.length : PAGE_SIZE;
         int var5 = TooltipPagination.nextPage(var2.page, this.maxPage(var3, var4) + 1);
         if (var5 == var2.page) {
            var1.playSound(var1.getLocation(), Sound.UI_BUTTON_CLICK, 0.35F, 0.75F);
         } else {
            this.reopenBrowser(var1, var2.withPage(var5), var3);
            var1.playSound(var1.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.65F, 1.18F);
         }
      }

   }

   private int browserEntryCount(BrowserHolder var1) {
      int var10000;
      switch (var1.mode.ordinal()) {
         case 0 -> var10000 = (int)this.browserCategories().stream().filter((var1x) -> !this.visibleTypes(var1x.id()).isEmpty()).count();
         case 1 -> var10000 = this.visibleTypes(var1.category).size();
         case 2 -> var10000 = this.filteredTemplates(var1.type, var1.query).size();
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private List<ItemTemplate> filteredTemplates(String var1, String var2) {
      String var3 = var1 == null ? "" : var1.toUpperCase(Locale.ROOT);
      String var4 = var2 == null ? "" : var2.toUpperCase(Locale.ROOT);
      return this.templates.values().stream().filter((var1x) -> var3.isBlank() || var1x.type().equals(var3)).filter((var1x) -> var4.isBlank() || var1x.id().contains(var4) || var1x.name().toUpperCase(Locale.ROOT).contains(var4)).sorted(Comparator.comparingInt(MMOItemsPlugin::requiredLevelOrder).thenComparingDouble(ItemTemplate::browserIndex).thenComparing(ItemTemplate::type).thenComparing(ItemTemplate::id)).toList();
   }

   private static int requiredLevelOrder(ItemTemplate var0) {
      return var0.requiredLevel() <= 0 ? Integer.MAX_VALUE : var0.requiredLevel();
   }

   private void decorateBrowser(Inventory var1) {
      if (this.getConfig().getBoolean("browser.vanilla-layout.fill-border", true)) {
         ItemStack var2 = this.browserFiller(Material.GRAY_STAINED_GLASS_PANE);

         for(int var3 = 0; var3 < 54; ++var3) {
            if (!this.isBrowserContentSlot(var3) && !this.isControlSlot(var3) && var3 != 4) {
               var1.setItem(var3, var2);
            }
         }
      }

   }

   private ItemStack browserFiller(Material var1) {
      ItemStack var2 = new ItemStack(var1);
      ItemMeta var3 = var2.getItemMeta();
      var3.displayName(Component.empty());
      var3.lore(List.of());
      var2.setItemMeta(var3);
      return var2;
   }

   private boolean isControlSlot(int var1) {
      boolean var10000;
      switch (var1) {
         case 45:
         case 46:
         case 48:
         case 49:
         case 50:
         case 52:
         case 53:
            var10000 = true;
            break;
         case 47:
         case 51:
         default:
            var10000 = false;
      }

      return var10000;
   }

   private boolean isBrowserContentSlot(int var1) {
      return this.browserSlotIndex(var1) >= 0;
   }

   private int browserSlotIndex(int var1) {
      for(int var2 = 0; var2 < BROWSER_CONTENT_SLOTS.length; ++var2) {
         if (BROWSER_CONTENT_SLOTS[var2] == var1) {
            return var2;
         }
      }

      return -1;
   }

   private int categorySlotIndex(int var1) {
      for(int var2 = 0; var2 < CATEGORY_SLOTS.length; ++var2) {
         if (CATEGORY_SLOTS[var2] == var1) {
            return var2;
         }
      }

      return -1;
   }

   private ItemStack button(Material var1, String var2, List<String> var3) {
      ItemStack var4 = new ItemStack(var1 != null && var1 != Material.AIR ? var1 : Material.PAPER);
      ItemMeta var5 = var4.getItemMeta();
      var5.displayName(color(var2));
      var5.lore(var3.stream().map(MMOItemsPlugin::color).toList());
      var5.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE});
      var4.setItemMeta(var5);
      return var4;
   }

   private String cycleTier(String var1, int var2) {
      ArrayList var3 = new ArrayList(this.tiers.keySet());
      if (var3.isEmpty()) {
         return "COMMON";
      } else {
         int var4 = var3.indexOf(var1.toUpperCase(Locale.ROOT));
         if (var4 < 0) {
            var4 = 0;
         }

         return (String)var3.get(Math.floorMod(var4 + var2, var3.size()));
      }
   }

   private String firstType() {
      return (String)this.visibleTypes("").stream().findFirst().orElse("SWORD");
   }

   private List<String> visibleTypes(String var1) {
      boolean var2 = this.getConfig().getBoolean("display.show-hidden-types", false);
      BrowserCategory var3 = var1 != null && !var1.isBlank() ? this.browserCategoryById(var1) : null;
      return this.templates.values().stream().map(ItemTemplate::type).distinct().filter((var2x) -> var2 || !((ItemTypeProfile)this.itemTypes.getOrDefault(var2x, ItemTypeProfile.fallback(var2x, Material.CHEST))).hidden()).filter((var2x) -> var3 == null || this.browserCategoryFor(var2x).id().equals(var3.id())).sorted(Comparator.<String>comparingDouble((var1x) -> ((ItemTypeProfile)this.itemTypes.getOrDefault(var1x, ItemTypeProfile.fallback(var1x, Material.CHEST))).browserIndex()).thenComparing(Comparator.naturalOrder())).toList();
   }

   private int maxPage(int var1) {
      return this.maxPage(var1, PAGE_SIZE);
   }

   private int maxPage(int var1, int var2) {
      return Math.max(0, (var1 - 1) / Math.max(1, var2));
   }

   private int clampPage(int var1, int var2) {
      return Math.max(0, Math.min(var2, var1));
   }

   private long browserSeed(ItemTemplate var1, String var2, int var3) {
      String var4 = var1.type();
      return (long)(var4 + "." + var1.id() + ":" + var2 + ":" + var3).hashCode();
   }

   private Component browserTitle(Player var1, String var2) {
      return RpgGuiTitle.browser(this, var2);
   }

   private void showThreeDimensionalPreview(Player var1, ItemStack var2, ItemTemplate var3) {
      if (!this.getConfig().getBoolean("browser.three-dimensional-preview.enabled", true)) {
         var1.sendMessage(color(this.messages.get("gui.preview.disabled", "&e3D 預覽目前已由管理員關閉。")));
      } else {
         this.removePreview(var1.getUniqueId());
         double var4 = Math.max(1.2, Math.min((double)4.0F, this.getConfig().getDouble("browser.three-dimensional-preview.distance", 2.2)));
         double var6 = Math.max(0.4, Math.min((double)2.5F, this.getConfig().getDouble("browser.three-dimensional-preview.scale", 1.15)));
         int var8 = Math.max(40, Math.min(600, this.getConfig().getInt("browser.three-dimensional-preview.duration-ticks", 160)));
         int var9 = Math.max(1, Math.min(10, this.getConfig().getInt("browser.three-dimensional-preview.update-period-ticks", 2)));
         Vector var10 = var1.getEyeLocation().getDirection().normalize();
         Location var11 = var1.getEyeLocation().add(var10.multiply(var4)).subtract((double)0.0F, 0.45, (double)0.0F);
         ItemDisplay var12 = (ItemDisplay)var1.getWorld().spawn(var11, ItemDisplay.class, (var4x) -> {
            var4x.setItemStack(var2);
            var4x.setItemDisplayTransform(ItemDisplayTransform.FIXED);
            var4x.setBillboard(Billboard.FIXED);
            var4x.setPersistent(false);
            var4x.setInvulnerable(true);
            var4x.setGravity(false);
            var4x.setViewRange(0.35F);
            var4x.setShadowRadius(0.18F);
            var4x.setShadowStrength(0.35F);
            var4x.setInterpolationDuration(var9 + 1);
            var4x.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f((float)var6), new Quaternionf()));
         });
         var12.setVisibleByDefault(false);
         var1.showEntity(this, var12);
         this.activePreviews.put(var1.getUniqueId(), var12);
         AtomicInteger var13 = new AtomicInteger();
         var12.getScheduler().runAtFixedRate(this, (var8x) -> {
            int var9x = var13.addAndGet(var9);
            if (var1.isOnline() && var12.isValid() && var9x < var8) {
               float var10a = (float)((double)var9x * Math.PI / (double)36.0F);
               var12.setInterpolationDelay(0);
               var12.setTransformation(new Transformation(new Vector3f(0.0F, (float)(Math.sin((double)var10a * (double)0.5F) * 0.04), 0.0F), (new Quaternionf()).rotateY(var10a).rotateZ((float)Math.sin((double)var10a) * 0.08F), new Vector3f((float)var6), new Quaternionf()));
            } else {
               var8x.cancel();
               this.activePreviews.remove(var1.getUniqueId(), var12);
               if (var12.isValid()) {
                  var12.remove();
               }
            }

         }, () -> this.activePreviews.remove(var1.getUniqueId(), var12), 1L, (long)var9);
         var1.sendMessage(color(this.messages.format("gui.preview.started", "&b正在預覽「{item}」；移動視角即可從不同角度查看。", "item", plainText(var3.name()))));
      }

   }

   private void removePreview(UUID var1) {
      ItemDisplay var2 = (ItemDisplay)this.activePreviews.remove(var1);
      if (var2 != null && var2.isValid()) {
         EntityScheduler var3 = var2.getScheduler();
         Objects.requireNonNull(var2);
         var3.execute(this, () -> var2.remove(), (Runnable)null, 1L);
      }

   }

   private boolean shouldBlockSoulbound(Player var1, ItemStack var2, boolean var3) {
      if (var1 != null && var2 != null && var2.getType() != Material.AIR && var2.hasItemMeta()) {
         String var4 = this.api.readItemTag(var2, "soulbound_uuid");
         if (!var4.isBlank() && !var1.hasPermission("mmoitems.soulbound.bypass")) {
            boolean var5 = var4.equalsIgnoreCase(var1.getUniqueId().toString());
            if (!var5) {
               return this.getConfig().getBoolean("soulbound.prevent-foreign-use", true);
            } else if (var3 && this.getConfig().getBoolean("soulbound.prevent-owner-drop", true)) {
               return !this.getConfig().getBoolean("soulbound.allow-owner-drop-while-sneaking", false) || !var1.isSneaking();
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean blockForeignSoulboundUse(Player var1, ItemStack var2, CommandSender var3) {
      if (!this.shouldBlockSoulbound(var1, var2, false)) {
         return false;
      } else {
         var3.sendMessage(color(this.messages.get("item.soulbound-foreign-use", "&c這件裝備已靈魂綁定給其他玩家，無法操作。")));
         return true;
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var2.getName().equalsIgnoreCase("updateitem")) {
         return this.refreshHeld(var1);
      } else {
         boolean var10000;
         switch (var4.length == 0 ? "help" : var4[0].toLowerCase(Locale.ROOT)) {
            case "reload":
               var10000 = this.reloadCommand(var1);
               break;
            case "list":
               var10000 = this.listCommand(var1, var4);
               break;
            case "give":
               var10000 = this.giveCommand(var1, var3, var4, false);
               break;
            case "roll":
               var10000 = this.giveCommand(var1, var3, var4, true);
               break;
            case "browse":
               var10000 = this.browseCommand(var1, var4);
               break;
            case "editor":
            case "edit":
               var10000 = this.editorCommand(var1);
               break;
            case "craft":
            case "crafting":
               var10000 = this.craftingCommand(var1);
               break;
            case "forge":
            case "station":
               var10000 = this.forgeCommand(var1);
               break;
            case "shop":
            case "market":
               var10000 = this.shopCommand(var1);
               break;
            case "gold":
            case "currency":
               var10000 = this.goldCommand(var1, var4);
               break;
            case "validate":
            case "selftest":
            case "check":
               var10000 = this.validateCommand(var1);
               break;
            case "identify":
               var10000 = this.identifyCommand(var1);
               break;
            case "upgrade":
               var10000 = this.upgradeCommand(var1, var4);
               break;
            case "gem":
               var10000 = this.gemCommand(var1, var4);
               break;
            case "gems":
               var10000 = this.listPowerCommand(var1, this.gems, this.messages.get("command.list-power.gems", "寶石"));
               break;
            case "rune":
               var10000 = this.runeCommand(var1, var4);
               break;
            case "runes":
               var10000 = this.listPowerCommand(var1, this.runes, this.messages.get("command.list-power.runes", "符文"));
               break;
            case "reforge":
            case "reroll":
               var10000 = this.reforgeCommand(var1, var4);
               break;
            case "deconstruct":
            case "salvage":
            case "breakdown":
               var10000 = this.deconstructCommand(var1, var4);
               break;
            case "soulbound":
            case "bind":
            case "soulbind":
               var10000 = this.soulboundCommand(var1, var4);
               break;
            default:
               var10000 = this.help(var1, var3);
         }

         return var10000;
      }
   }

   private boolean reloadCommand(CommandSender var1) {
      if (!var1.hasPermission("mmoitems.admin")) {
         var1.sendMessage(color(this.messages.get("command.reload.no-permission", "&c你沒有權限重載 MMOItems。")));
         return true;
      } else {
         this.reloadAll();
         if (this.shopService != null) {
            this.shopService.reload();
         }

         if (this.craftingService != null) {
            this.craftingService.reload();
         }

         if (this.mythicDropService != null) {
            this.mythicDropService.reload();
         }

         var1.sendMessage(color(this.messages.format("command.reload.done", "&aMMOItems 已重新載入：模板 {templates}、類型 {types}、tooltip {tooltips}、詞綴 {affixes}、套裝 {sets}。", "templates", this.templates.size(), "types", this.itemTypes.size(), "tooltips", this.tooltips.size(), "affixes", this.affixes.size(), "sets", this.sets.size())));
         return true;
      }
   }

   private boolean validateCommand(CommandSender var1) {
      if (!var1.hasPermission("mmoitems.admin")) {
         var1.sendMessage(color(this.messages.get("command.validate.no-permission", "&c你沒有權限執行資料檢查。")));
         return true;
      } else {
         MMOValidationService.ValidationReport var2 = this.validationService.validate();
         String var3 = var2.summary();
         var1.sendMessage(Component.text("MMOItems 完整資料檢查：" + var3, var2.passed() ? NamedTextColor.GREEN : NamedTextColor.RED));
         var2.errors().stream().limit(30L).forEach((var2x) -> var1.sendMessage(color(this.messages.format("command.validate.error", "&c錯誤：{detail}", "detail", var2x))));
         var2.warnings().stream().limit(20L).forEach((var2x) -> var1.sendMessage(color(this.messages.format("command.validate.warning", "&e警告：{detail}", "detail", var2x))));
         return true;
      }
   }

   private boolean editorCommand(CommandSender var1) {
      if (var1 instanceof Player var2) {
         if (!var1.hasPermission("mmoitems.admin")) {
            var1.sendMessage(color(this.messages.get("command.editor.no-permission", "&c你沒有權限使用物品編輯器。")));
            return true;
         } else {
            this.editorService.openIndex(var2, 0);
            return true;
         }
      } else {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      }
   }

   private boolean craftingCommand(CommandSender var1) {
      if (var1 instanceof Player var2) {
         if (!var1.hasPermission("mmoitems.craft")) {
            var1.sendMessage(color(this.messages.get("command.craft.no-permission", "&c你沒有權限使用工藝台。")));
            return true;
         } else {
            this.craftingService.open(var2, 0);
            return true;
         }
      } else {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      }
   }

   private boolean forgeCommand(CommandSender var1) {
      if (var1 instanceof Player var2) {
         if (!var1.hasPermission("mmoitems.forge")) {
            var1.sendMessage(color(this.messages.get("command.forge.no-permission", "&c你沒有權限使用強化台。")));
            return true;
         } else {
            this.forgeService.open(var2);
            return true;
         }
      } else {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      }
   }

   private boolean shopCommand(CommandSender var1) {
      if (var1 instanceof Player var2) {
         if (!var1.hasPermission("mmoitems.shop")) {
            var1.sendMessage(color(this.messages.get("command.shop.no-permission", "&c你沒有權限使用交易所。")));
            return true;
         } else {
            this.shopService.open(var2, 0);
            return true;
         }
      } else {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      }
   }

   private boolean goldCommand(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player var3)) {
         if (var2.length >= 4 && var2[1].equalsIgnoreCase("give")) {
            Player var11 = Bukkit.getPlayerExact(var2[2]);
            long var12 = Math.max(0L, parseLong(var2[3], 0L));
            if (var11 != null && var12 > 0L) {
               long var16 = this.currencyService.credit(var11, var12);
               var1.sendMessage(color(this.messages.format("command.gold.deposited-other", "&a已存入 {amount} 黃金至 {player} 的錢包。", "amount", var16, "player", var11.getName())));
            }

            return true;
         } else {
            var1.sendMessage(color(this.messages.get("command.gold.console-usage", "&e主控台用法：/mi gold give <玩家> <數量>")));
            return true;
         }
      } else if (!var1.hasPermission("mmoitems.gold")) {
         var1.sendMessage(color(this.messages.get("command.gold.no-permission", "&c你沒有權限使用黃金帳戶。")));
         return true;
      } else {
         switch (var2.length > 1 ? var2[1].toLowerCase(Locale.ROOT) : "balance") {
            case "deposit":
            case "存入":
               long var14 = this.currencyService.depositAll(var3);
               var1.sendMessage(color(this.messages.format("command.gold.deposited", "&6已存入 {amount} 黃金。", "amount", var14)));
               break;
            case "withdraw":
            case "領取":
               long var13 = var2.length > 2 ? Math.max(1L, parseLong(var2[2], 1L)) : 1L;
               long var8 = this.currencyService.withdrawItems(var3, var13);
               var1.sendMessage(color(this.messages.format("command.gold.withdrawn", "&6已領取 {amount} 個實體黃金。", "amount", var8)));
               break;
            case "give":
               if (var1.hasPermission("mmoitems.admin") && var2.length >= 4) {
                  Player var6 = Bukkit.getPlayerExact(var2[2]);
                  long var7 = Math.max(0L, parseLong(var2[3], 0L));
                  if (var6 != null && var7 > 0L) {
                     long var9 = this.currencyService.credit(var6, var7);
                     var1.sendMessage(color(this.messages.format("command.gold.given", "&a已給予 {player} {amount} 黃金。", "player", var6.getName(), "amount", var9)));
                  } else {
                     var1.sendMessage(color(this.messages.get("command.gold.invalid-target", "&c找不到玩家或數量無效。")));
                  }
               } else {
                  var1.sendMessage(color(this.messages.get("command.gold.usage", "&e/mi gold give <玩家> <數量>")));
               }
               break;
            default:
               List<Component> var15 = this.currencyService.balanceLore(var3);
               Objects.requireNonNull(var1);
               var15.forEach((var1x) -> var1.sendMessage(var1x));
         }

         return true;
      }
   }

   private boolean listCommand(CommandSender var1, String[] var2) {
      String var3 = var2.length > 1 ? var2[1].toUpperCase(Locale.ROOT) : "";
      BrowserCategory var4 = var3.isBlank() ? null : this.browserCategoryById(var3);
      boolean var5 = !var3.isBlank() && this.templates.values().stream().anyMatch((var1x) -> var1x.type().equalsIgnoreCase(var3));
      List<ItemTemplate> var6 = this.templates.values().stream().filter((var4x) -> this.matchesListFilter(var4x, var3, var4, var5)).sorted(Comparator.comparing(ItemTemplate::type).thenComparing(ItemTemplate::id)).toList();
      int var7 = var2.length > 2 ? Math.max(1, Math.min(200, parseInt(var2[2], 80))) : 80;
      var1.sendMessage(color(this.messages.format("command.list.header", "&bMMOItems 物品清單&7  共 {count} 筆", "count", var6.size())));
      AtomicInteger var8 = new AtomicInteger(1);
      var6.stream().limit((long)var7).forEach((var3x) -> this.sendTemplateListLine(var1, var8.getAndIncrement(), var3x));
      if (var6.size() > var7) {
         var1.sendMessage(color(this.messages.format("command.list.truncated", "&8還有 {remaining} 筆未顯示，輸入 /mi list <分類或類型> 200 可提高上限。", "remaining", var6.size() - var7)));
      }

      return true;
   }

   private boolean matchesListFilter(ItemTemplate var1, String var2, BrowserCategory var3, boolean var4) {
      if (var2 != null && !var2.isBlank()) {
         if (var4) {
            return var1.type().equalsIgnoreCase(var2);
         } else if (var1.id().contains(var2)) {
            return true;
         } else if (var3 != null && this.browserCategoryFor(var1.type()).id().equals(var3.id())) {
            return true;
         } else {
            ItemTypeProfile var5 = this.itemType(var1);
            return plainText(var1.name()).toUpperCase(Locale.ROOT).contains(var2) || plainText(this.typeDisplay(var1, var5)).toUpperCase(Locale.ROOT).contains(var2) || plainText(this.browserCategoryFor(var1.type()).display()).toUpperCase(Locale.ROOT).contains(var2);
         }
      } else {
         return true;
      }
   }

   private void sendTemplateListLine(CommandSender var1, int var2, ItemTemplate var3) {
      var1.sendMessage(((TextComponent)((TextComponent)Component.text(String.format(Locale.ROOT, "%02d. ", var2), NamedTextColor.DARK_GRAY).append(Component.text(this.readableTemplateName(var3), NamedTextColor.WHITE))).append(Component.text(" | ", NamedTextColor.DARK_GRAY))).append(Component.text(this.readableTypePath(var3), NamedTextColor.GRAY)));
      var1.sendMessage(Component.text("    ID: " + var3.type() + "." + var3.id(), NamedTextColor.DARK_GRAY));
   }

   private String readableTemplateName(ItemTemplate var1) {
      String var2 = plainText(var1.name()).trim();
      return var2.isBlank() ? this.humanizeId(var1.id()) : var2;
   }

   private String readableTypePath(ItemTemplate var1) {
      ItemTypeProfile var2 = this.itemType(var1);
      String var3 = plainText(this.browserCategoryFor(var1.type()).display());
      return var3 + " > " + plainText(this.typeDisplay(var1, var2));
   }

   private boolean browseCommand(CommandSender var1, String[] var2) {
      if (var1 instanceof Player var3) {
         if (!var1.hasPermission("mmoitems.browse")) {
            var1.sendMessage(color(this.messages.get("command.browse.no-permission", "&c你沒有權限開啟 MMOItems 物品圖鑑。")));
            return true;
         } else if (var2.length >= 2) {
            String var4 = var2[1].toUpperCase(Locale.ROOT);
            BrowserCategory var5 = this.browserCategoryById(var4);
            if (var5 != null && !this.templates.values().stream().anyMatch((var1x) -> var1x.type().equals(var4))) {
               this.openTypeBrowser(var3, var5.id(), 0);
               return true;
            } else if (!this.templates.values().stream().anyMatch((var1x) -> var1x.type().equals(var4))) {
               var1.sendMessage(color(this.messages.format("command.browse.not-found", "&c找不到分類或類型：{query}", "query", var4)));
               return true;
            } else {
               String var6 = var2.length >= 3 ? var2[2].toUpperCase(Locale.ROOT) : "";
               String var7 = var2.length >= 4 ? var2[3].toUpperCase(Locale.ROOT) : this.defaultBrowserTier();
               this.openItemBrowser(var3, var4, var6, var7, 0);
               return true;
            }
         } else {
            this.openCategoryBrowser(var3, 0);
            return true;
         }
      } else {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      }
   }

   private boolean giveCommand(CommandSender var1, String var2, String[] var3, boolean var4) {
      if (!var1.hasPermission("mmoitems.give")) {
         var1.sendMessage(color(this.messages.get("command.give.no-permission", "&c你沒有權限給予 MMOItems 物品。")));
         return true;
      } else if (var3.length < 4) {
         var1.sendMessage(Component.text("/" + var2 + " " + var3[0] + " <玩家> <類型> <ID> [等級] [數量] [階級|RANDOM]", NamedTextColor.YELLOW));
         return true;
      } else {
         Player var6 = Bukkit.getPlayerExact(var3[1]);
         if (var6 == null) {
            var1.sendMessage(color(this.messages.get("command.give.player-not-found", "&c找不到玩家。")));
            return true;
         } else {
            ItemTemplate var7 = (ItemTemplate)this.templates.get(key(var3[2], var3[3]));
            if (var7 == null) {
               var1.sendMessage(Component.text("找不到模板：" + var3[2] + "." + var3[3], NamedTextColor.RED));
               return true;
            } else {
               int var8 = this.safeLevel(var3.length > 4 ? parseInt(var3[4], 1) : Math.max(1, var6.getLevel()));
               int var9 = this.safeAmount(var3.length > 5 ? parseInt(var3[5], 1) : 1);
               String var5 = var4 ? this.randomTier().id() : (var3.length > 6 ? var3[6] : var7.tier());
               if (var5.equalsIgnoreCase("RANDOM")) {
                  var5 = this.randomTier().id();
               }

               var6.getInventory().addItem(new ItemStack[]{this.createItem(var7, var8, var9, var5, (Integer)null, 0, ThreadLocalRandom.current().nextLong(), (List)null, List.of(), "")});
               var1.sendMessage(color(this.messages.format("command.give.done", "&a已給予 {player} {type}.{id} [{tier}]", "player", var6.getName(), "type", var7.type(), "id", var7.id(), "tier", var5.toUpperCase(Locale.ROOT))));
               return true;
            }
         }
      }
   }

   private boolean identifyCommand(CommandSender var1) {
      if (var1 instanceof Player var2) {
         ItemStack var3 = var2.getInventory().getItemInMainHand();
         String var4 = this.api.readItemType(var3);
         var1.sendMessage(Component.text("Item: " + var4 + "." + this.api.readItemId(var3) + " Lv." + this.api.readItemLevel(var3), NamedTextColor.AQUA));

         for(String var6 : List.of("tier", "tier_name", "type_name", "displayed_type", "quality", "required_level", "required_class", "required_strength", "required_dexterity", "required_intelligence", "required_defence", "required_agility", "required_quests", "upgrade_level", "item_score", "affixes", "gem_slots", "gems", "rune", "set_id", "tooltip", "can_deconstruct", "soulbound_name", "soulbound_level", "major_identification", "major_identification_name", "ability_on_hit", "ability_chance", "ability_power")) {
            String var7 = this.api.readItemTag(var3, var6);
            if (!var7.isBlank()) {
               var1.sendMessage(Component.text(" - " + var6 + ": " + var7, NamedTextColor.DARK_GRAY));
            }
         }

         this.api.readItemStats(var3).forEach((var1x, var2x) -> var1.sendMessage(Component.text(" - " + var1x + ": " + format(var2x), NamedTextColor.GRAY)));
         return true;
      } else {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      }
   }

   private boolean upgradeCommand(CommandSender var1, String[] var2) {
      if (var1 instanceof Player var3) {
         if (!var1.hasPermission("mmoitems.upgrade")) {
            var1.sendMessage(color(this.messages.get("command.upgrade.no-permission", "&c你沒有權限強化 MMOItems 物品。")));
            return true;
         } else {
            ItemStack var4 = var3.getInventory().getItemInMainHand();
            if (this.blockForeignSoulboundUse(var3, var4, var1)) {
               return true;
            } else {
               ItemTemplate var5 = (ItemTemplate)this.templates.get(key(this.api.readItemType(var4), this.api.readItemId(var4)));
               if (var5 == null) {
                  var1.sendMessage(color(this.messages.get("item.not-managed", "&c手中物品不是新版 MMOItems 裝備。")));
                  return true;
               } else {
                  int var6 = var2.length > 1 ? Math.max(1, parseInt(var2[1], 1)) : 1;
                  int var7 = this.getConfig().getInt("upgrade.max-level", 20);
                  int var8 = Math.min(var7, parseInt(this.api.readItemTag(var4, "upgrade_level"), 0) + var6);
                  this.replaceHeld(var3, var5, var4, var8);
                  var1.sendMessage(color(this.messages.format("command.upgrade.done", "&a強化完成：+{level}", "level", var8)));
                  return true;
               }
            }
         }
      } else {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      }
   }

   private boolean refreshHeld(CommandSender var1) {
      if (var1 instanceof Player var2) {
         ItemStack var3 = var2.getInventory().getItemInMainHand();
         if (this.blockForeignSoulboundUse(var2, var3, var1)) {
            return true;
         } else {
            ItemTemplate var4 = (ItemTemplate)this.templates.get(key(this.api.readItemType(var3), this.api.readItemId(var3)));
            if (var4 == null) {
               var1.sendMessage(color(this.messages.get("item.not-managed", "&c手中物品不是新版 MMOItems 裝備。")));
               return true;
            } else {
               this.replaceHeld(var2, var4, var3, parseInt(this.api.readItemTag(var3, "upgrade_level"), 0));
               var1.sendMessage(color(this.messages.get("command.refresh.done", "&a已刷新手中裝備資料。")));
               return true;
            }
         }
      } else {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      }
   }

   private void replaceHeld(Player var1, ItemTemplate var2, ItemStack var3, int var4) {
      String var5 = this.api.readItemTag(var3, "tier");
      int var6 = parseInt(this.api.readItemTag(var3, "quality"), 70);
      long var7 = parseLong(this.api.readItemTag(var3, "instance_seed"), ThreadLocalRandom.current().nextLong());
      List var9 = splitCsv(this.api.readItemTag(var3, "affixes"));
      List var10 = splitCsv(this.api.readItemTag(var3, "gems"));
      String var11 = this.api.readItemTag(var3, "rune");
      ItemStack var12 = this.createItem(var2, this.api.readItemLevel(var3), var3.getAmount(), var5, var6, var4, var7, var9, var10, var11);
      this.carryProtectionTags(var3, var12);
      var1.getInventory().setItemInMainHand(var12);
   }

   private boolean gemCommand(CommandSender var1, String[] var2) {
      if (var1 instanceof Player var3) {
         if (!var1.hasPermission("mmoitems.socket")) {
            var1.sendMessage(color(this.messages.get("command.gem.no-permission", "&c你沒有權限鑲嵌寶石。")));
            return true;
         } else if (var2.length < 2) {
            var1.sendMessage(color(this.messages.get("command.gem.usage", "&e/mmoitems gem <寶石ID>")));
            return true;
         } else {
            ItemStack var4 = var3.getInventory().getItemInMainHand();
            if (this.blockForeignSoulboundUse(var3, var4, var1)) {
               return true;
            } else {
               ItemTemplate var5 = (ItemTemplate)this.templates.get(key(this.api.readItemType(var4), this.api.readItemId(var4)));
               ItemPowerTemplate var6 = (ItemPowerTemplate)this.gems.get(var2[1].toUpperCase(Locale.ROOT));
               if (var5 != null && var6 != null) {
                  int var7 = this.api.readItemLevel(var4);
                  if (!var6.matches(var7)) {
                     var1.sendMessage(color(this.messages.get("command.gem.level-mismatch", "&c這顆寶石不符合裝備等級。")));
                     return true;
                  } else {
                     ArrayList var8 = new ArrayList(splitCsv(this.api.readItemTag(var4, "gems")));
                     int var9 = parseInt(this.api.readItemTag(var4, "gem_slots"), this.gemSlots(var5, this.tier(this.api.readItemTag(var4, "tier"))));
                     if (var8.size() >= var9) {
                        var1.sendMessage(color(this.messages.get("command.gem.slots-full", "&c寶石孔已滿。")));
                        return true;
                     } else {
                        var8.add(var6.id());
                        this.rebuildHeld(var3, var5, var4, false, (String)null, (Integer)null, var8, this.api.readItemTag(var4, "rune"));
                        var1.sendMessage(color(this.messages.format("command.gem.inserted", "&a已鑲嵌寶石：{gem}", "gem", var6.display())));
                        return true;
                     }
                  }
               } else {
                  var1.sendMessage(color(this.messages.get("command.gem.not-found", "&c找不到裝備或寶石。")));
                  return true;
               }
            }
         }
      } else {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      }
   }

   private boolean runeCommand(CommandSender var1, String[] var2) {
      if (var1 instanceof Player var3) {
         if (!var1.hasPermission("mmoitems.socket")) {
            var1.sendMessage(color(this.messages.get("command.rune.no-permission", "&c你沒有權限刻印符文。")));
            return true;
         } else if (var2.length < 2) {
            var1.sendMessage(color(this.messages.get("command.rune.usage", "&e/mmoitems rune <符文ID>")));
            return true;
         } else {
            ItemStack var4 = var3.getInventory().getItemInMainHand();
            if (this.blockForeignSoulboundUse(var3, var4, var1)) {
               return true;
            } else {
               ItemTemplate var5 = (ItemTemplate)this.templates.get(key(this.api.readItemType(var4), this.api.readItemId(var4)));
               ItemPowerTemplate var6 = (ItemPowerTemplate)this.runes.get(var2[1].toUpperCase(Locale.ROOT));
               if (var5 != null && var6 != null) {
                  if (var5.runeSlots() <= 0) {
                     var1.sendMessage(color(this.messages.get("command.rune.no-slot", "&c這件裝備沒有符文槽。")));
                     return true;
                  } else if (!var6.matches(this.api.readItemLevel(var4))) {
                     var1.sendMessage(color(this.messages.get("command.rune.level-mismatch", "&c這枚符文不符合裝備等級。")));
                     return true;
                  } else {
                     this.rebuildHeld(var3, var5, var4, false, (String)null, (Integer)null, splitCsv(this.api.readItemTag(var4, "gems")), var6.id());
                     var1.sendMessage(color(this.messages.format("command.rune.inserted", "&a已刻印符文：{rune}", "rune", var6.display())));
                     return true;
                  }
               } else {
                  var1.sendMessage(color(this.messages.get("command.rune.not-found", "&c找不到裝備或符文。")));
                  return true;
               }
            }
         }
      } else {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      }
   }

   private boolean reforgeCommand(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player var4)) {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      } else if (!var1.hasPermission("mmoitems.reforge")) {
         var1.sendMessage(color(this.messages.get("command.reforge.no-permission", "&c你沒有權限重鑄 MMOItems 物品。")));
         return true;
      } else {
         ItemStack var5 = var4.getInventory().getItemInMainHand();
         if (this.blockForeignSoulboundUse(var4, var5, var1)) {
            return true;
         } else {
            ItemTemplate var6 = (ItemTemplate)this.templates.get(key(this.api.readItemType(var5), this.api.readItemId(var5)));
            if (var6 == null) {
               var1.sendMessage(color(this.messages.get("item.not-managed", "&c手中物品不是新版 MMOItems 裝備。")));
               return true;
            } else {
               ReforgeRules var7 = this.reforgeRules.withFlags(var2);
               String var8 = null;

               for(int var9 = 1; var9 < var2.length; ++var9) {
                  if (!var2[var9].startsWith("--")) {
                     var8 = var2[var9];
                     break;
                  }
               }

               String var3 = var8 != null ? var8 : this.api.readItemTag(var5, "tier");
               if (var3.equalsIgnoreCase("RANDOM")) {
                  var3 = this.randomTier().id();
               }

               List var10 = var7.keepGems() ? splitCsv(this.api.readItemTag(var5, "gems")) : List.of();
               String var11 = var7.keepRune() ? this.api.readItemTag(var5, "rune") : "";
               this.rebuildHeld(var4, var6, var5, true, var3, (Integer)null, var10, var11, var7);
               var1.sendMessage(LEGACY_AMPERSAND.deserialize(var7.describe()));
               return true;
            }
         }
      }
   }

   private boolean deconstructCommand(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player var3)) {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      } else if (!var1.hasPermission("mmoitems.deconstruct")) {
         var1.sendMessage(color(this.messages.get("command.deconstruct.no-permission", "&c你沒有權限分解 MMOItems 物品。")));
         return true;
      } else {
         ItemStack var4 = var3.getInventory().getItemInMainHand();
         if (this.blockForeignSoulboundUse(var3, var4, var1)) {
            return true;
         } else {
            ItemTemplate var5 = (ItemTemplate)this.templates.get(key(this.api.readItemType(var4), this.api.readItemId(var4)));
            if (var5 != null && var4.getType() != Material.AIR) {
               int var6 = this.parseDeconstructAmount(var2, var4.getAmount());
               String var7 = this.parseDeconstructMode(var2);
               TierProfile var8 = this.tier(this.api.readItemTag(var4, "tier"));
               int var9 = Math.max(1, this.api.readItemLevel(var4));
               int var10 = 0;
               int var11 = 0;
               ArrayList<ItemStack> var12 = new ArrayList<>();

               for(int var13 = 0; var13 < var6; ++var13) {
                  boolean var14 = this.deconstructionSucceeded(var4, var7);
                  if (var14) {
                     ++var10;
                  } else {
                     ++var11;
                  }

                  List<DeconstructionProfile.DropSpec> var15 = var8.deconstruction().roll(var14, ThreadLocalRandom.current());
                  if (var15.isEmpty() && this.getConfig().getBoolean("deconstruct.guarantee-result", true)) {
                     var12.add(this.fallbackDeconstructItem(var8, 1));
                  } else {
                     for(DeconstructionProfile.DropSpec var17 : var15) {
                        var12.add(this.createDropItem(var17, var9, var8));
                     }
                  }
               }

               this.consumeMainHand(var3, var6);
               var12.forEach((var2x) -> this.giveOrDrop(var3, var2x));
               var1.sendMessage(color(this.messages.format("command.deconstruct.done", "&a分解完成：成功 {success} 次，耗損 {lost} 次，產出 {materials} 組材料。", "success", var10, "lost", var11, "materials", var12.size())));
               return true;
            } else {
               var1.sendMessage(color(this.messages.get("item.not-managed", "&c手中物品不是新版 MMOItems 裝備。")));
               return true;
            }
         }
      }
   }

   private boolean soulboundCommand(CommandSender var1, String[] var2) {
      if (var1 instanceof Player var3) {
         if (!var1.hasPermission("mmoitems.soulbound")) {
            var1.sendMessage(color(this.messages.get("command.soulbound.no-permission", "&c你沒有權限綁定 MMOItems 物品。")));
            return true;
         } else {
            ItemStack var4 = var3.getInventory().getItemInMainHand();
            if (this.blockForeignSoulboundUse(var3, var4, var1)) {
               return true;
            } else if (this.templates.get(key(this.api.readItemType(var4), this.api.readItemId(var4))) != null && var4.getType() != Material.AIR) {
               if (var2.length > 1 && var2[1].equalsIgnoreCase("clear")) {
                  if (!var1.hasPermission("mmoitems.admin")) {
                     var1.sendMessage(color(this.messages.get("command.soulbound.admin-only", "&c只有管理員可以解除靈魂綁定。")));
                     return true;
                  } else {
                     this.writeTags(var4, Map.of("soulbound_uuid", "", "soulbound_name", "", "soulbound_level", ""));
                     this.stripSoulboundLore(var4);
                     var1.sendMessage(color(this.messages.get("command.soulbound.cleared", "&a已解除手中裝備的靈魂綁定。")));
                     return true;
                  }
               } else {
                  this.writeTags(var4, Map.of("soulbound_uuid", var3.getUniqueId().toString(), "soulbound_name", var3.getName(), "soulbound_level", var2.length > 1 ? var2[1] : "1"));
                  this.applySoulboundLore(var4, var3.getName());
                  var1.sendMessage(Component.text("已將手中裝備靈魂綁定給 " + var3.getName() + "。", NamedTextColor.GREEN));
                  return true;
               }
            } else {
               var1.sendMessage(color(this.messages.get("item.not-managed", "&c手中物品不是新版 MMOItems 裝備。")));
               return true;
            }
         }
      } else {
         var1.sendMessage(color(this.messages.get("command.players-only", "&c這個指令只能由玩家使用。")));
         return true;
      }
   }

   private boolean listPowerCommand(CommandSender var1, Map<String, ItemPowerTemplate> var2, String var3) {
      var1.sendMessage(Component.text("MMOItems " + var3, NamedTextColor.AQUA));
      var2.values().stream().sorted(Comparator.comparing(ItemPowerTemplate::id)).forEach((var1x) -> var1.sendMessage(Component.text(" - " + var1x.id() + " / ", NamedTextColor.GRAY).append(color(var1x.display()))));
      return true;
   }

   private int parseDeconstructAmount(String[] var1, int var2) {
      int var3 = 1;

      for(int var4 = 1; var4 < var1.length; ++var4) {
         if (var1[var4].matches("\\d+")) {
            var3 = parseInt(var1[var4], 1);
            break;
         }
      }

      return Math.max(1, Math.min(var2, var3));
   }

   private String parseDeconstructMode(String[] var1) {
      for(int var2 = 1; var2 < var1.length; ++var2) {
         String var3 = var1[var2].toLowerCase(Locale.ROOT);
         if (List.of("auto", "success", "lose", "fail").contains(var3)) {
            return var3;
         }
      }

      return "auto";
   }

   private boolean deconstructionSucceeded(ItemStack var1, String var2) {
      if (var2.equalsIgnoreCase("success")) {
         return true;
      } else if (!var2.equalsIgnoreCase("lose") && !var2.equalsIgnoreCase("fail")) {
         int var3 = parseInt(this.api.readItemTag(var1, "quality"), 70);
         int var4 = parseInt(this.api.readItemTag(var1, "upgrade_level"), 0);
         double var5 = this.getConfig().getDouble("deconstruct.base-success-chance", 0.55) + (double)var3 * this.getConfig().getDouble("deconstruct.quality-factor", 0.0035) + (double)var4 * this.getConfig().getDouble("deconstruct.upgrade-factor", 0.012);
         double var7 = this.getConfig().getDouble("deconstruct.max-success-chance", 0.92);
         return ThreadLocalRandom.current().nextDouble() <= Math.max((double)0.0F, Math.min(var7, var5));
      } else {
         return false;
      }
   }

   private ItemStack createDropItem(DeconstructionProfile.DropSpec var1, int var2, TierProfile var3) {
      ItemTemplate var4 = (ItemTemplate)this.templates.get(key(var1.type(), var1.id()));
      int var5 = var1.rollAmount(ThreadLocalRandom.current());
      return var4 != null ? this.createItem(var4, var2, var5, var4.tier(), 80, 0, ThreadLocalRandom.current().nextLong(), (List)null, List.of(), "") : this.fallbackMaterial(var1.id(), var5, var3);
   }

   private ItemStack fallbackDeconstructItem(TierProfile var1, int var2) {
      return this.fallbackMaterial(var1.id() + "_FRAGMENT", var2, var1);
   }

   private ItemStack fallbackMaterial(String var1, int var2, TierProfile var3) {
      ItemStack var4 = new ItemStack(this.materialForDrop(var1), Math.max(1, var2));
      ItemMeta var5 = var4.getItemMeta();
      String var10001 = this.humanizeId(var1);
      var5.displayName(color("&b" + var10001));
      var5.lore(List.of(color(this.messages.get("item.salvage-material", "&7MMOItems 分解材料")), color(this.messages.format("item.salvage-source-tier", "&7來源階級: {tier}", "tier", var3.display())), color(this.messages.format("item.salvage-id", "&8ID: {id}", "id", var1.toUpperCase(Locale.ROOT)))));
      this.api.writeItemTags(var5, Map.of("material_id", var1.toUpperCase(Locale.ROOT), "material_source_tier", var3.id()));
      var4.setItemMeta(var5);
      return var4;
   }

   private Material materialForDrop(String var1) {
      Material var2 = Material.matchMaterial(var1);
      if (var2 != null && var2 != Material.AIR) {
         return var2;
      } else {
         String var3 = var1.toUpperCase(Locale.ROOT);
         if (var3.contains("POWDER")) {
            return Material.GLOWSTONE_DUST;
         } else if (var3.contains("ESSENCE")) {
            return Material.AMETHYST_SHARD;
         } else if (var3.contains("FRAGMENT")) {
            return Material.PRISMARINE_SHARD;
         } else if (var3.contains("COIN")) {
            return Material.GOLD_NUGGET;
         } else {
            return var3.contains("DIAMOND") ? Material.DIAMOND : Material.PAPER;
         }
      }
   }

   private void consumeMainHand(Player var1, int var2) {
      ItemStack var3 = var1.getInventory().getItemInMainHand();
      if (var3.getAmount() <= var2) {
         var1.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
      } else {
         var3.setAmount(var3.getAmount() - var2);
      }

   }

   private void giveOrDrop(Player var1, ItemStack var2) {
      HashMap<Integer, ItemStack> var3 = var1.getInventory().addItem(new ItemStack[]{var2});

      for(ItemStack var5 : var3.values()) {
         var1.getWorld().dropItemNaturally(var1.getLocation(), var5);
      }

   }

   private void carryProtectionTags(ItemStack var1, ItemStack var2) {
      String var3 = this.api.readItemTag(var1, "soulbound_uuid");
      if (!var3.isBlank()) {
         String var4 = this.api.readItemTag(var1, "soulbound_name");
         String var5 = this.api.readItemTag(var1, "soulbound_level");
         this.writeTags(var2, Map.of("soulbound_uuid", var3, "soulbound_name", var4, "soulbound_level", var5.isBlank() ? "1" : var5));
         this.applySoulboundLore(var2, var4.isBlank() ? this.messages.get("item.unknown-owner", "未知玩家") : var4);
      }

   }

   private void writeTags(ItemStack var1, Map<String, String> var2) {
      if (var1 != null && var1.hasItemMeta()) {
         ItemMeta var3 = var1.getItemMeta();
         this.api.writeItemTags(var3, var2);
         var1.setItemMeta(var3);
      }

   }

   private String soulboundLorePrefix() {
      return this.messages.get("item.soulbound-lore-prefix", "&c靈魂綁定: ");
   }

   private void applySoulboundLore(ItemStack var1, String var2) {
      if (var1 != null && var1.hasItemMeta()) {
         ItemMeta var3 = var1.getItemMeta();
         ArrayList<Component> var4 = var3.hasLore() ? new ArrayList<>(var3.lore()) : new ArrayList<>();
         var4.removeIf(this::isSoulboundLine);
         String var10002 = this.soulboundLorePrefix();
         var4.add(0, noItalic(color(var10002 + var2)));
         var3.lore(var4.stream().map(MMOItemsPlugin::noItalic).toList());
         var1.setItemMeta(var3);
      }

   }

   private void stripSoulboundLore(ItemStack var1) {
      ItemMeta var2;
      if (var1 != null && var1.hasItemMeta() && (var2 = var1.getItemMeta()).hasLore()) {
         ArrayList<Component> var3 = new ArrayList<>(var2.lore());
         var3.removeIf(this::isSoulboundLine);
         var2.lore(var3.stream().map(MMOItemsPlugin::noItalic).toList());
         var1.setItemMeta(var2);
      }

   }

   private boolean isSoulboundLine(Component var1) {
      String var2 = plainLegacy(this.soulboundLorePrefix()).trim();
      if (var2.endsWith(":")) {
         var2 = var2.substring(0, var2.length() - 1).trim();
      }

      return var2.isEmpty() ? false : plainLegacy(LEGACY_AMPERSAND.serialize(var1)).contains(var2);
   }

   private void rebuildHeld(Player var1, ItemTemplate var2, ItemStack var3, boolean var4, String var5, Integer var6, List<String> var7, String var8) {
      this.rebuildHeld(var1, var2, var3, var4, var5, var6, var7, var8, (ReforgeRules)null);
   }

   private void rebuildHeld(Player var1, ItemTemplate var2, ItemStack var3, boolean var4, String var5, Integer var6, List<String> var7, String var8, ReforgeRules var9) {
      boolean var11 = var4 && var9 != null && var9.keepTier();
      String var10 = !var11 && var5 != null && !var5.isBlank() ? var5 : this.api.readItemTag(var3, "tier");
      boolean var13 = !var4 || var9 != null && var9.keepQuality();
      boolean var14 = !var4 || var9 != null && var9.keepAffixes();
      Integer var15 = var13 ? var6 == null ? parseInt(this.api.readItemTag(var3, "quality"), 70) : var6 : null;
      long var16 = var4 ? ThreadLocalRandom.current().nextLong() : parseLong(this.api.readItemTag(var3, "instance_seed"), ThreadLocalRandom.current().nextLong());
      List var18 = var14 ? splitCsv(this.api.readItemTag(var3, "affixes")) : null;
      int var19 = parseInt(this.api.readItemTag(var3, "upgrade_level"), 0);
      ItemStack var20 = this.createItem(var2, this.api.readItemLevel(var3), var3.getAmount(), var10, var15, var19, var16, var18, var7, var8);
      this.carryProtectionTags(var3, var20);
      var1.getInventory().setItemInMainHand(var20);
   }

   private boolean help(CommandSender var1, String var2) {
      for(String var4 : this.messages.formatList("command.help", DEFAULT_HELP_LINES, "cmd", var2)) {
         var1.sendMessage(color(var4));
      }

      return true;
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var4.length == 1) {
         return List.of("give", "roll", "browse", "editor", "craft", "forge", "shop", "gold", "validate", "list", "identify", "upgrade", "gem", "gems", "rune", "runes", "reforge", "deconstruct", "salvage", "soulbound", "reload").stream().filter((var1x) -> var1x.startsWith(var4[0].toLowerCase(Locale.ROOT))).toList();
      } else if (var4.length != 2 || !var4[0].equalsIgnoreCase("browse") && !var4[0].equalsIgnoreCase("list")) {
         if (var4.length != 3 || !var4[0].equalsIgnoreCase("give") && !var4[0].equalsIgnoreCase("roll")) {
            if (var4.length != 4 || !var4[0].equalsIgnoreCase("give") && !var4[0].equalsIgnoreCase("roll")) {
               if (var4.length == 7 && var4[0].equalsIgnoreCase("give")) {
                  ArrayList var9 = new ArrayList(this.tiers.keySet());
                  var9.add("RANDOM");
                  return var9;
               } else if (var4.length == 2 && var4[0].equalsIgnoreCase("gem")) {
                  return this.gems.keySet().stream().sorted().toList();
               } else if (var4.length == 2 && var4[0].equalsIgnoreCase("rune")) {
                  return this.runes.keySet().stream().sorted().toList();
               } else if (var4.length == 2 && var4[0].equalsIgnoreCase("reforge")) {
                  ArrayList var8 = new ArrayList(this.tiers.keySet());
                  var8.add("RANDOM");
                  return var8;
               } else if (var4.length == 2 && var4[0].equalsIgnoreCase("deconstruct")) {
                  return List.of("1", "2", "4", "auto", "success", "lose");
               } else if (var4.length == 3 && var4[0].equalsIgnoreCase("deconstruct")) {
                  return List.of("auto", "success", "lose");
               } else if (var4.length == 2 && var4[0].equalsIgnoreCase("soulbound")) {
                  return List.of("1", "2", "3", "clear");
               } else if (var4.length == 2 && var4[0].equalsIgnoreCase("gold")) {
                  return List.of("balance", "deposit", "withdraw", "give");
               } else {
                  return var4.length == 4 && var4[0].equalsIgnoreCase("browse") ? this.tiers.keySet().stream().sorted().toList() : List.of();
               }
            } else {
               String var7 = var4[2].toUpperCase(Locale.ROOT);
               return this.templates.values().stream().filter((var1x) -> var1x.type().equals(var7)).map(ItemTemplate::id).sorted().toList();
            }
         } else {
            return this.templates.values().stream().map(ItemTemplate::type).distinct().sorted().toList();
         }
      } else {
         ArrayList<String> var5 = new ArrayList<>();
         this.browserCategories().forEach((var1x) -> var5.add(var1x.id()));
         Stream<String> var6 = this.templates.values().stream().map(ItemTemplate::type).distinct().sorted();
         Objects.requireNonNull(var5);
         Objects.requireNonNull(var5);
         var6.forEach(var5::add);
         return var5.stream().filter((var1x) -> var1x.startsWith(var4[1].toUpperCase(Locale.ROOT))).toList();
      }
   }

   List<ItemTemplate> serviceTemplates() {
      return List.copyOf(this.templates.values());
   }

   ItemTemplate serviceTemplate(String var1, String var2) {
      return (ItemTemplate)this.templates.get(key(var1, var2));
   }

   List<String> serviceTypeIds() {
      return this.itemTypes.values().stream().filter((var0) -> !var0.hidden()).sorted(Comparator.comparingDouble(ItemTypeProfile::browserIndex).thenComparing(ItemTypeProfile::id)).map(ItemTypeProfile::id).toList();
   }

   String serviceCategoryId(String var1) {
      return this.browserCategoryFor(var1).id();
   }

   List<String> serviceTierIds() {
      return this.tiers.keySet().stream().sorted().toList();
   }

   List<String> serviceSetIds() {
      return this.sets.keySet().stream().sorted().toList();
   }

   List<String> serviceUpgradeIds() {
      return this.upgrades.keySet().stream().sorted().toList();
   }

   boolean serviceHasType(String var1) {
      return this.itemTypes.containsKey(StatSnapshot.normalize(var1));
   }

   boolean serviceHasTier(String var1) {
      return this.tiers.containsKey(StatSnapshot.normalize(var1));
   }

   boolean serviceHasSet(String var1) {
      return this.sets.containsKey(StatSnapshot.normalize(var1));
   }

   boolean serviceHasUpgrade(String var1) {
      return this.upgrades.containsKey(StatSnapshot.normalize(var1));
   }

   Material serviceTypeMaterial(String var1) {
      return ((ItemTypeProfile)this.itemTypes.getOrDefault(StatSnapshot.normalize(var1), ItemTypeProfile.fallback(var1, Material.PAPER))).icon();
   }

   String serviceTypeName(String var1) {
      return plainText(((ItemTypeProfile)this.itemTypes.getOrDefault(StatSnapshot.normalize(var1), ItemTypeProfile.fallback(var1, Material.PAPER))).displayName());
   }

   boolean serviceIsWeaponType(String var1) {
      return !((ItemTypeProfile)this.itemTypes.getOrDefault(StatSnapshot.normalize(var1), ItemTypeProfile.fallback(var1, Material.PAPER))).damageTypes().isEmpty();
   }

   boolean serviceIsManaged(ItemStack var1) {
      return var1 != null && var1.getType() != Material.AIR && this.templates.containsKey(key(this.api.readItemType(var1), this.api.readItemId(var1)));
   }

   private boolean looksManaged(ItemStack var1) {
      if (var1 != null && var1.getType() != Material.AIR) {
         try {
            return !this.api.readItemType(var1).isBlank() && !this.api.readItemId(var1).isBlank();
         } catch (RuntimeException var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   ItemStack serviceCreateItem(String var1, String var2, int var3, int var4, String var5) {
      ItemTemplate var6 = (ItemTemplate)this.templates.get(key(var1, var2));
      return var6 == null ? null : this.createItem(var6, var3, var4, var5, (Integer)null, 0, ThreadLocalRandom.current().nextLong(), (List)null, List.of(), "");
   }

   ItemStack serviceRebuildUpgrade(ItemStack var1, int var2) {
      if (!this.serviceIsManaged(var1)) {
         return null;
      } else {
         ItemTemplate var3 = (ItemTemplate)this.templates.get(key(this.api.readItemType(var1), this.api.readItemId(var1)));
         String var4 = this.api.readItemTag(var1, "tier");
         int var5 = parseInt(this.api.readItemTag(var1, "quality"), 70);
         long var6 = parseLong(this.api.readItemTag(var1, "instance_seed"), ThreadLocalRandom.current().nextLong());
         ItemStack var8 = this.createItem(var3, this.api.readItemLevel(var1), var1.getAmount(), var4, var5, Math.max(0, Math.min(this.getConfig().getInt("upgrade.max-level", 20), var2)), var6, splitCsv(this.api.readItemTag(var1, "affixes")), splitCsv(this.api.readItemTag(var1, "gems")), this.api.readItemTag(var1, "rune"));
         this.carryProtectionTags(var1, var8);
         return var8;
      }
   }

   void serviceGiveOrDrop(Player var1, ItemStack var2) {
      this.giveOrDrop(var1, var2);
   }

   void serviceReloadItems() {
      this.reloadAll();
   }

   private int safeLevel(int var1) {
      int var2 = Math.max(1, Math.min(10000, this.getConfig().getInt("safety.max-generated-level", 1000)));
      return Math.max(1, Math.min(var2, var1));
   }

   private int safeAmount(int var1) {
      int var2 = Math.max(1, Math.min(64, this.getConfig().getInt("safety.max-stack-amount", 64)));
      return Math.max(1, Math.min(var2, var1));
   }

   private void sanitizeStats(Map<String, Double> var1) {
      LinkedHashMap<String, Double> var2 = new LinkedHashMap<>();

      for(Map.Entry var4 : new ArrayList<>(var1.entrySet())) {
         String var5 = WeaponStatCatalog.normalize((String)var4.getKey());
         if (!var5.isBlank()) {
            double var6 = var4.getValue() == null ? (double)0.0F : (Double)var4.getValue();
            double var8 = this.safeStatValue(var5, var6);
            if (Math.abs(var8) > 1.0E-6) {
               var2.merge(var5, var8, Double::sum);
            }
         }
      }

      var1.clear();
      var2.forEach((var2x, var3) -> var1.put(var2x, this.safeStatValue(var2x, var3)));
   }

   private double safeStatValue(String var1, double var2) {
      if (!Double.isFinite(var2)) {
         return (double)0.0F;
      } else {
         double var4 = this.statLimit(var1);
         return Math.max(-var4, Math.min(var4, var2));
      }
   }

   private double statLimit(String var1) {
      String var2 = WeaponStatCatalog.normalize(var1);
      if (var2.isBlank()) {
         return Math.max((double)1.0F, this.getConfig().getDouble("safety.max-stat-value", (double)100000.0F));
      } else {
         double var3 = WeaponStatCatalog.limit(var2);
         if (!var2.endsWith("_CHANCE") && !var2.endsWith("_POWER") && !var2.endsWith("_PERCENT") && !var2.endsWith("_REDUCTION") && !var2.equals("LIFE_STEAL")) {
            if (var2.endsWith("_RATING")) {
               return Math.min(var3, Math.max((double)1.0F, this.getConfig().getDouble("safety.max-rating-value", (double)20000.0F)));
            } else {
               return var2.endsWith("_COST") ? Math.min(var3, Math.max((double)1.0F, this.getConfig().getDouble("safety.max-resource-cost", (double)1000.0F))) : Math.min(var3, Math.max((double)1.0F, this.getConfig().getDouble("safety.max-stat-value", (double)100000.0F)));
            }
         } else {
            return Math.min(var3, Math.max((double)1.0F, this.getConfig().getDouble("safety.max-percent-value", (double)500.0F)));
         }
      }
   }

   private void saveResourceIfMissing(String var1) {
      if (!(new File(this.getDataFolder(), var1)).exists()) {
         this.saveResource(var1, false);
      }

   }

   private ConfigurationSection readYaml(String var1) {
      File var2 = new File(this.getDataFolder(), var1);
      if (!var2.isFile()) {
         return null;
      } else {
         try {
            return YamlConfiguration.loadConfiguration(var2);
         } catch (RuntimeException var4) {
            this.getLogger().warning("無法讀取 " + var1 + "：" + var4.getMessage());
            return null;
         }
      }
   }

   private static void merge(Map<String, Double> var0, Map<String, Double> var1) {
      for(Map.Entry var3 : var1.entrySet()) {
         String var4;
         if (var3.getValue() != null && Double.isFinite((Double)var3.getValue()) && !(var4 = WeaponStatCatalog.normalize((String)var3.getKey())).isBlank()) {
            var0.merge(var4, (Double)var3.getValue(), Double::sum);
         }
      }

   }

   private static boolean scalesWithPower(String var0) {
      return !List.of("REQUIRED_LEVEL", "MANA_COST", "COOLDOWN").contains(StatSnapshot.normalize(var0));
   }

   private String displayName(ItemTemplate var1, List<AffixTemplate> var2, int var3) {
      String var4;
      if (this.properNameTiers.contains(this.canonicalTierId(var1.tier()))) {
         var4 = var1.name();
      } else {
         String var5 = (String)var2.stream().map(AffixTemplate::prefix).filter((var0) -> !var0.isBlank()).collect(Collectors.joining(" "));
         String var6 = (String)var2.stream().map(AffixTemplate::suffix).filter((var0) -> !var0.isBlank()).collect(Collectors.joining(" "));
         String var7 = var5.isBlank() ? "" : var5 + " ";
         var4 = (String)var7 + var1.name() + (var6.isBlank() ? "" : " " + var6);
      }

      return var3 > 0 ? var4 + " &8(&e+" + var3 + "&8)" : var4;
   }

   private String translateAbility(String var1) {
      return this.abilityDisplays.get(var1 == null ? "" : var1.toUpperCase(Locale.ROOT), var1);
   }

   private static String key(String var0, String var1) {
      String var2 = var0.toUpperCase(Locale.ROOT);
      return var2 + "." + var1.toUpperCase(Locale.ROOT);
   }

   private static String stripExtension(String var0) {
      int var1 = var0.lastIndexOf(46);
      return var1 < 0 ? var0 : var0.substring(0, var1);
   }

   private static int parseInt(String var0, int var1) {
      try {
         return Integer.parseInt(var0);
      } catch (RuntimeException var3) {
         return var1;
      }
   }

   private static long parseLong(String var0, long var1) {
      try {
         return Long.parseLong(var0);
      } catch (RuntimeException var4) {
         return var1;
      }
   }

   private static List<String> splitCsv(String var0) {
      if (var0 != null && !var0.isBlank()) {
         ArrayList var1 = new ArrayList();

         for(String var5 : var0.split(",")) {
            if (!var5.isBlank()) {
               var1.add(var5.trim());
            }
         }

         return var1;
      } else {
         return List.of();
      }
   }

   private static Component color(String var0) {
      if (var0 != null && !var0.isBlank()) {
         Object var1 = var0.indexOf(167) >= 0 ? LEGACY_SECTION.deserialize(var0) : (!var0.contains("<#") && !var0.contains("<gradient") && !var0.contains("<rainbow") && !var0.contains("<bold>") && !var0.contains("<italic>") && !var0.contains("<underlined>") && !var0.contains("<strikethrough>") && !var0.contains("<obfuscated>") ? LEGACY_AMPERSAND.deserialize(var0) : MINI_MESSAGE.deserialize(var0));
         return noItalic((Component)var1);
      } else {
         return noItalic(Component.empty());
      }
   }

   private static Component noItalic(Component var0) {
      return var0.decoration(TextDecoration.ITALIC, false);
   }

   private static String plainLegacy(String var0) {
      return var0 == null ? "" : var0.replaceAll("&[0-9A-FK-ORa-fk-or]", "");
   }

   private static String plainText(String var0) {
      return plainLegacy(var0).replaceAll("§[0-9A-FK-ORa-fk-or]", "").replaceAll("<[^>]+>", "");
   }

   private String humanizeId(String var1) {
      if (var1 != null && !var1.isBlank()) {
         String[] var2 = var1.toLowerCase(Locale.ROOT).split("[_-]+");
         ArrayList var3 = new ArrayList();

         for(String var7 : var2) {
            if (!var7.isBlank()) {
               String var8 = var7.substring(0, 1).toUpperCase(Locale.ROOT);
               var3.add(var8 + var7.substring(1));
            }
         }

         return String.join(" ", var3);
      } else {
         return this.messages.get("item.unknown-material", "未知材料");
      }
   }

   private static String compact(double var0) {
      if (!Double.isFinite(var0)) {
         var0 = (double)0.0F;
      }

      double var2;
      return Math.abs(var0 - (var2 = Math.rint(var0))) < 0.05 ? Long.toString(Math.round(var2)) : String.format(Locale.ROOT, "%.1f", var0);
   }

   private static String format(double var0) {
      if (!Double.isFinite(var0)) {
         var0 = (double)0.0F;
      }

      return String.format(Locale.ROOT, "%.2f", var0);
   }

   static {
      OFFENSE_CATEGORIES = Set.of(WeaponStatCatalog.Category.OFFENSE);
      SURVIVAL_CATEGORIES = Set.of(WeaponStatCatalog.Category.DEFENSE, WeaponStatCatalog.Category.RESOURCE, WeaponStatCatalog.Category.MOBILITY, WeaponStatCatalog.Category.ECONOMY, WeaponStatCatalog.Category.PRIMARY);
      PAGE_SIZE = BROWSER_CONTENT_SLOTS.length;
      LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
      LEGACY_SECTION = LegacyComponentSerializer.legacySection();
      MINI_MESSAGE = MiniMessage.miniMessage();
      DEFAULT_HELP_LINES = List.of("&e/{cmd} give <玩家> <類型> <ID> [等級] [數量] [階級|RANDOM]", "&e/{cmd} roll <玩家> <類型> <ID> [等級] [數量]", "&e/{cmd} browse [分類|類型] [搜尋] [階級]", "&e/{cmd} editor | craft | forge | shop | gold [deposit|withdraw|balance]", "&e/{cmd} validate | reload", "&e/{cmd} gem <ID> | rune <ID> | reforge [階級|RANDOM]", "&e/{cmd} deconstruct [次數] [auto|success|lose] | soulbound [等級|clear]", "&e/{cmd} list [分類|類型] [數量] | identify | upgrade [等級] | reload");
      DEFAULT_BROWSER_CATEGORIES = List.of(new BrowserCategory("MELEE", "&c近戰武器", Material.DIAMOND_SWORD, "劍、大劍、斧、匕首、長矛", Set.of("SWORD", "GREATSWORD", "LONG_SWORD", "THRUSTING_SWORD", "KATANA", "DAGGER", "SPEAR", "LANCE", "HALBERD", "AXE", "GREATAXE", "HAMMER", "GREAT_HAMMER", "GREATHAMMER", "GAUNTLET", "WHIP"), Set.of("SWORD", "DAGGER", "SPEAR", "AXE", "HAMMER", "LANCE", "GAUNTLET", "WHIP")), new BrowserCategory("RANGED", "&d遠程・法器", Material.BOW, "弓、弩、法杖、聖器", Set.of("BOW", "GREATBOW", "CROSSBOW", "MUSKET", "STAFF", "GREATSTAFF", "WAND", "TOME", "LUTE", "CATALYST", "MAIN_CATALYST", "OFF_CATALYST"), Set.of("BOW", "STAFF", "WAND", "TOME", "CATALYST", "MUSKET", "LUTE")), new BrowserCategory("ARMOR", "&b防具", Material.IRON_CHESTPLATE, "頭盔、胸甲、護腿、靴子、盾牌", Set.of("ARMOR", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "SHIELD", "GLOVES"), Set.of("ARMOR", "HELMET", "CHEST", "LEGGING", "BOOT", "SHIELD", "GLOVE")), new BrowserCategory("TRINKET", "&e飾品", Material.GOLD_INGOT, "戒指、護符、手鐲與其他配飾", Set.of("RING", "AMULET", "BRACELET", "TALISMAN", "ORNAMENT", "ACCESSORY", "ARTIFACT"), Set.of("RING", "AMULET", "BRACELET", "TALISMAN", "ORNAMENT", "ACCESSORY", "ARTIFACT")), new BrowserCategory("ENHANCE", "&6強化・寶石", Material.AMETHYST_SHARD, "寶石、符文與強化石", Set.of("GEM_STONE", "UPGRADE_STONE", "RUNE"), Set.of("GEM", "UPGRADE", "RUNE")), new BrowserCategory("SUPPLY", "&a素材・消耗品", Material.BUNDLE, "素材、食物、藥水與工具", Set.of("MATERIAL", "FOOD", "CONSUMABLE", "POTION", "TOOL", "BLOCK", "SKIN", "MISCELLANEOUS"), Set.of("MATERIAL", "FOOD", "CONSUMABLE", "POTION", "TOOL", "BLOCK", "SKIN", "MISC", "COIN")), new BrowserCategory("QUEST", "&f任務物品", Material.WRITTEN_BOOK, "任務道具與劇情物件", Set.of("QUEST", "QUEST_ITEM", "KEY"), Set.of("QUEST", "KEY")));
   }

   private static record ScoreBreakdown(double offense, double defense, double resource, double utility) {
      double total() {
         double var1 = this.offense + this.defense + this.resource + this.utility;
         return Double.isFinite(var1) ? Math.max((double)0.0F, var1) : (double)0.0F;
      }

      String grade() {
         double var1 = this.total();
         if (var1 >= (double)360.0F) {
            return "S+";
         } else if (var1 >= (double)280.0F) {
            return "S";
         } else if (var1 >= (double)210.0F) {
            return "A";
         } else if (var1 >= (double)140.0F) {
            return "B";
         } else {
            return var1 >= (double)80.0F ? "C" : "D";
         }
      }
   }

   private static final class BrowserHolder implements InventoryHolder {
      private final BrowserMode mode;
      private final String category;
      private final String type;
      private final String query;
      private final String tier;
      private final int page;
      private Inventory inventory;

      private BrowserHolder(BrowserMode var1, String var2, String var3, String var4, String var5, int var6) {
         this.mode = var1;
         this.category = var2 == null ? "" : var2;
         this.type = var3 == null ? "" : var3;
         this.query = var4 == null ? "" : var4;
         this.tier = var5 != null && !var5.isBlank() ? var5 : "RARE";
         this.page = Math.max(0, var6);
      }

      private BrowserHolder withPage(int var1) {
         return new BrowserHolder(this.mode, this.category, this.type, this.query, this.tier, var1);
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static enum BrowserMode {
      CATEGORIES,
      TYPES,
      ITEMS;

      private BrowserMode() {
      }
   }
}
