package com.xuzhihuanjing.rpgcore.command;

import com.xuzhihuanjing.rpgcore.combat.CharacterActivationService;
import com.xuzhihuanjing.rpgcore.combat.CombatHudService;
import com.xuzhihuanjing.rpgcore.combat.TrainingWeaponService;
import com.xuzhihuanjing.rpgcore.config.AbilityRegistry;
import com.xuzhihuanjing.rpgcore.config.AbilityTreeRegistry;
import com.xuzhihuanjing.rpgcore.config.ClassRegistry;
import com.xuzhihuanjing.rpgcore.config.ContentGraphValidator;
import com.xuzhihuanjing.rpgcore.config.DiscoveryRegistry;
import com.xuzhihuanjing.rpgcore.config.EncounterRegistry;
import com.xuzhihuanjing.rpgcore.config.EquipmentRegistry;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.config.MonsterRegistry;
import com.xuzhihuanjing.rpgcore.config.QuestRegistry;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.discovery.DiscoveryDefinition;
import com.xuzhihuanjing.rpgcore.domain.encounter.EncounterDefinition;
import com.xuzhihuanjing.rpgcore.domain.monster.MonsterDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestDefinition;
import com.xuzhihuanjing.rpgcore.encounter.EncounterRuntimeService;
import com.xuzhihuanjing.rpgcore.encounter.EncounterSnapshot;
import com.xuzhihuanjing.rpgcore.encounter.EncounterStartResult;
import com.xuzhihuanjing.rpgcore.gui.AbilityTreeMenuService;
import com.xuzhihuanjing.rpgcore.gui.CharacterMenuService;
import com.xuzhihuanjing.rpgcore.gui.CharacterProfileMenuService;
import com.xuzhihuanjing.rpgcore.gui.ContentBookMenuService;
import com.xuzhihuanjing.rpgcore.gui.MainMenuService;
import com.xuzhihuanjing.rpgcore.gui.PartyMenuService;
import com.xuzhihuanjing.rpgcore.gui.ProfessionMenuService;
import com.xuzhihuanjing.rpgcore.gui.QuestJournalMenuService;
import com.xuzhihuanjing.rpgcore.gui.SkillCrystalMenuService;
import com.xuzhihuanjing.rpgcore.hud.CombatHudRenderer;
import com.xuzhihuanjing.rpgcore.hud.InternalHudPackService;
import com.xuzhihuanjing.rpgcore.monster.MonsterRuntimeService;
import com.xuzhihuanjing.rpgcore.platform.FoliaSelfTestService;
import com.xuzhihuanjing.rpgcore.platform.RpgScheduler;
import com.xuzhihuanjing.rpgcore.progression.ProgressionService;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RpgCommand implements CommandExecutor, TabCompleter {
   private final CharacterMenuService menuService;
   private final ClassRegistry classRegistry;
   private final AbilityRegistry abilityRegistry;
   private final AbilityTreeRegistry abilityTreeRegistry;
   private final EquipmentRegistry equipmentRegistry;
   private final MonsterRegistry monsterRegistry;
   private final EncounterRegistry encounterRegistry;
   private final QuestRegistry questRegistry;
   private final DiscoveryRegistry discoveryRegistry;
   private final MessageBundle messages;
   private final File classesFile;
   private final File abilitiesFile;
   private final File abilityTreeFile;
   private final File equipmentFile;
   private final File monstersFile;
   private final File encountersFile;
   private final File questsFile;
   private final File discoveriesFile;
   private final Logger logger;
   private final CharacterService characterService;
   private final CharacterActivationService activationService;
   private final TrainingWeaponService trainingWeaponService;
   private final AbilityTreeMenuService abilityTreeMenuService;
   private final QuestJournalMenuService questJournalMenuService;
   private final ContentBookMenuService contentBookMenuService;
   private final MainMenuService mainMenuService;
   private final CharacterProfileMenuService characterProfileMenuService;
   private final SkillCrystalMenuService skillCrystalMenuService;
   private final ProfessionMenuService professionMenuService;
   private final PartyMenuService partyMenuService;
   private final RpgScheduler scheduler;
   private final FoliaSelfTestService selfTestService;
   private final MonsterRuntimeService monsterRuntimeService;
   private final ProgressionService progressionService;
   private final EncounterRuntimeService encounterRuntimeService;
   private final CombatHudService combatHudService;
   private final InternalHudPackService hudPackService;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();

   public RpgCommand(CharacterMenuService menuService, ClassRegistry classRegistry, AbilityRegistry abilityRegistry, AbilityTreeRegistry abilityTreeRegistry, EquipmentRegistry equipmentRegistry, MonsterRegistry monsterRegistry, EncounterRegistry encounterRegistry, QuestRegistry questRegistry, DiscoveryRegistry discoveryRegistry, MessageBundle messages, File classesFile, File abilitiesFile, File abilityTreeFile, File equipmentFile, File monstersFile, File encountersFile, File questsFile, File discoveriesFile, Logger logger, CharacterService characterService, CharacterActivationService activationService, TrainingWeaponService trainingWeaponService, AbilityTreeMenuService abilityTreeMenuService, QuestJournalMenuService questJournalMenuService, ContentBookMenuService contentBookMenuService, MainMenuService mainMenuService, CharacterProfileMenuService characterProfileMenuService, SkillCrystalMenuService skillCrystalMenuService, ProfessionMenuService professionMenuService, PartyMenuService partyMenuService, RpgScheduler scheduler, FoliaSelfTestService selfTestService, MonsterRuntimeService monsterRuntimeService, ProgressionService progressionService, EncounterRuntimeService encounterRuntimeService, CombatHudService combatHudService, InternalHudPackService hudPackService) {
      this.menuService = menuService;
      this.classRegistry = classRegistry;
      this.abilityRegistry = abilityRegistry;
      this.abilityTreeRegistry = abilityTreeRegistry;
      this.equipmentRegistry = equipmentRegistry;
      this.monsterRegistry = monsterRegistry;
      this.encounterRegistry = encounterRegistry;
      this.questRegistry = questRegistry;
      this.discoveryRegistry = discoveryRegistry;
      this.messages = messages;
      this.classesFile = classesFile;
      this.abilitiesFile = abilitiesFile;
      this.abilityTreeFile = abilityTreeFile;
      this.equipmentFile = equipmentFile;
      this.monstersFile = monstersFile;
      this.encountersFile = encountersFile;
      this.questsFile = questsFile;
      this.discoveriesFile = discoveriesFile;
      this.logger = logger;
      this.characterService = characterService;
      this.activationService = activationService;
      this.trainingWeaponService = trainingWeaponService;
      this.abilityTreeMenuService = abilityTreeMenuService;
      this.questJournalMenuService = questJournalMenuService;
      this.contentBookMenuService = contentBookMenuService;
      this.mainMenuService = mainMenuService;
      this.characterProfileMenuService = characterProfileMenuService;
      this.skillCrystalMenuService = skillCrystalMenuService;
      this.professionMenuService = professionMenuService;
      this.partyMenuService = partyMenuService;
      this.scheduler = scheduler;
      this.selfTestService = selfTestService;
      this.monsterRuntimeService = monsterRuntimeService;
      this.progressionService = progressionService;
      this.encounterRuntimeService = encounterRuntimeService;
      this.combatHudService = combatHudService;
      this.hudPackService = hudPackService;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      String subcommand = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
      if (subcommand.equals("selftest")) {
         if (!sender.hasPermission("rpgcore.admin")) {
            sender.sendMessage(this.messages.message("no-permission"));
            return true;
         } else {
            this.selfTestService.run(sender);
            return true;
         }
      } else if (subcommand.equals("reload")) {
         if (!sender.hasPermission("rpgcore.admin")) {
            sender.sendMessage(this.messages.message("no-permission"));
            return true;
         } else {
            this.scheduler.executeGlobal(() -> this.reloadContent(sender));
            return true;
         }
      } else if (subcommand.equals("mob")) {
         if (!sender.hasPermission("rpgcore.admin")) {
            sender.sendMessage(this.messages.message("no-permission"));
            return true;
         } else {
            return this.mobCommand(sender, args);
         }
      } else if (subcommand.equals("encounter")) {
         if (!sender.hasPermission("rpgcore.admin")) {
            sender.sendMessage(this.messages.message("no-permission"));
            return true;
         } else {
            return this.encounterCommand(sender, args);
         }
      } else if (subcommand.equals("quest")) {
         if (!sender.hasPermission("rpgcore.admin")) {
            sender.sendMessage(this.messages.message("no-permission"));
            return true;
         } else {
            return this.questAdminCommand(sender, args);
         }
      } else if (subcommand.equals("discovery")) {
         if (!sender.hasPermission("rpgcore.admin")) {
            sender.sendMessage(this.messages.message("no-permission"));
            return true;
         } else {
            return this.discoveryAdminCommand(sender, args);
         }
      } else if (!sender.hasPermission("rpgcore.player")) {
         sender.sendMessage(this.messages.message("no-permission"));
         return true;
      } else if (subcommand.equals("menu")) {
         if (sender instanceof Player) {
            Player player = (Player)sender;
            this.openDefaultMenu(player);
            return true;
         } else {
            sender.sendMessage(this.messages.message("no-console"));
            return true;
         }
      } else if (subcommand.equals("hud")) {
         return this.hudCommand(sender, args);
      } else if (!subcommand.equals("character") && !subcommand.equals("characters")) {
         if (!subcommand.equals("journal") && !subcommand.equals("quests")) {
            if (!subcommand.equals("book") && !subcommand.equals("content")) {
               if (!subcommand.equals("skills") && !subcommand.equals("skill")) {
                  if (!subcommand.equals("professions") && !subcommand.equals("profession")) {
                     if (!subcommand.equals("party") && !subcommand.equals("group")) {
                        if (subcommand.equals("identify")) {
                           sender.sendMessage(this.messages.message("identify-npc-only"));
                           return true;
                        } else if (subcommand.equals("weapon")) {
                           if (sender instanceof Player) {
                              Player player = (Player)sender;
                              CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
                              if (character == null) {
                                 player.sendMessage(this.messages.message("no-active-character"));
                                 return true;
                              } else {
                                 this.trainingWeaponService.ensure(player, character);
                                 player.sendMessage(this.messages.message("training-weapon-restored"));
                                 return true;
                              }
                           } else {
                              sender.sendMessage(this.messages.message("no-console"));
                              return true;
                           }
                        } else if (subcommand.equals("profile")) {
                           if (sender instanceof Player) {
                              Player player = (Player)sender;
                              this.characterProfileMenuService.open(player);
                              return true;
                           } else {
                              sender.sendMessage(this.messages.message("no-console"));
                              return true;
                           }
                        } else if (!subcommand.equals("abilities") && !subcommand.equals("tree")) {
                           if (sender instanceof Player) {
                              Player player = (Player)sender;
                              this.openDefaultMenu(player);
                              return true;
                           } else {
                              sender.sendMessage(this.messages.message("no-console"));
                              return true;
                           }
                        } else if (sender instanceof Player) {
                           Player player = (Player)sender;
                           this.abilityTreeMenuService.open(player);
                           return true;
                        } else {
                           sender.sendMessage(this.messages.message("no-console"));
                           return true;
                        }
                     } else if (sender instanceof Player) {
                        Player player = (Player)sender;
                        this.partyMenuService.open(player);
                        return true;
                     } else {
                        sender.sendMessage(this.messages.message("no-console"));
                        return true;
                     }
                  } else if (sender instanceof Player) {
                     Player player = (Player)sender;
                     this.professionMenuService.open(player);
                     return true;
                  } else {
                     sender.sendMessage(this.messages.message("no-console"));
                     return true;
                  }
               } else if (sender instanceof Player) {
                  Player player = (Player)sender;
                  this.skillCrystalMenuService.open(player);
                  return true;
               } else {
                  sender.sendMessage(this.messages.message("no-console"));
                  return true;
               }
            } else if (sender instanceof Player) {
               Player player = (Player)sender;
               this.contentBookMenuService.open(player);
               return true;
            } else {
               sender.sendMessage(this.messages.message("no-console"));
               return true;
            }
         } else if (sender instanceof Player) {
            Player player = (Player)sender;
            this.questJournalMenuService.open(player);
            return true;
         } else {
            sender.sendMessage(this.messages.message("no-console"));
            return true;
         }
      } else if (sender instanceof Player) {
         Player player = (Player)sender;
         this.menuService.openCharacterSelector(player);
         return true;
      } else {
         sender.sendMessage(this.messages.message("no-console"));
         return true;
      }
   }

   public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
      if (args.length == 1) {
         List<String> options = new ArrayList(List.of("menu", "character", "skills", "abilities", "professions", "journal", "book", "party", "weapon", "profile", "hud"));
         if (sender.hasPermission("rpgcore.admin")) {
            options.add("mob");
            options.add("encounter");
            options.add("quest");
            options.add("discovery");
            options.add("reload");
            options.add("selftest");
         }

         String prefix = args[0].toLowerCase(Locale.ROOT);
         return options.stream().filter((option) -> option.startsWith(prefix)).toList();
      } else if (args[0].equalsIgnoreCase("hud") && args.length == 2) {
         List<String> options = new ArrayList(List.of("status", "on", "off", "toggle", "refresh"));
         if (sender.hasPermission("rpgcore.admin")) {
            options.add("pack");
         }

         return this.filterPrefix(options, args[1]);
      } else if (!sender.hasPermission("rpgcore.admin")) {
         return List.of();
      } else {
         if (args[0].equalsIgnoreCase("mob")) {
            if (args.length == 2) {
               return this.filterPrefix(List.of("list", "spawn", "spawnat"), args[1]);
            }

            if (args.length == 3 && (args[1].equalsIgnoreCase("spawn") || args[1].equalsIgnoreCase("spawnat"))) {
               return this.filterPrefix(this.monsterRegistry.all().stream().map(MonsterDefinition::id).sorted().toList(), args[2]);
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("spawn")) {
               return (List)this.monsterRegistry.find(args[2]).map((definition) -> this.filterPrefix(List.of(Integer.toString(definition.baseLevel())), args[3])).orElse(List.of());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("spawnat")) {
               return this.filterPrefix(Bukkit.getWorlds().stream().map(WorldInfo::getName).toList(), args[3]);
            }
         }

         if (args[0].equalsIgnoreCase("encounter")) {
            if (args.length == 2) {
               return this.filterPrefix(List.of("list", "start", "startat", "status", "cancel"), args[1]);
            }

            if (args.length == 3 && (args[1].equalsIgnoreCase("start") || args[1].equalsIgnoreCase("startat"))) {
               return this.filterPrefix(this.encounterRegistry.all().stream().map(EncounterDefinition::id).sorted().toList(), args[2]);
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("cancel")) {
               return this.filterPrefix(List.of("all"), args[2]);
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("start")) {
               return (List)this.encounterRegistry.find(args[2]).map((definition) -> this.filterPrefix(List.of(Integer.toString(definition.minimumLevel())), args[3])).orElse(List.of());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("startat")) {
               return this.filterPrefix(Bukkit.getWorlds().stream().map(WorldInfo::getName).toList(), args[3]);
            }
         }

         if (args[0].equalsIgnoreCase("quest")) {
            if (args.length == 2) {
               return this.filterPrefix(List.of("list", "inspect"), args[1]);
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("inspect")) {
               return this.filterPrefix(this.questRegistry.all().stream().map(QuestDefinition::id).sorted().toList(), args[2]);
            }
         }

         if (args[0].equalsIgnoreCase("discovery")) {
            if (args.length == 2) {
               return this.filterPrefix(List.of("list", "inspect"), args[1]);
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("inspect")) {
               return this.filterPrefix(this.discoveryRegistry.all().stream().map(DiscoveryDefinition::id).sorted().toList(), args[2]);
            }
         }

         return List.of();
      }
   }

   private boolean hudCommand(CommandSender sender, String[] args) {
      String action = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
      if (action.equals("pack")) {
         if (!sender.hasPermission("rpgcore.admin")) {
            sender.sendMessage(this.messages.message("no-permission"));
            return true;
         } else {
            boolean requested = this.hudPackService.requestRegeneration(true);
            sender.sendMessage(this.messages.message(requested ? "hud-pack-regeneration-started" : "hud-pack-regeneration-busy"));
            return true;
         }
      } else if (sender instanceof Player) {
         Player player = (Player)sender;
         switch (action) {
            case "on":
               this.combatHudService.setHudEnabled(player, true);
               player.sendMessage(this.messages.message("hud-enabled"));
               break;
            case "off":
               this.combatHudService.setHudEnabled(player, false);
               player.sendMessage(this.messages.message("hud-disabled"));
               break;
            case "toggle":
               boolean enabled = !this.combatHudService.hudEnabled(player);
               this.combatHudService.setHudEnabled(player, enabled);
               player.sendMessage(this.messages.message(enabled ? "hud-enabled" : "hud-disabled"));
               break;
            case "refresh":
               this.combatHudService.refreshHud(player);
               player.sendMessage(this.messages.message("hud-refreshed"));
               break;
            case "status":
               this.sendHudStatus(player);
               break;
            default:
               player.sendMessage(this.messages.message("hud-usage"));
         }

         return true;
      } else {
         sender.sendMessage(this.messages.message("no-console"));
         return true;
      }
   }

   private void sendHudStatus(Player player) {
      CombatHudRenderer.Status runtime = this.combatHudService.hudStatus(player);
      InternalHudPackService.Status pack = this.hudPackService.status();
      player.sendMessage(this.messages.message("hud-status", MessageBundle.value("renderer", this.localizedHudState(runtime.renderer())), MessageBundle.value("enabled", this.localizedBoolean(runtime.enabled())), MessageBundle.value("visible", this.localizedBoolean(runtime.visible())), MessageBundle.value("resource-pack", this.localizedHudState(runtime.resourcePackState())), MessageBundle.value("assets", this.localizedHudState(pack.state()))));
   }

   private String localizedBoolean(boolean value) {
      return value ? "是" : "否";
   }

   private String localizedHudState(String state) {
      String var10000;
      switch (state) {
         case "internal" -> var10000 = "RPGCore 內建";
         case "native" -> var10000 = "原生";
         case "waiting" -> var10000 = "等待載入";
         case "loading" -> var10000 = "載入中";
         case "ready" -> var10000 = "已就緒";
         case "blocked" -> var10000 = "拒絕或失敗";
         case "not-required" -> var10000 = "不需要";
         case "disabled" -> var10000 = "未啟用";
         case "update-required" -> var10000 = "需要重建";
         default -> var10000 = state;
      }

      return var10000;
   }

   private void reloadContent(CommandSender sender) {
      if (!this.encounterRuntimeService.executeIfIdle(() -> this.reloadContentWhileIdle(sender))) {
         this.reply(sender, this.messages.message("reload-encounter-active"));
      }

   }

   private void reloadContentWhileIdle(CommandSender sender) {
      try {
         ClassRegistry candidateClasses = new ClassRegistry();
         candidateClasses.load(this.classesFile);
         AbilityRegistry candidateAbilities = new AbilityRegistry();
         candidateAbilities.load(this.abilitiesFile, candidateClasses);
         AbilityTreeRegistry candidateTree = new AbilityTreeRegistry();
         candidateTree.load(this.abilityTreeFile, candidateClasses);
         EquipmentRegistry candidateEquipment = new EquipmentRegistry();
         candidateEquipment.load(this.equipmentFile, candidateClasses);
         MonsterRegistry candidateMonsters = new MonsterRegistry();
         candidateMonsters.load(this.monstersFile, candidateEquipment);
         EncounterRegistry candidateEncounters = new EncounterRegistry();
         candidateEncounters.load(this.encountersFile, candidateMonsters);
         DiscoveryRegistry candidateDiscoveries = new DiscoveryRegistry();
         candidateDiscoveries.load(this.discoveriesFile);
         QuestRegistry candidateQuests = new QuestRegistry();
         candidateQuests.load(this.questsFile, candidateMonsters, candidateEncounters, candidateDiscoveries);
         ContentGraphValidator.validate(candidateQuests, candidateDiscoveries);
         this.classRegistry.replaceWith(candidateClasses);
         this.abilityRegistry.replaceWith(candidateAbilities);
         this.abilityTreeRegistry.replaceWith(candidateTree);
         this.equipmentRegistry.replaceWith(candidateEquipment);
         this.monsterRegistry.replaceWith(candidateMonsters);
         this.encounterRegistry.replaceWith(candidateEncounters);
         this.questRegistry.replaceWith(candidateQuests);
         this.discoveryRegistry.replaceWith(candidateDiscoveries);

         for(Player player : Bukkit.getOnlinePlayers()) {
            this.scheduler.executeEntity(player, () -> this.characterService.activeCharacter(player.getUniqueId()).ifPresent((character) -> this.activationService.activate(player, character)), () -> this.characterService.saveAndUnload(player.getUniqueId()));
         }

         this.reply(sender, this.messages.message("reload-complete", MessageBundle.value("classes", Integer.toString(this.classRegistry.size())), MessageBundle.value("abilities", Integer.toString(this.abilityRegistry.size())), MessageBundle.value("nodes", Integer.toString(this.abilityTreeRegistry.size())), MessageBundle.value("equipment", Integer.toString(this.equipmentRegistry.size())), MessageBundle.value("monsters", Integer.toString(this.monsterRegistry.size())), MessageBundle.value("encounters", Integer.toString(this.encounterRegistry.size())), MessageBundle.value("quests", Integer.toString(this.questRegistry.size())), MessageBundle.value("discoveries", Integer.toString(this.discoveryRegistry.size()))));
      } catch (RuntimeException exception) {
         this.logger.log(Level.SEVERE, "Could not reload RPGCore class definitions", exception);
         this.reply(sender, this.messages.message("reload-failed"));
      }

   }

   private boolean encounterCommand(CommandSender sender, String[] args) {
      boolean var10000;
      switch (args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT)) {
         case "list" -> var10000 = this.listEncounters(sender);
         case "status" -> var10000 = this.encounterStatus(sender);
         case "start" -> var10000 = this.startEncounter(sender, args);
         case "startat" -> var10000 = this.startEncounterAt(sender, args);
         case "cancel" -> var10000 = this.cancelEncounter(sender, args);
         default -> var10000 = this.listEncounters(sender);
      }

      return var10000;
   }

   private boolean questAdminCommand(CommandSender sender, String[] args) {
      String action = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
      if (action.equals("inspect") && args.length >= 3) {
         QuestDefinition quest = (QuestDefinition)this.questRegistry.find(args[2].toLowerCase(Locale.ROOT)).orElse(null);
         if (quest == null) {
            sender.sendMessage(this.messages.message("quest-unknown", MessageBundle.value("quest", args[2])));
            return true;
         } else {
            String prerequisites = quest.prerequisites().isEmpty() ? "-" : String.join(", ", quest.prerequisites());
            String objectives = (String)quest.objectives().stream().map((objective) -> {
               String var10000 = objective.type().name();
               return var10000 + ":" + objective.target() + " x" + objective.requiredAmount();
            }).reduce((left, right) -> left + ", " + right).orElse("-");
            sender.sendMessage(this.messages.message("quest-inspect", MessageBundle.value("id", quest.id()), MessageBundle.value("name", this.miniMessage.stripTags(quest.displayName())), MessageBundle.value("level", Integer.toString(quest.minimumLevel())), MessageBundle.value("prerequisites", prerequisites), MessageBundle.value("objectives", objectives), MessageBundle.value("experience", Long.toString(quest.rewardExperience()))));
            return true;
         }
      } else {
         String names = (String)this.questRegistry.all().stream().sorted(Comparator.comparingInt(QuestDefinition::minimumLevel).thenComparing(QuestDefinition::id)).map((questx) -> {
            String var10000 = questx.id();
            return var10000 + " [Lv." + questx.minimumLevel() + ", " + questx.category().name() + "]";
         }).reduce((left, right) -> left + ", " + right).orElse("-");
         sender.sendMessage(this.messages.message("quest-list-header", MessageBundle.value("quests", names)));
         return true;
      }
   }

   private boolean discoveryAdminCommand(CommandSender sender, String[] args) {
      String action = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
      if (action.equals("inspect") && args.length >= 3) {
         DiscoveryDefinition discovery = (DiscoveryDefinition)this.discoveryRegistry.find(args[2].toLowerCase(Locale.ROOT)).orElse(null);
         if (discovery == null) {
            sender.sendMessage(this.messages.message("discovery-unknown", MessageBundle.value("discovery", args[2])));
            return true;
         } else {
            sender.sendMessage(this.messages.message("discovery-inspect", MessageBundle.value("id", discovery.id()), MessageBundle.value("name", this.miniMessage.stripTags(discovery.displayName())), MessageBundle.value("level", Integer.toString(discovery.minimumLevel())), MessageBundle.value("world", discovery.world()), MessageBundle.value("x", this.coordinate(discovery.x())), MessageBundle.value("y", this.coordinate(discovery.y())), MessageBundle.value("z", this.coordinate(discovery.z())), MessageBundle.value("radius", this.coordinate(discovery.radius())), MessageBundle.value("prerequisites", discovery.prerequisites().isEmpty() ? "-" : String.join(", ", discovery.prerequisites())), MessageBundle.value("quests", discovery.requiredQuests().isEmpty() ? "-" : String.join(", ", discovery.requiredQuests())), MessageBundle.value("experience", Long.toString(discovery.rewardExperience()))));
            return true;
         }
      } else {
         String names = (String)this.discoveryRegistry.all().stream().sorted(Comparator.comparingInt(DiscoveryDefinition::minimumLevel).thenComparing(DiscoveryDefinition::id)).map((discoveryx) -> {
            String var10000 = discoveryx.id();
            return var10000 + " [Lv." + discoveryx.minimumLevel() + ", " + discoveryx.category().name() + "]";
         }).reduce((left, right) -> left + ", " + right).orElse("-");
         sender.sendMessage(this.messages.message("discovery-list-header", MessageBundle.value("discoveries", names)));
         return true;
      }
   }

   private String coordinate(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }

   private void openDefaultMenu(Player player) {
      if (this.characterService.activeCharacter(player.getUniqueId()).isPresent()) {
         this.mainMenuService.open(player);
      } else {
         this.menuService.openCharacterSelector(player);
      }

   }

   private boolean listEncounters(CommandSender sender) {
      String names = (String)this.encounterRegistry.all().stream().sorted(Comparator.comparing(EncounterDefinition::id)).map((definition) -> {
         String var10000 = definition.id();
         return var10000 + " [" + definition.minimumLevel() + "-" + definition.maximumLevel() + ", " + definition.waves().size() + " waves]";
      }).reduce((left, right) -> left + ", " + right).orElse("-");
      sender.sendMessage(this.messages.message("encounter-list-header", MessageBundle.value("encounters", names)));
      return true;
   }

   private boolean encounterStatus(CommandSender sender) {
      List<EncounterSnapshot> snapshots = this.encounterRuntimeService.snapshots();
      if (snapshots.isEmpty()) {
         sender.sendMessage(this.messages.message("encounter-none-active"));
         return true;
      } else {
         sender.sendMessage(this.messages.message("encounter-status-header", MessageBundle.value("amount", Integer.toString(snapshots.size()))));

         for(EncounterSnapshot snapshot : snapshots) {
            sender.sendMessage(this.messages.message("encounter-status-entry", MessageBundle.value("id", snapshot.runId().toString().substring(0, 8)), MessageBundle.value("name", this.miniMessage.stripTags(snapshot.displayName())), MessageBundle.value("level", Integer.toString(snapshot.level())), MessageBundle.value("wave", Integer.toString(snapshot.currentWave())), MessageBundle.value("total", Integer.toString(snapshot.waveCount())), MessageBundle.value("remaining", Integer.toString(snapshot.remainingMonsters())), MessageBundle.value("participants", Integer.toString(snapshot.participants())), MessageBundle.value("world", snapshot.world()), MessageBundle.value("x", Integer.toString(snapshot.blockX())), MessageBundle.value("y", Integer.toString(snapshot.blockY())), MessageBundle.value("z", Integer.toString(snapshot.blockZ()))));
         }

         return true;
      }
   }

   private boolean startEncounter(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         if (args.length < 3) {
            return this.listEncounters(sender);
         } else {
            EncounterDefinition definition = this.encounterDefinition(sender, args[2]);
            if (definition == null) {
               return true;
            } else {
               int fallback = (Integer)this.characterService.activeCharacter(player.getUniqueId()).map(CharacterProfile::level).map((levelx) -> Math.max(definition.minimumLevel(), Math.min(definition.maximumLevel(), levelx))).orElse(definition.minimumLevel());
               Integer level = this.parseInteger(args, 3, fallback);
               if (!this.validEncounterLevel(sender, definition, level)) {
                  return true;
               } else {
                  this.scheduler.executeEntity(player, () -> {
                     EncounterStartResult result = this.encounterRuntimeService.start(player.getLocation(), definition, level, player.getUniqueId());
                     this.sendEncounterStartResult(player, definition, level, result);
                  }, () -> {
                  });
                  return true;
               }
            }
         }
      } else {
         sender.sendMessage(this.messages.message("no-console"));
         return true;
      }
   }

   private boolean startEncounterAt(CommandSender sender, String[] args) {
      if (args.length < 7) {
         sender.sendMessage(this.messages.message("encounter-invalid-location"));
         return true;
      } else {
         EncounterDefinition definition = this.encounterDefinition(sender, args[2]);
         if (definition == null) {
            return true;
         } else {
            World world = Bukkit.getWorld(args[3]);
            Double x = this.parseDouble(args[4]);
            Double y = this.parseDouble(args[5]);
            Double z = this.parseDouble(args[6]);
            if (world != null && x != null && y != null && z != null) {
               Integer level = this.parseInteger(args, 7, definition.minimumLevel());
               if (!this.validEncounterLevel(sender, definition, level)) {
                  return true;
               } else {
                  Location center = new Location(world, x, y, z);
                  UUID var10000;
                  if (sender instanceof Player) {
                     Player player = (Player)sender;
                     var10000 = player.getUniqueId();
                  } else {
                     var10000 = null;
                  }

                  UUID starterId = var10000;
                  this.scheduler.executeRegion(center, () -> {
                     EncounterStartResult result = this.encounterRuntimeService.start(center, definition, level, starterId);
                     Component response = this.encounterStartMessage(definition, level, result);
                     if (sender instanceof Player player) {
                        this.reply(player, response);
                     } else {
                        this.scheduler.executeGlobal(() -> sender.sendMessage(response));
                     }

                  });
                  return true;
               }
            } else {
               sender.sendMessage(this.messages.message("encounter-invalid-location"));
               return true;
            }
         }
      }
   }

   private boolean cancelEncounter(CommandSender sender, String[] args) {
      if (args.length >= 3 && args[2].equalsIgnoreCase("all")) {
         int cancelled = this.encounterRuntimeService.cancelAll();
         sender.sendMessage(this.messages.message("encounter-cancel-result", MessageBundle.value("amount", Integer.toString(cancelled))));
         return true;
      } else if (args.length >= 3) {
         boolean cancelled = this.encounterRuntimeService.cancelByPrefix(args[2]);
         sender.sendMessage(this.messages.message(cancelled ? "encounter-cancel-result" : "encounter-cancel-unknown", MessageBundle.value("amount", cancelled ? "1" : "0")));
         return true;
      } else if (sender instanceof Player) {
         Player player = (Player)sender;
         this.scheduler.executeEntity(player, () -> {
            boolean cancelled = this.encounterRuntimeService.cancelNearest(player.getLocation());
            player.sendMessage(this.messages.message(cancelled ? "encounter-cancel-result" : "encounter-cancel-unknown", MessageBundle.value("amount", cancelled ? "1" : "0")));
         }, () -> {
         });
         return true;
      } else {
         sender.sendMessage(this.messages.message("encounter-cancel-unknown"));
         return true;
      }
   }

   private EncounterDefinition encounterDefinition(CommandSender sender, String id) {
      EncounterDefinition definition = (EncounterDefinition)this.encounterRegistry.find(id.toLowerCase(Locale.ROOT)).orElse(null);
      if (definition == null) {
         sender.sendMessage(this.messages.message("encounter-unknown", MessageBundle.value("encounter", id)));
      }

      return definition;
   }

   private boolean validEncounterLevel(CommandSender sender, EncounterDefinition definition, Integer level) {
      if (level != null && level >= definition.minimumLevel() && level <= definition.maximumLevel()) {
         return true;
      } else {
         sender.sendMessage(this.messages.message("encounter-invalid-level", MessageBundle.value("minimum", Integer.toString(definition.minimumLevel())), MessageBundle.value("maximum", Integer.toString(definition.maximumLevel()))));
         return false;
      }
   }

   private void sendEncounterStartResult(CommandSender sender, EncounterDefinition definition, int level, EncounterStartResult result) {
      sender.sendMessage(this.encounterStartMessage(definition, level, result));
   }

   private Component encounterStartMessage(EncounterDefinition definition, int level, EncounterStartResult result) {
      Component var10000;
      switch (result.status()) {
         case STARTED -> var10000 = this.messages.message("encounter-started", MessageBundle.value("name", this.miniMessage.stripTags(definition.displayName())), MessageBundle.value("level", Integer.toString(level)), MessageBundle.value("id", result.encounterId().toString().substring(0, 8)));
         case INVALID_LEVEL -> var10000 = this.messages.message("encounter-invalid-level", MessageBundle.value("minimum", Integer.toString(definition.minimumLevel())), MessageBundle.value("maximum", Integer.toString(definition.maximumLevel())));
         case OVERLAPPING -> var10000 = this.messages.message("encounter-overlap");
         case COOLDOWN -> var10000 = this.messages.message("encounter-cooldown", MessageBundle.value("seconds", Long.toString(result.remainingCooldownSeconds())));
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private boolean mobCommand(CommandSender sender, String[] args) {
      if (args.length >= 2 && !args[1].equalsIgnoreCase("list")) {
         if (args[1].equalsIgnoreCase("spawnat")) {
            return this.spawnAtCommand(sender, args);
         } else if (args[1].equalsIgnoreCase("spawn") && sender instanceof Player) {
            Player player = (Player)sender;
            if (args.length < 3) {
               player.sendMessage(this.messages.message("monster-list-header", MessageBundle.value("monsters", "/rpg mob spawn <id> [level] [amount]")));
               return true;
            } else {
               MonsterDefinition definition = (MonsterDefinition)this.monsterRegistry.find(args[2].toLowerCase(Locale.ROOT)).orElse(null);
               if (definition == null) {
                  player.sendMessage(this.messages.message("monster-unknown", MessageBundle.value("monster", args[2])));
                  return true;
               } else {
                  Integer level = this.parseInteger(args, 3, definition.baseLevel());
                  if (level != null && level >= definition.minimumLevel() && level <= definition.maximumLevel()) {
                     Integer amount = this.parseInteger(args, 4, 1);
                     if (amount != null && amount >= 1 && amount <= 10) {
                        int spawnLevel = level;
                        int spawnAmount = amount;
                        this.scheduler.executeEntity(player, () -> {
                           Location origin = player.getLocation().clone();
                           this.spawnRing(origin, definition, spawnLevel, spawnAmount, (double)3.0F);
                           player.sendMessage(this.messages.message("monster-spawned", MessageBundle.value("amount", Integer.toString(spawnAmount)), MessageBundle.value("monster", this.miniMessage.stripTags(definition.displayName())), MessageBundle.value("level", Integer.toString(spawnLevel))));
                        }, () -> {
                        });
                        return true;
                     } else {
                        player.sendMessage(this.messages.message("monster-invalid-amount"));
                        return true;
                     }
                  } else {
                     player.sendMessage(this.messages.message("monster-invalid-level", MessageBundle.value("minimum", Integer.toString(definition.minimumLevel())), MessageBundle.value("maximum", Integer.toString(definition.maximumLevel()))));
                     return true;
                  }
               }
            }
         } else {
            sender.sendMessage(this.messages.message(sender instanceof Player ? "monster-list-header" : "no-console", MessageBundle.value("monsters", "/rpg mob list | /rpg mob spawn <id> [level] [amount]")));
            return true;
         }
      } else {
         String names = (String)this.monsterRegistry.all().stream().sorted(Comparator.comparing(MonsterDefinition::id)).map((definitionx) -> {
            String var10000 = definitionx.id();
            return var10000 + " [" + definitionx.minimumLevel() + "-" + definitionx.maximumLevel() + "]";
         }).reduce((left, right) -> left + ", " + right).orElse("-");
         sender.sendMessage(this.messages.message("monster-list-header", MessageBundle.value("monsters", names)));
         return true;
      }
   }

   private boolean spawnAtCommand(CommandSender sender, String[] args) {
      if (args.length < 7) {
         sender.sendMessage(this.messages.message("monster-invalid-location"));
         return true;
      } else {
         MonsterDefinition definition = (MonsterDefinition)this.monsterRegistry.find(args[2].toLowerCase(Locale.ROOT)).orElse(null);
         if (definition == null) {
            sender.sendMessage(this.messages.message("monster-unknown", MessageBundle.value("monster", args[2])));
            return true;
         } else {
            World world = Bukkit.getWorld(args[3]);
            Double x = this.parseDouble(args[4]);
            Double y = this.parseDouble(args[5]);
            Double z = this.parseDouble(args[6]);
            if (world != null && x != null && y != null && z != null) {
               Integer level = this.parseInteger(args, 7, definition.baseLevel());
               if (level != null && level >= definition.minimumLevel() && level <= definition.maximumLevel()) {
                  Integer amount = this.parseInteger(args, 8, 1);
                  if (amount != null && amount >= 1 && amount <= 10) {
                     Location origin = new Location(world, x, y, z);
                     int spawnLevel = level;
                     int spawnAmount = amount;
                     this.scheduler.executeRegion(origin, () -> {
                        this.spawnRing(origin, definition, spawnLevel, spawnAmount, (double)1.25F);
                        Component confirmation = this.messages.message("monster-spawned", MessageBundle.value("amount", Integer.toString(spawnAmount)), MessageBundle.value("monster", this.miniMessage.stripTags(definition.displayName())), MessageBundle.value("level", Integer.toString(spawnLevel)));
                        if (sender instanceof Player player) {
                           this.reply(player, confirmation);
                        } else {
                           this.scheduler.executeGlobal(() -> sender.sendMessage(confirmation));
                        }

                     });
                     return true;
                  } else {
                     sender.sendMessage(this.messages.message("monster-invalid-amount"));
                     return true;
                  }
               } else {
                  sender.sendMessage(this.messages.message("monster-invalid-level", MessageBundle.value("minimum", Integer.toString(definition.minimumLevel())), MessageBundle.value("maximum", Integer.toString(definition.maximumLevel()))));
                  return true;
               }
            } else {
               sender.sendMessage(this.messages.message("monster-invalid-location"));
               return true;
            }
         }
      }
   }

   private void spawnRing(Location origin, MonsterDefinition definition, int level, int amount, double radius) {
      for(int index = 0; index < amount; ++index) {
         double angle = (Math.PI * 2D) * (double)index / (double)amount;
         Location location = origin.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
         this.monsterRuntimeService.spawn(location, definition, level);
      }

   }

   private Integer parseInteger(String[] args, int index, int fallback) {
      if (args.length <= index) {
         return fallback;
      } else {
         try {
            return Integer.parseInt(args[index]);
         } catch (NumberFormatException var5) {
            return null;
         }
      }
   }

   private Double parseDouble(String value) {
      try {
         double parsed = Double.parseDouble(value);
         return Double.isFinite(parsed) ? parsed : null;
      } catch (NumberFormatException var4) {
         return null;
      }
   }

   private List<String> filterPrefix(List<String> options, String value) {
      String prefix = value.toLowerCase(Locale.ROOT);
      return options.stream().filter((option) -> option.startsWith(prefix)).toList();
   }

   private void reply(CommandSender sender, Component message) {
      if (sender instanceof Player player) {
         this.scheduler.executeEntity(player, () -> player.sendMessage(message), () -> {
         });
      } else {
         sender.sendMessage(message);
      }

   }
}
