package com.xuzhihuanjing.rpgcore;

import com.xuzhihuanjing.rpgcore.ability.AbilityCastService;
import com.xuzhihuanjing.rpgcore.ability.AbilityExecutor;
import com.xuzhihuanjing.rpgcore.ability.AbilityInputService;
import com.xuzhihuanjing.rpgcore.ability.AbilityModifierService;
import com.xuzhihuanjing.rpgcore.ability.AbilityTreeService;
import com.xuzhihuanjing.rpgcore.ability.SpawnSafeZoneService;
import com.xuzhihuanjing.rpgcore.combat.CharacterActivationService;
import com.xuzhihuanjing.rpgcore.combat.CombatFormula;
import com.xuzhihuanjing.rpgcore.combat.CombatHudService;
import com.xuzhihuanjing.rpgcore.combat.CombatStateService;
import com.xuzhihuanjing.rpgcore.combat.DamagePipeline;
import com.xuzhihuanjing.rpgcore.combat.HudNotificationService;
import com.xuzhihuanjing.rpgcore.combat.NavigationHudService;
import com.xuzhihuanjing.rpgcore.combat.StatService;
import com.xuzhihuanjing.rpgcore.combat.TrainingWeaponService;
import com.xuzhihuanjing.rpgcore.command.RpgCommand;
import com.xuzhihuanjing.rpgcore.config.AbilityRegistry;
import com.xuzhihuanjing.rpgcore.config.AbilityTreeRegistry;
import com.xuzhihuanjing.rpgcore.config.AbilityTreeSettings;
import com.xuzhihuanjing.rpgcore.config.ClassRegistry;
import com.xuzhihuanjing.rpgcore.config.CombatSettings;
import com.xuzhihuanjing.rpgcore.config.ContentGraphValidator;
import com.xuzhihuanjing.rpgcore.config.DiscoveryRegistry;
import com.xuzhihuanjing.rpgcore.config.EncounterRegistry;
import com.xuzhihuanjing.rpgcore.config.EquipmentRegistry;
import com.xuzhihuanjing.rpgcore.config.HudSettings;
import com.xuzhihuanjing.rpgcore.config.IdentificationSettings;
import com.xuzhihuanjing.rpgcore.config.InterfaceSettings;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.config.MonsterRegistry;
import com.xuzhihuanjing.rpgcore.config.PartySettings;
import com.xuzhihuanjing.rpgcore.config.PluginSettings;
import com.xuzhihuanjing.rpgcore.config.ProgressionSettings;
import com.xuzhihuanjing.rpgcore.config.QuestRegistry;
import com.xuzhihuanjing.rpgcore.discovery.DiscoveryService;
import com.xuzhihuanjing.rpgcore.encounter.EncounterRuntimeService;
import com.xuzhihuanjing.rpgcore.equipment.EmeraldCurrencyService;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentService;
import com.xuzhihuanjing.rpgcore.gui.AbilityTreeMenuService;
import com.xuzhihuanjing.rpgcore.gui.CharacterMenuService;
import com.xuzhihuanjing.rpgcore.gui.CharacterProfileMenuService;
import com.xuzhihuanjing.rpgcore.gui.CharacterSelectorPresentationService;
import com.xuzhihuanjing.rpgcore.gui.ContentBookMenuService;
import com.xuzhihuanjing.rpgcore.gui.IdentificationMenuService;
import com.xuzhihuanjing.rpgcore.gui.MainMenuService;
import com.xuzhihuanjing.rpgcore.gui.PartyMenuService;
import com.xuzhihuanjing.rpgcore.gui.ProfessionMenuService;
import com.xuzhihuanjing.rpgcore.gui.QuestJournalMenuService;
import com.xuzhihuanjing.rpgcore.gui.SkillCrystalMenuService;
import com.xuzhihuanjing.rpgcore.gui.WayfinderCodexService;
import com.xuzhihuanjing.rpgcore.hud.AeloriaCombatHudRenderer;
import com.xuzhihuanjing.rpgcore.hud.CombatHudRenderer;
import com.xuzhihuanjing.rpgcore.hud.InternalBossBarHudRenderer;
import com.xuzhihuanjing.rpgcore.hud.InternalHudPackService;
import com.xuzhihuanjing.rpgcore.hud.NoopCombatHudRenderer;
import com.xuzhihuanjing.rpgcore.integration.mmoitems.MmoItemsBridge;
import com.xuzhihuanjing.rpgcore.integration.nexo.CustomItemProvider;
import com.xuzhihuanjing.rpgcore.integration.nexo.CustomItemProviders;
import com.xuzhihuanjing.rpgcore.integration.nexo.HudGlyphProvider;
import com.xuzhihuanjing.rpgcore.integration.nexo.HudGlyphProviders;
import com.xuzhihuanjing.rpgcore.integration.placeholder.PlaceholderBridge;
import dev.aeloria.hud.api.AeloriaHudService;
import com.xuzhihuanjing.rpgcore.listener.AbilityInputListener;
import com.xuzhihuanjing.rpgcore.listener.AbilityTreeMenuListener;
import com.xuzhihuanjing.rpgcore.listener.CharacterMenuListener;
import com.xuzhihuanjing.rpgcore.listener.CharacterProfileMenuListener;
import com.xuzhihuanjing.rpgcore.listener.ContentBookMenuListener;
import com.xuzhihuanjing.rpgcore.listener.DiscoveryMovementListener;
import com.xuzhihuanjing.rpgcore.listener.EquipmentRefreshListener;
import com.xuzhihuanjing.rpgcore.listener.EquipmentUsageListener;
import com.xuzhihuanjing.rpgcore.listener.IdentificationMenuListener;
import com.xuzhihuanjing.rpgcore.listener.IdentificationNpcListener;
import com.xuzhihuanjing.rpgcore.listener.MainMenuListener;
import com.xuzhihuanjing.rpgcore.listener.MonsterCombatListener;
import com.xuzhihuanjing.rpgcore.listener.PartyMenuListener;
import com.xuzhihuanjing.rpgcore.listener.PlayerSessionListener;
import com.xuzhihuanjing.rpgcore.listener.ProfessionExperienceListener;
import com.xuzhihuanjing.rpgcore.listener.ProfessionMenuListener;
import com.xuzhihuanjing.rpgcore.config.DialogueRegistry;
import com.xuzhihuanjing.rpgcore.config.NpcRegistry;
import com.xuzhihuanjing.rpgcore.integration.citizens.CitizensBridge;
import com.xuzhihuanjing.rpgcore.listener.CitizensNpcListener;
import com.xuzhihuanjing.rpgcore.npc.NpcBehaviorService;
import com.xuzhihuanjing.rpgcore.npc.QuestIndicatorService;
import com.xuzhihuanjing.rpgcore.dialogue.DialogueService;
import com.xuzhihuanjing.rpgcore.listener.DialogueListener;
import com.xuzhihuanjing.rpgcore.listener.QuestInteractionListener;
import com.xuzhihuanjing.rpgcore.listener.QuestObjectiveListener;
import com.xuzhihuanjing.rpgcore.listener.QuestJournalMenuListener;
import com.xuzhihuanjing.rpgcore.listener.SkillCrystalMenuListener;
import com.xuzhihuanjing.rpgcore.listener.WayfinderCodexListener;
import com.xuzhihuanjing.rpgcore.monster.ContributionLedger;
import com.xuzhihuanjing.rpgcore.monster.ModelEngineBridge;
import com.xuzhihuanjing.rpgcore.monster.MonsterLootService;
import com.xuzhihuanjing.rpgcore.monster.MonsterRuntimeService;
import com.xuzhihuanjing.rpgcore.monster.MonsterScalingFormula;
import com.xuzhihuanjing.rpgcore.monster.MythicMobsBridge;
import com.xuzhihuanjing.rpgcore.party.PartyService;
import com.xuzhihuanjing.rpgcore.persistence.YamlCharacterRepository;
import com.xuzhihuanjing.rpgcore.platform.FoliaSelfTestService;
import com.xuzhihuanjing.rpgcore.platform.RpgScheduler;
import com.xuzhihuanjing.rpgcore.progression.ExperienceCurve;
import com.xuzhihuanjing.rpgcore.progression.PrimarySkillService;
import com.xuzhihuanjing.rpgcore.progression.ProfessionService;
import com.xuzhihuanjing.rpgcore.progression.ProgressionService;
import com.xuzhihuanjing.rpgcore.quest.QuestService;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class RpgCorePlugin extends JavaPlugin {
   private CharacterService characterService;
   private CharacterActivationService activationService;
   private CombatStateService combatStateService;
   private AbilityInputService abilityInputService;
   private CombatHudService combatHudService;
   private CharacterSelectorPresentationService selectorPresentationService;
   private RpgScheduler scheduler;
   private MonsterRuntimeService monsterRuntimeService;
   private EncounterRuntimeService encounterRuntimeService;
   private ContributionLedger contributionLedger;
   private InternalHudPackService hudPackService;
   private PartyService partyService;
   private DialogueService dialogueService;
   private NpcBehaviorService npcBehaviorService;
   private AutoCloseable placeholderBridge;

   public void onEnable() {
      try {
         this.prepareVersionedResource("config.yml", 15);
         this.reloadConfig();
         this.prepareVersionedResource("classes.yml", 4);
         this.prepareVersionedResource("abilities.yml", 2);
         this.prepareVersionedResource("ability-tree.yml", 3);
         this.prepareVersionedResource("messages.yml", 18);
         this.prepareVersionedResource("equipment.yml", 2);
         this.prepareVersionedResource("monsters.yml", 2);
         this.prepareVersionedResource("encounters.yml", 1);
         this.prepareVersionedResource("quests.yml", 4);
         this.prepareVersionedResource("discoveries.yml", 1);
         this.prepareVersionedResource("dialogues.yml", 1);
         this.prepareVersionedResource("npcs.yml", 1);
         this.saveBundledResourceIfMissing("裝備系統設定指南.md");
         PluginSettings settings = PluginSettings.from(this.getConfig());
         CombatSettings combatSettings = CombatSettings.from(this.getConfig());
         HudSettings hudSettings = HudSettings.from(this.getConfig());
         InterfaceSettings interfaceSettings = InterfaceSettings.from(this.getConfig());
         AbilityTreeSettings abilityTreeSettings = AbilityTreeSettings.from(this.getConfig());
         ProgressionSettings progressionSettings = ProgressionSettings.from(this.getConfig());
         IdentificationSettings identificationSettings = IdentificationSettings.from(this.getConfig());
         PartySettings partySettings = PartySettings.from(this.getConfig());
         File classesFile = new File(this.getDataFolder(), "classes.yml");
         File abilitiesFile = new File(this.getDataFolder(), "abilities.yml");
         File abilityTreeFile = new File(this.getDataFolder(), "ability-tree.yml");
         File equipmentFile = new File(this.getDataFolder(), "equipment.yml");
         File monstersFile = new File(this.getDataFolder(), "monsters.yml");
         File encountersFile = new File(this.getDataFolder(), "encounters.yml");
         File questsFile = new File(this.getDataFolder(), "quests.yml");
         File discoveriesFile = new File(this.getDataFolder(), "discoveries.yml");
         File dialoguesFile = new File(this.getDataFolder(), "dialogues.yml");
         File npcsFile = new File(this.getDataFolder(), "npcs.yml");
         ClassRegistry classRegistry = new ClassRegistry();
         classRegistry.load(classesFile);
         AbilityRegistry abilityRegistry = new AbilityRegistry();
         abilityRegistry.load(abilitiesFile, classRegistry);
         AbilityTreeRegistry abilityTreeRegistry = new AbilityTreeRegistry();
         this.loadAbilityTree(abilityTreeRegistry, abilityTreeFile, classRegistry);
         EquipmentRegistry equipmentRegistry = new EquipmentRegistry();
         equipmentRegistry.load(equipmentFile, classRegistry);
         MonsterRegistry monsterRegistry = new MonsterRegistry();
         monsterRegistry.load(monstersFile, equipmentRegistry);
         EncounterRegistry encounterRegistry = new EncounterRegistry();
         encounterRegistry.load(encountersFile, monsterRegistry);
         DiscoveryRegistry discoveryRegistry = new DiscoveryRegistry();
         discoveryRegistry.load(discoveriesFile);
         QuestRegistry questRegistry = new QuestRegistry();
         questRegistry.load(questsFile, monsterRegistry, encounterRegistry, discoveryRegistry);
         DialogueRegistry dialogueRegistry = new DialogueRegistry();
         dialogueRegistry.load(dialoguesFile);
         NpcRegistry npcRegistry = new NpcRegistry();
         npcRegistry.load(npcsFile, questRegistry);
         ContentGraphValidator.validate(questRegistry, discoveryRegistry);
         MessageBundle messages = new MessageBundle(new File(this.getDataFolder(), "messages.yml"));
         this.scheduler = new RpgScheduler(this);
         CombatHudRenderer hudRenderer;
          if (hudSettings.renderer() == HudSettings.Renderer.AELORIAHUD) {
             AeloriaHudService aeloriaHud = this.getServer().getServicesManager().load(AeloriaHudService.class);
             if (aeloriaHud == null) {
                throw new IllegalStateException("AeloriaHUD service is unavailable");
             }
             hudRenderer = new AeloriaCombatHudRenderer(aeloriaHud, hudSettings.notificationDurationMillis());
          } else if (hudSettings.renderer() == HudSettings.Renderer.INTERNAL && hudSettings.internalEnabled()) {
            InternalBossBarHudRenderer internalRenderer = new InternalBossBarHudRenderer(this, hudSettings);
            this.getServer().getPluginManager().registerEvents(internalRenderer, this);
            hudRenderer = internalRenderer;
         } else {
            hudRenderer = new NoopCombatHudRenderer();
         }

         this.hudPackService = new InternalHudPackService(this, hudSettings, this.scheduler);
         HudGlyphProvider hudGlyphs = HudGlyphProviders.create(this.getServer().getPluginManager(), hudSettings, Stream.of(interfaceSettings.contentBookGuiGlyph(), interfaceSettings.characterSelectorGuiGlyph(), "rpgcore_key_f").distinct().toList(), this.getLogger());
         CustomItemProvider customItems = CustomItemProviders.create(this.getServer().getPluginManager(), hudSettings.aeloriaAssetsEnabled(), this.getLogger());
         MythicMobsBridge mythicMobsBridge = new MythicMobsBridge(this.getServer().getPluginManager(), this.getLogger());
         ModelEngineBridge modelEngineBridge = new ModelEngineBridge(this.getServer().getPluginManager(), this.getLogger());
         MmoItemsBridge mmoItemsBridge = new MmoItemsBridge(this.getServer().getPluginManager(), this.getLogger());
         if (mmoItemsBridge.available()) {
            this.getLogger().info("Optional AelornItems service API integration enabled for equipment stats and item quest objectives.");
         } else {
            this.getLogger().info("AelornItems service API is not ready yet; native RPGCore equipment and quests remain active.");
         }

         EquipmentService equipmentService = new EquipmentService(this, equipmentRegistry, customItems, identificationSettings, mmoItemsBridge);
         Path storageDirectory = this.getDataFolder().toPath().toAbsolutePath().normalize().resolve(settings.storageDirectory()).normalize();
         if (!storageDirectory.startsWith(this.getDataFolder().toPath().toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Storage directory escaped the plugin data folder");
         }

         YamlCharacterRepository repository = new YamlCharacterRepository(storageDirectory);
         this.characterService = new CharacterService(repository, classRegistry, settings.maximumCharacterSlots(), settings.backupLimit(), this.getLogger());
         this.partyService = new PartyService(partySettings.maximumMembers(), partySettings.inviteLifetimeSeconds(), partySettings.finderEnabled());
         EmeraldCurrencyService emeraldCurrencyService = new EmeraldCurrencyService();
         IdentificationMenuService identificationMenuService = new IdentificationMenuService(this, this.characterService, equipmentService, emeraldCurrencyService, identificationSettings, messages);
         PrimarySkillService primarySkillService = new PrimarySkillService(this.characterService);
         ProfessionService professionService = new ProfessionService(this.characterService);
         StatService statService = new StatService(classRegistry, primarySkillService);
         CombatFormula combatFormula = new CombatFormula(combatSettings);
         this.combatStateService = new CombatStateService();
         TrainingWeaponService trainingWeaponService = new TrainingWeaponService(this, classRegistry, messages);
         WayfinderCodexService codexService = new WayfinderCodexService(this, messages, customItems, hudGlyphs, interfaceSettings);
         DamagePipeline damagePipeline = new DamagePipeline(this.characterService, statService, equipmentService, combatFormula);
         AbilityModifierService modifierService = new AbilityModifierService(abilityTreeRegistry);
         HudNotificationService hudNotifications = new HudNotificationService(hudSettings.notificationDurationMillis());
         ExperienceCurve experienceCurve = new ExperienceCurve(progressionSettings);
         ProgressionService progressionService = new ProgressionService(this.characterService, experienceCurve);
         NavigationHudService navigationHudService = new NavigationHudService(questRegistry, messages, hudSettings, this.scheduler);
         this.contributionLedger = new ContributionLedger();
         this.monsterRuntimeService = new MonsterRuntimeService(this, monsterRegistry, new MonsterScalingFormula(progressionSettings), this.characterService, this.scheduler, mythicMobsBridge, modelEngineBridge);
         AbilityExecutor abilityExecutor = new AbilityExecutor(statService, combatFormula, damagePipeline, equipmentService, modifierService, this.scheduler);
         AbilityCastService abilityCastService = new AbilityCastService(this.combatStateService, abilityExecutor, statService, equipmentService, messages, modifierService, hudNotifications);
         this.abilityInputService = new AbilityInputService(this.characterService, abilityRegistry, abilityCastService, trainingWeaponService, messages, hudNotifications, combatSettings.comboTimeoutMillis());
         this.combatHudService = new CombatHudService(this.characterService, statService, equipmentService, this.combatStateService, this.abilityInputService, hudNotifications, progressionService, navigationHudService, hudRenderer, this.scheduler, hudSettings, combatSettings.manaRegenerationPerSecond(), combatSettings.baseHealthRegenerationPerSecond(), combatSettings.healthToMinecraftScale());
         this.initializePlaceholderIntegration();
         this.activationService = new CharacterActivationService(statService, equipmentService, combatFormula, this.combatStateService, trainingWeaponService, this.combatHudService, codexService);
         QuestService questService = new QuestService(questRegistry, this.characterService, progressionService, this.activationService, hudNotifications, messages, mmoItemsBridge, professionService);
         this.dialogueService = new DialogueService(this, dialogueRegistry, this.characterService, questService);
         CitizensBridge citizensBridge = new CitizensBridge(this.getServer().getPluginManager(), this.getLogger());
         QuestIndicatorService questIndicators = new QuestIndicatorService(this, questRegistry, questService, this.characterService);
         this.npcBehaviorService = new NpcBehaviorService(this, citizensBridge, npcRegistry, questService, questIndicators);
         DiscoveryService discoveryService = new DiscoveryService(discoveryRegistry, this.characterService, progressionService, this.activationService, questService, hudNotifications, messages);
         this.encounterRuntimeService = new EncounterRuntimeService(monsterRegistry, this.monsterRuntimeService, this.characterService, progressionService, experienceCurve, this.activationService, hudNotifications, messages, this.scheduler, this.getLogger(), questService);
         this.selectorPresentationService = new CharacterSelectorPresentationService(this.scheduler);
         CharacterMenuService menuService = new CharacterMenuService(this.scheduler, this.characterService, classRegistry, questRegistry, discoveryRegistry, progressionService, messages, hudGlyphs, interfaceSettings, this.selectorPresentationService, settings.baseCharacterSlots(), settings.maximumCharacterSlots(), settings.deletionGraceMinutes());
         AbilityTreeService abilityTreeService = new AbilityTreeService(abilityTreeRegistry, this.characterService);
         AbilityTreeMenuService abilityTreeMenuService = new AbilityTreeMenuService(this.characterService, abilityTreeRegistry, abilityTreeService, classRegistry, messages);
         QuestJournalMenuService questJournalMenuService = new QuestJournalMenuService(this.characterService, questRegistry, questService, messages);
         ContentBookMenuService contentBookMenuService = new ContentBookMenuService(this.characterService, questRegistry, questService, discoveryRegistry, discoveryService, messages, hudGlyphs, interfaceSettings);
         SkillCrystalMenuService skillCrystalMenuService = new SkillCrystalMenuService(this.characterService, primarySkillService, equipmentService, customItems, messages);
         ProfessionMenuService professionMenuService = new ProfessionMenuService(this.characterService, professionService, customItems, messages);
         PartyMenuService partyMenuService = new PartyMenuService(this.partyService, this.characterService, classRegistry, messages);
         MainMenuService mainMenuService = new MainMenuService(this.characterService, classRegistry, questRegistry, discoveryRegistry, primarySkillService, abilityTreeService, professionService, progressionService, equipmentService, this.partyService, customItems, messages);
         CharacterProfileMenuService characterProfileMenuService = new CharacterProfileMenuService(this.characterService, classRegistry, questRegistry, discoveryRegistry, primarySkillService, abilityTreeService, professionService, progressionService, equipmentService, statService, this.partyService, messages);
         SpawnSafeZoneService safeZoneService = new SpawnSafeZoneService(abilityTreeSettings.safeZoneRadius());
         this.getServer().getPluginManager().registerEvents(new CharacterMenuListener(this.characterService, menuService, messages, this.activationService, this.abilityInputService, this.selectorPresentationService, this.scheduler), this);
         this.getServer().getPluginManager().registerEvents(new PlayerSessionListener(this.scheduler, this.characterService, menuService, messages, settings.openSelectorOnFirstJoin(), this.activationService, this.abilityInputService), this);
         this.getServer().getPluginManager().registerEvents(new AbilityInputListener(this.abilityInputService, this.characterService, trainingWeaponService, damagePipeline), this);
         this.getServer().getPluginManager().registerEvents(new AbilityTreeMenuListener(this.characterService, abilityTreeRegistry, abilityTreeService, abilityTreeMenuService, safeZoneService, messages), this);
         this.getServer().getPluginManager().registerEvents(new MainMenuListener(this.characterService, menuService, characterProfileMenuService, contentBookMenuService, skillCrystalMenuService, abilityTreeMenuService, professionMenuService, questJournalMenuService, partyMenuService, trainingWeaponService, messages), this);
         this.getServer().getPluginManager().registerEvents(new CharacterProfileMenuListener(this.characterService, characterProfileMenuService, mainMenuService, menuService, skillCrystalMenuService, abilityTreeMenuService, professionMenuService, questJournalMenuService, contentBookMenuService, partyMenuService), this);
         this.getServer().getPluginManager().registerEvents(new PartyMenuListener(this.partyService, partyMenuService, messages), this);
         this.getServer().getPluginManager().registerEvents(new SkillCrystalMenuListener(this.characterService, primarySkillService, skillCrystalMenuService, this.activationService, messages), this);
         this.getServer().getPluginManager().registerEvents(new ProfessionMenuListener(this.characterService, mainMenuService), this);
         this.getServer().getPluginManager().registerEvents(new ProfessionExperienceListener(this.characterService, professionService, hudNotifications, messages), this);
         this.getServer().getPluginManager().registerEvents(new EquipmentRefreshListener(this.characterService, this.activationService, this.scheduler), this);
         this.getServer().getPluginManager().registerEvents(new EquipmentUsageListener(this.characterService, equipmentService, hudNotifications), this);
         this.getServer().getPluginManager().registerEvents(new QuestJournalMenuListener(this.characterService, questJournalMenuService, questService, messages), this);
         this.getServer().getPluginManager().registerEvents(new QuestInteractionListener(questService, mmoItemsBridge, mythicMobsBridge, this.dialogueService), this);
         this.getServer().getPluginManager().registerEvents(new DialogueListener(this.dialogueService), this);
         this.getServer().getPluginManager().registerEvents(new QuestObjectiveListener(questService, mmoItemsBridge), this);
         if (citizensBridge.available()) {
            this.getServer().getPluginManager().registerEvents(new CitizensNpcListener(citizensBridge, npcRegistry, questRegistry, questService, this.dialogueService, this.npcBehaviorService, this.characterService, messages), this);
            this.getLogger().info("已載入 " + npcRegistry.size() + " 個 NPC 設定。");
         }
         this.getServer().getPluginManager().registerEvents(new ContentBookMenuListener(this.characterService, contentBookMenuService, questService, messages), this);
         this.getServer().getPluginManager().registerEvents(new WayfinderCodexListener(codexService, mainMenuService, mmoItemsBridge), this);
         this.getServer().getPluginManager().registerEvents(new DiscoveryMovementListener(discoveryService), this);
         this.getServer().getPluginManager().registerEvents(new IdentificationNpcListener(mythicMobsBridge, identificationMenuService, identificationSettings.npcMythicMobId()), this);
         this.getServer().getPluginManager().registerEvents(new IdentificationMenuListener(this.characterService, identificationMenuService, equipmentService, emeraldCurrencyService, messages), this);
         this.getServer().getPluginManager().registerEvents(new MonsterCombatListener(this.monsterRuntimeService, this.contributionLedger, new MonsterLootService(equipmentService), this.characterService, progressionService, experienceCurve, this.activationService, damagePipeline, hudNotifications, messages, this.scheduler, this.encounterRuntimeService, questService), this);
         PluginCommand command = (PluginCommand)Objects.requireNonNull(this.getCommand("rpg"), "rpg command");
         RpgCommand executor = new RpgCommand(menuService, classRegistry, abilityRegistry, abilityTreeRegistry, equipmentRegistry, monsterRegistry, encounterRegistry, questRegistry, discoveryRegistry, messages, classesFile, abilitiesFile, abilityTreeFile, equipmentFile, monstersFile, encountersFile, questsFile, discoveriesFile, this.getLogger(), this.characterService, this.activationService, trainingWeaponService, abilityTreeMenuService, questJournalMenuService, contentBookMenuService, mainMenuService, characterProfileMenuService, skillCrystalMenuService, professionMenuService, partyMenuService, this.scheduler, new FoliaSelfTestService(this.scheduler, this.getLogger(), messages), this.monsterRuntimeService, progressionService, this.encounterRuntimeService, this.combatHudService, this.hudPackService);
         command.setExecutor(executor);
         command.setTabCompleter(executor);
         this.hudPackService.initialize();
         Logger var10000 = this.getLogger();
         int var10001 = classRegistry.size();
         var10000.info("RPGCore enabled with " + var10001 + " classes, " + abilityRegistry.size() + " abilities, and " + abilityTreeRegistry.size() + " ability tree nodes, plus " + equipmentRegistry.size() + " equipment templates, " + monsterRegistry.size() + " monsters and " + encounterRegistry.size() + " encounters and " + questRegistry.size() + " quests and " + discoveryRegistry.size() + " discoveries on " + (this.scheduler.isFolia() ? "Folia" : "Paper/Purpur") + "; Wayfinder Codex uses hotbar slot " + (interfaceSettings.contentBookHotbarSlot() + 1) + ".");
      } catch (RuntimeException | IOException exception) {
         this.getLogger().log(Level.SEVERE, "RPGCore startup validation failed; disabling plugin", exception);
         this.getServer().getPluginManager().disablePlugin(this);
      }

   }

   private void loadAbilityTree(AbilityTreeRegistry abilityTreeRegistry, File abilityTreeFile, ClassRegistry classRegistry) throws IOException {
      try {
         abilityTreeRegistry.load(abilityTreeFile, classRegistry);
      } catch (IllegalArgumentException exception) {
         this.restoreBundledResource("ability-tree.yml", abilityTreeFile, "Ability tree validation failed: " + exception.getMessage());
         abilityTreeRegistry.load(abilityTreeFile, classRegistry);
      }

   }

   public void onDisable() {
      if (this.npcBehaviorService != null) {
         this.npcBehaviorService.shutdown();
      }

      if (this.dialogueService != null) {
         this.dialogueService.shutdown();
      }

      if (this.encounterRuntimeService != null) {
         this.encounterRuntimeService.shutdown();
      }

      if (this.monsterRuntimeService != null) {
         this.monsterRuntimeService.shutdown();
      }

      if (this.contributionLedger != null) {
         this.contributionLedger.clearAll();
      }

      if (this.partyService != null) {
         this.partyService.clear();
      }

      if (this.combatHudService != null) {
         this.combatHudService.shutdown();
      }

      if (this.hudPackService != null) {
         this.hudPackService.close();
      }

      this.closePlaceholderIntegration();
      if (this.selectorPresentationService != null) {
         this.selectorPresentationService.shutdown();
      }

      if (this.abilityInputService != null) {
         this.abilityInputService.clearAll();
      }

      if (this.combatStateService != null) {
         this.combatStateService.clear();
      }

      if (this.characterService != null) {
         this.characterService.close();
      }

      if (this.scheduler != null) {
         this.scheduler.shutdown();
      }

   }

   private void prepareVersionedResource(String name, int expectedSchema) throws IOException {
      File destination = new File(this.getDataFolder(), name);
      if (!destination.exists()) {
         this.saveResource(name, false);
      } else {
         YamlConfiguration current = YamlConfiguration.loadConfiguration(destination);
         int existingSchema = current.getInt("schema-version", -1);
         if (existingSchema != expectedSchema) {
            if (existingSchema > expectedSchema) {
               throw new IllegalArgumentException(name + " was created by a newer RPGCore version");
            } else {
               Path var10000 = destination.toPath();
               String var10001 = destination.getName();
               Path backup = var10000.resolveSibling(var10001 + ".schema-v" + existingSchema + ".bak");
               Files.copy(destination.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
               this.saveResource(name, true);
               this.getLogger().warning("Migrated " + name + " to schema " + expectedSchema + "; previous content was backed up to " + String.valueOf(backup.getFileName()));
            }
         }
      }
   }

   private void saveBundledResourceIfMissing(String name) {
      if (!(new File(this.getDataFolder(), name)).exists()) {
         this.saveResource(name, false);
      }

   }

   private synchronized boolean initializePlaceholderIntegration() {
      if (this.placeholderBridge != null) {
         return true;
      } else if (!this.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
         return false;
      } else {
         try {
            this.placeholderBridge = PlaceholderBridge.register(this, this.combatHudService, this.partyService, this.getLogger());
            return this.placeholderBridge != null;
         } catch (RuntimeException | LinkageError exception) {
            this.getLogger().log(Level.WARNING, "Could not register optional RPGCore placeholders; the internal HUD remains active", exception);
            return false;
         }
      }
   }

   private synchronized void closePlaceholderIntegration() {
      if (this.placeholderBridge != null) {
         try {
            this.placeholderBridge.close();
         } catch (LinkageError | Exception exception) {
            this.getLogger().log(Level.WARNING, "Could not unregister RPGCore placeholders", exception);
         } finally {
            this.placeholderBridge = null;
         }

      }
   }

   private void restoreBundledResource(String name, File destination, String reason) throws IOException {
      Path var10000 = destination.toPath();
      String var10001 = destination.getName();
      Path backup = var10000.resolveSibling(var10001 + ".invalid-" + System.currentTimeMillis() + ".bak");
      Files.copy(destination.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
      this.saveResource(name, true);
      this.getLogger().warning(reason + "; restored bundled " + name + " and backed up previous content to " + String.valueOf(backup.getFileName()));
   }
}
