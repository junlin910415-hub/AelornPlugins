package tw.linsy.aelorn.rpgcore.gui;

import tw.linsy.aelorn.rpgcore.ability.AbilityTreeService;
import tw.linsy.aelorn.rpgcore.combat.StatService;
import tw.linsy.aelorn.rpgcore.config.ClassRegistry;
import tw.linsy.aelorn.rpgcore.config.DiscoveryRegistry;
import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.config.QuestRegistry;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.classes.CharacterClassDefinition;
import tw.linsy.aelorn.rpgcore.domain.combat.CombatStats;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestStatus;
import tw.linsy.aelorn.rpgcore.domain.stats.PrimarySkill;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentBonuses;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentService;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentStatType;
import tw.linsy.aelorn.rpgcore.hud.InternalGuiTitle;
import tw.linsy.aelorn.rpgcore.party.PartyService;
import tw.linsy.aelorn.rpgcore.party.PartySnapshot;
import tw.linsy.aelorn.rpgcore.progression.PrimarySkillService;
import tw.linsy.aelorn.rpgcore.progression.ProfessionService;
import tw.linsy.aelorn.rpgcore.progression.ProgressionResult;
import tw.linsy.aelorn.rpgcore.progression.ProgressionService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class CharacterProfileMenuService {
   public static final int MENU_SIZE = 54;
   public static final int IDENTITY_SLOT = 4;
   public static final int PROGRESSION_SLOT = 10;
   public static final int COMBAT_SLOT = 12;
   public static final int RESOURCE_SLOT = 14;
   public static final int EQUIPMENT_SLOT = 16;
   public static final int QUEST_SLOT = 20;
   public static final int ABILITY_SLOT = 22;
   public static final int ATTRIBUTE_SLOT = 24;
   public static final int PROFESSION_SLOT = 26;
   public static final int DISCOVERY_SLOT = 30;
   public static final int PARTY_SLOT = 32;
   public static final int RECORD_SLOT = 34;
   public static final int REFRESH_SLOT = 45;
   public static final int BACK_SLOT = 49;
   public static final int CHARACTER_SELECT_SLOT = 53;
   private static final int PROGRESS_SEGMENTS = 18;
   private static final DateTimeFormatter DATE;
   private final CharacterService characterService;
   private final ClassRegistry classRegistry;
   private final QuestRegistry questRegistry;
   private final DiscoveryRegistry discoveryRegistry;
   private final PrimarySkillService primarySkillService;
   private final AbilityTreeService abilityTreeService;
   private final ProfessionService professionService;
   private final ProgressionService progressionService;
   private final EquipmentService equipmentService;
   private final StatService statService;
   private final PartyService partyService;
   private final MessageBundle messages;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();

   public CharacterProfileMenuService(CharacterService characterService, ClassRegistry classRegistry, QuestRegistry questRegistry, DiscoveryRegistry discoveryRegistry, PrimarySkillService primarySkillService, AbilityTreeService abilityTreeService, ProfessionService professionService, ProgressionService progressionService, EquipmentService equipmentService, StatService statService, PartyService partyService, MessageBundle messages) {
      this.characterService = characterService;
      this.classRegistry = classRegistry;
      this.questRegistry = questRegistry;
      this.discoveryRegistry = discoveryRegistry;
      this.primarySkillService = primarySkillService;
      this.abilityTreeService = abilityTreeService;
      this.professionService = professionService;
      this.progressionService = progressionService;
      this.equipmentService = equipmentService;
      this.statService = statService;
      this.partyService = partyService;
      this.messages = messages;
   }

   public void open(Player player) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character == null) {
         player.sendMessage(this.messages.message("no-active-character"));
      } else {
         CharacterProfileMenuHolder holder = new CharacterProfileMenuHolder(player.getUniqueId(), character.id());
         Inventory inventory = Bukkit.createInventory(holder, 54, InternalGuiTitle.profile());
         holder.inventory(inventory);
         EquipmentBonuses bonuses = this.equipmentService.bonuses(player, character);
         CombatStats stats = this.statService.calculate(character, bonuses);
         ProgressionResult progression = this.progressionService.describe(character);
         CharacterClassDefinition definition = (CharacterClassDefinition)this.classRegistry.find(character.classId()).orElse(null);
         inventory.setItem(4, this.identityItem(player, character, definition));
         inventory.setItem(10, this.progressionItem(character, progression));
         inventory.setItem(12, this.combatItem(stats));
         inventory.setItem(14, this.resourceItem(stats));
         inventory.setItem(16, this.equipmentItem(bonuses));
         inventory.setItem(20, this.questItem(character));
         inventory.setItem(22, this.abilityItem(character));
         inventory.setItem(24, this.attributeItem(character, stats));
         inventory.setItem(26, this.professionItem(character));
         inventory.setItem(30, this.discoveryItem(character));
         inventory.setItem(32, this.partyItem(player));
         inventory.setItem(34, this.recordItem(character));
         inventory.setItem(45, this.item(Material.LIME_DYE, "<green><bold>重新整理</bold></green>", List.of("<gray>重新計算目前裝備與角色數值。</gray>", "", "<green>左鍵重新整理</green>")));
         inventory.setItem(49, this.item(Material.ARROW, "<yellow><bold>返回角色總覽</bold></yellow>", List.of("<gray>回到主要 RPG 功能選單。</gray>", "", "<green>左鍵返回</green>")));
         inventory.setItem(53, this.item(Material.RECOVERY_COMPASS, "<aqua><bold>切換角色</bold></aqua>", List.of("<gray>返回角色欄位選單。</gray>", "", "<green>左鍵開啟</green>")));
         player.openInventory(inventory);
      }
   }

   private ItemStack identityItem(Player player, CharacterProfile character, CharacterClassDefinition definition) {
      String className = definition == null ? character.classId() : this.miniMessage.stripTags(definition.displayName());
      String role = definition == null ? "冒險者" : this.miniMessage.stripTags(definition.role());
      ItemStack item = this.item(Material.PLAYER_HEAD, "<aqua><bold>" + player.getName() + "</bold></aqua>", List.of("<gray>角色：</gray><white>" + character.name() + "</white>", "<gray>欄位：</gray><white>" + (character.slot() + 1) + "</white>", "", "<gray>職業：</gray><gold>" + className + "</gold>", "<gray>定位：</gray><white>" + role + "</white>", "<gray>戰鬥等級：</gray><green>" + character.level() + "</green>"));
      ItemMeta var8 = item.getItemMeta();
      if (var8 instanceof SkullMeta skullMeta) {
         skullMeta.setOwningPlayer(player);
         item.setItemMeta(skullMeta);
      }

      return item;
   }

   private ItemStack progressionItem(CharacterProfile character, ProgressionResult progression) {
      double ratio = experienceRatio(progression);
      String current = progression.requiredLevelExperience() <= 0L ? "MAX" : compact(progression.currentLevelExperience());
      String required = progression.requiredLevelExperience() <= 0L ? "MAX" : compact(progression.requiredLevelExperience());
      Material var10001 = Material.EXPERIENCE_BOTTLE;
      String var10003 = "<gray>戰鬥等級：</gray><white>" + character.level() + "</white>";
      String var10004 = "<gray>目前經驗：</gray><white>" + current + "</white><dark_gray>/</dark_gray><white>" + required + "</white>";
      String var10005 = progressBar(ratio);
      String var10006 = "<gray>等級進度：</gray><green>" + percent(ratio) + "%</green>";
      String var10007 = "<gray>累積經驗：</gray><white>" + compact(progression.totalExperience()) + "</white>";
      String var10009 = "<gray>可用技能點：</gray><green>" + this.primarySkillService.availablePoints(character) + "</green>";
      int var10010 = this.abilityTreeService.availablePoints(character);
      return this.item(var10001, "<green><bold>角色成長</bold></green>", List.of(var10003, var10004, var10005, var10006, var10007, "", var10009, "<gray>可用能力點：</gray><aqua>" + var10010 + "</aqua>"));
   }

   private ItemStack combatItem(CombatStats stats) {
      double mitigation = Math.max((double)0.0F, (double)1.0F - stats.damageTakenMultiplier());
      return this.item(Material.IRON_SWORD, "<red><bold>戰鬥能力</bold></red>", List.of("<gray>攻擊力：</gray><white>" + number(stats.attackPower()) + "</white>", "<gray>防禦：</gray><white>" + number(stats.defense()) + "</white>", "<gray>抗性：</gray><white>" + number(stats.resistance()) + "</white>", "<gray>移動速度：</gray><white>" + number(stats.speed()) + "%</white>", "", "<gray>減傷：</gray><green>" + percent(mitigation) + "%</green>", "<gray>暴擊率：</gray><yellow>" + percent(stats.criticalChance()) + "%</yellow>", "<gray>閃避率：</gray><aqua>" + percent(stats.dodgeChance()) + "%</aqua>", "<gray>普攻倍率：</gray><white>x" + number(stats.basicAttackMultiplier()) + "</white>"));
   }

   private ItemStack resourceItem(CombatStats stats) {
      return this.item(Material.HEART_OF_THE_SEA, "<aqua><bold>生命與魔力</bold></aqua>", List.of("<gray>生命上限：</gray><red>" + number(stats.maximumHealth()) + "</red>", "<gray>生命回復：</gray><red>" + number(stats.healthRegeneration()) + "/秒</red>", "", "<gray>魔力上限：</gray><aqua>" + number(stats.maximumMana()) + "</aqua>", "<gray>魔力回復：</gray><aqua>" + number(stats.manaRegeneration()) + "/秒</aqua>", "<gray>技能消耗降低：</gray><green>" + percent(stats.spellCostReduction()) + "%</green>"));
   }

   private ItemStack attributeItem(CharacterProfile character, CombatStats stats) {
      return this.item(Material.AMETHYST_SHARD, "<light_purple><bold>主屬性</bold></light_purple>", List.of(this.attributeLine(PrimarySkill.STRENGTH, stats.strengthPoints()), this.attributeLine(PrimarySkill.DEXTERITY, stats.dexterityPoints()), this.attributeLine(PrimarySkill.INTELLIGENCE, stats.intelligencePoints()), this.attributeLine(PrimarySkill.DEFENCE, stats.defencePoints()), this.attributeLine(PrimarySkill.AGILITY, stats.agilityPoints()), "", "<gray>已投入：</gray><white>" + this.primarySkillService.spentPoints(character) + "/" + this.primarySkillService.earnedPoints(character) + "</white>", "<green>左鍵開啟技能水晶</green>"));
   }

   private ItemStack equipmentItem(EquipmentBonuses bonuses) {
      List<String> lore = new ArrayList();
      lore.add("<gray>目前穿戴裝備提供的鑑定加成。</gray>");
      lore.add("");
      int visible = 0;
      int total = 0;

      for(EquipmentStatType type : EquipmentStatType.values()) {
         int value = bonuses.value(type);
         if (value != 0) {
            ++total;
            if (visible < 9) {
               String color = value > 0 ? "<green>" : "<red>";
               String close = value > 0 ? "</green>" : "</red>";
               String var10001 = type.displayName();
               lore.add("<dark_gray>• </dark_gray><gray>" + var10001 + "：</gray>" + color + (value > 0 ? "+" : "") + value + type.suffix() + close);
               ++visible;
            }
         }
      }

      if (total == 0) {
         lore.add("<dark_gray>目前沒有裝備鑑定加成。</dark_gray>");
      } else if (total > visible) {
         lore.add("<dark_gray>另有 " + (total - visible) + " 項次要加成。</dark_gray>");
      }

      return this.item(Material.NETHERITE_CHESTPLATE, "<gold><bold>裝備鑑定</bold></gold>", lore);
   }

   private ItemStack questItem(CharacterProfile character) {
      long active = character.questProgress().values().stream().filter((progress) -> progress.status() == QuestStatus.ACTIVE).count();
      long completed = character.questProgress().values().stream().filter((progress) -> progress.status() == QuestStatus.COMPLETED).count();
      String tracked = character.trackedQuestId().isBlank() ? "未追蹤" : (String)this.questRegistry.find(character.trackedQuestId()).map((quest) -> this.miniMessage.stripTags(quest.displayName())).orElse(character.trackedQuestId());
      return this.item(Material.WRITABLE_BOOK, "<gold><bold>任務進度</bold></gold>", List.of("<gray>進行中：</gray><yellow>" + active + "</yellow>", "<gray>已完成：</gray><green>" + completed + "/" + this.questRegistry.size() + "</green>", "<gray>目前追蹤：</gray><white>" + tracked + "</white>", "", "<green>左鍵開啟任務日誌</green>"));
   }

   private ItemStack abilityItem(CharacterProfile character) {
      int earned = this.abilityTreeService.earnedPoints(character);
      int available = this.abilityTreeService.availablePoints(character);
      return this.item(Material.ENCHANTED_BOOK, "<aqua><bold>能力配置</bold></aqua>", List.of("<gray>已解鎖節點：</gray><white>" + character.unlockedAbilityNodes().size() + "</white>", "<gray>已使用能力點：</gray><white>" + (earned - available) + "/" + earned + "</white>", "<gray>可用能力點：</gray><aqua>" + available + "</aqua>", "", "<green>左鍵開啟能力樹</green>"));
   }

   private ItemStack professionItem(CharacterProfile character) {
      return this.item(Material.DIAMOND_PICKAXE, "<green><bold>生活技能</bold></green>", List.of("<gray>生活技能總等級：</gray><white>" + this.professionService.totalProfessionLevel(character) + "</white>", "<gray>採集、製作與專業進度集中管理。</gray>", "", "<green>左鍵開啟生活技能</green>"));
   }

   private ItemStack discoveryItem(CharacterProfile character) {
      return this.item(Material.COMPASS, "<light_purple><bold>世界探索</bold></light_purple>", List.of("<gray>已發現地點：</gray><white>" + character.discoveredLocations().size() + "/" + this.discoveryRegistry.all().size() + "</white>", "<gray>內容書會整理地域、地標與秘密。</gray>", "", "<green>左鍵開啟內容書</green>"));
   }

   private ItemStack partyItem(Player player) {
      PartySnapshot party = (PartySnapshot)this.partyService.partyOf(player.getUniqueId()).orElse(null);
      if (party == null) {
         return this.item(Material.PLAYER_HEAD, "<aqua><bold>冒險隊伍</bold></aqua>", List.of("<gray>目前沒有隊伍。</gray>", "<gray>可建立隊伍、處理邀請或尋找成員。</gray>", "", "<green>左鍵開啟隊伍</green>"));
      } else {
         Material var10001 = Material.PLAYER_HEAD;
         int var10003 = party.members().size();
         String var3 = "<gray>成員：</gray><white>" + var10003 + "/" + this.partyService.maximumMembers() + "</white>";
         boolean var10004 = party.isLeader(player.getUniqueId());
         return this.item(var10001, "<aqua><bold>冒險隊伍</bold></aqua>", List.of(var3, "<gray>身分：</gray>" + (var10004 ? "<gold>隊長</gold>" : "<white>隊員</white>"), "<gray>公開招募：</gray>" + (party.listed() ? "<green>開啟</green>" : "<dark_gray>關閉</dark_gray>"), "", "<green>左鍵開啟隊伍</green>"));
      }
   }

   private ItemStack recordItem(CharacterProfile character) {
      Material var10001 = Material.CLOCK;
      String var10003 = "<gray>遊玩時間：</gray><white>" + formatPlayTime(character.playTimeSeconds()) + "</white>";
      DateTimeFormatter var10004 = DATE;
      String var2 = "<gray>建立日期：</gray><white>" + var10004.format(character.createdAt()) + "</white>";
      DateTimeFormatter var10005 = DATE;
      return this.item(var10001, "<yellow><bold>角色紀錄</bold></yellow>", List.of(var10003, var2, "<gray>最後遊玩：</gray><white>" + var10005.format(character.lastPlayedAt()) + "</white>", "", "<dark_gray>角色 ID：</dark_gray>", "<dark_gray>" + String.valueOf(character.id()) + "</dark_gray>"));
   }

   private String attributeLine(PrimarySkill skill, int points) {
      String var10000 = skill.coloredName();
      return "<dark_gray>• </dark_gray>" + var10000 + "<gray>：</gray><white>" + points + "</white>";
   }

   private ItemStack item(Material material, String name, List<String> loreLines) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.messages.text(name).decoration(TextDecoration.ITALIC, false));
      var var10000 = loreLines.stream();
      MessageBundle var10001 = this.messages;
      Objects.requireNonNull(var10001);
      List<Component> lore = var10000.map((x$0) -> var10001.text(x$0)).map((line) -> line.decoration(TextDecoration.ITALIC, false)).toList();
      meta.lore(lore);
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE});
      item.setItemMeta(meta);
      return item;
   }

   static String progressBar(double ratio) {
      double clamped = Math.max((double)0.0F, Math.min((double)1.0F, ratio));
      int filled = (int)Math.round(clamped * (double)18.0F);
      String var10000 = "■".repeat(filled);
      return "<green>" + var10000 + "</green><dark_gray>" + "■".repeat(18 - filled) + "</dark_gray>";
   }

   static String formatPlayTime(long seconds) {
      long safe = Math.max(0L, seconds);
      long days = safe / 86400L;
      long hours = safe % 86400L / 3600L;
      long minutes = safe % 3600L / 60L;
      if (days > 0L) {
         return days + " 天 " + hours + " 小時";
      } else {
         return hours > 0L ? hours + " 小時 " + minutes + " 分" : minutes + " 分";
      }
   }

   private static double experienceRatio(ProgressionResult progression) {
      return progression.requiredLevelExperience() <= 0L ? (double)1.0F : Math.max((double)0.0F, Math.min((double)1.0F, (double)progression.currentLevelExperience() / (double)progression.requiredLevelExperience()));
   }

   private static String compact(long value) {
      if (value < 1000L) {
         return Long.toString(value);
      } else {
         String suffix;
         double divisor;
         if (value >= 1000000000L) {
            suffix = "B";
            divisor = (double)1.0E9F;
         } else if (value >= 1000000L) {
            suffix = "M";
            divisor = (double)1000000.0F;
         } else {
            suffix = "K";
            divisor = (double)1000.0F;
         }

         String rendered = String.format(Locale.ROOT, "%.1f%s", (double)value / divisor, suffix);
         return rendered.replace(".0" + suffix, suffix);
      }
   }

   private static String number(double value) {
      return Math.abs(value - Math.rint(value)) < 0.05 ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
   }

   private static String percent(double ratio) {
      return Long.toString(Math.round(Math.max((double)0.0F, ratio) * (double)100.0F));
   }

   static {
      DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT).withZone(ZoneId.systemDefault());
   }
}
