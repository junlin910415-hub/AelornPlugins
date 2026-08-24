package tw.linsy.aelorn.rpgcore.gui;

import tw.linsy.aelorn.rpgcore.ability.AbilityTreeService;
import tw.linsy.aelorn.rpgcore.config.ClassRegistry;
import tw.linsy.aelorn.rpgcore.config.DiscoveryRegistry;
import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.config.QuestRegistry;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.classes.CharacterClassDefinition;
import tw.linsy.aelorn.rpgcore.domain.discovery.DiscoveryCategory;
import tw.linsy.aelorn.rpgcore.domain.discovery.DiscoveryDefinition;
import tw.linsy.aelorn.rpgcore.domain.profession.ProfessionCategory;
import tw.linsy.aelorn.rpgcore.domain.profession.ProfessionProgress;
import tw.linsy.aelorn.rpgcore.domain.profession.ProfessionType;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestStatus;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentService;
import tw.linsy.aelorn.rpgcore.hud.InternalGuiTitle;
import tw.linsy.aelorn.rpgcore.integration.nexo.CustomItemProvider;
import tw.linsy.aelorn.rpgcore.party.PartyService;
import tw.linsy.aelorn.rpgcore.party.PartySnapshot;
import tw.linsy.aelorn.rpgcore.progression.PrimarySkillService;
import tw.linsy.aelorn.rpgcore.progression.ProfessionService;
import tw.linsy.aelorn.rpgcore.progression.ProgressionResult;
import tw.linsy.aelorn.rpgcore.progression.ProgressionService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

public final class MainMenuService {
   public static final int MENU_SIZE = 54;
   public static final int PROFILE_SLOT = 4;
   public static final int CONTENT_BOOK_SLOT = 20;
   public static final int SKILL_CRYSTAL_SLOT = 22;
   public static final int ABILITY_TREE_SLOT = 24;
   public static final int PROFESSION_SLOT = 31;
   public static final int QUEST_JOURNAL_SLOT = 38;
   public static final int PARTY_SLOT = 40;
   public static final int TRAINING_WEAPON_SLOT = 42;
   public static final int CHARACTER_SELECT_SLOT = 49;
   private static final String PROFILE_ITEM_ID = "rpgcore_character_profile";
   private static final String SKILL_CRYSTAL_ITEM_ID = "rpgcore_skill_crystal";
   private static final String ABILITY_TREE_ITEM_ID = "rpgcore_ability_tree";
   private static final String PROFESSION_ITEM_ID = "rpgcore_profession_tome";
   private final CharacterService characterService;
   private final ClassRegistry classRegistry;
   private final QuestRegistry questRegistry;
   private final DiscoveryRegistry discoveryRegistry;
   private final PrimarySkillService primarySkillService;
   private final AbilityTreeService abilityTreeService;
   private final ProfessionService professionService;
   private final ProgressionService progressionService;
   private final EquipmentService equipmentService;
   private final PartyService partyService;
   private final CustomItemProvider customItems;
   private final MessageBundle messages;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();

   public MainMenuService(CharacterService characterService, ClassRegistry classRegistry, QuestRegistry questRegistry, DiscoveryRegistry discoveryRegistry, PrimarySkillService primarySkillService, AbilityTreeService abilityTreeService, ProfessionService professionService, ProgressionService progressionService, EquipmentService equipmentService, PartyService partyService, CustomItemProvider customItems, MessageBundle messages) {
      this.characterService = characterService;
      this.classRegistry = classRegistry;
      this.questRegistry = questRegistry;
      this.discoveryRegistry = discoveryRegistry;
      this.primarySkillService = primarySkillService;
      this.abilityTreeService = abilityTreeService;
      this.professionService = professionService;
      this.progressionService = progressionService;
      this.equipmentService = equipmentService;
      this.partyService = partyService;
      this.customItems = customItems;
      this.messages = messages;
   }

   public void open(Player player) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character == null) {
         player.sendMessage(this.messages.message("no-active-character"));
      } else {
         MainMenuHolder holder = new MainMenuHolder(player.getUniqueId(), character.id());
         Inventory inventory = Bukkit.createInventory(holder, 54, InternalGuiTitle.mainMenu());
         holder.inventory(inventory);
         inventory.setItem(4, this.profileItem(player, character));
         inventory.setItem(20, this.contentBookItem(character));
         inventory.setItem(22, this.skillCrystalItem(character));
         inventory.setItem(24, this.abilityTreeItem(character));
         inventory.setItem(31, this.professionItem(character));
         inventory.setItem(38, this.item(Material.WRITABLE_BOOK, "", "<gold>任務日誌</gold>", List.of("<gray>查看已接任務與進行中的目標。</gray>", "", "<green>左鍵開啟</green>"), false));
         inventory.setItem(40, this.partyItem(player.getUniqueId()));
         inventory.setItem(42, this.item(Material.IRON_SWORD, "", "<yellow>補發職業武器</yellow>", List.of("<gray>遺失訓練武器時使用。</gray>", "<dark_gray>會依照目前職業補發。</dark_gray>", "", "<green>左鍵補發</green>"), false));
         inventory.setItem(49, this.item(Material.PLAYER_HEAD, "", "<aqua>切換角色</aqua>", List.of("<gray>返回角色欄位選單。</gray>", "", "<green>左鍵開啟</green>"), false));
         player.openInventory(inventory);
      }
   }

   private ItemStack partyItem(UUID playerId) {
      PartySnapshot party = (PartySnapshot)this.partyService.partyOf(playerId).orElse(null);
      List<String> lore = new ArrayList();
      if (party == null) {
         lore.add("<gray>建立隊伍、查看邀請或尋找公開隊伍。</gray>");
         int invites = this.partyService.pendingInvites(playerId, System.currentTimeMillis()).size();
         lore.add("<gray>待處理邀請：</gray><white>" + invites + "</white>");
      } else {
         int var10001 = party.members().size();
         lore.add("<gray>目前成員：</gray><white>" + var10001 + "/" + this.partyService.maximumMembers() + "</white>");
         boolean leader = party.isLeader(playerId);
         lore.add("<gray>身分：</gray>" + (leader ? "<gold>隊長</gold>" : "<white>隊員</white>"));
         lore.add("<gray>公開招募：</gray>" + (party.listed() ? "<green>開啟</green>" : "<dark_gray>關閉</dark_gray>"));
      }

      lore.add("");
      lore.add("<green>左鍵開啟</green>");
      return this.item(Material.PLAYER_HEAD, "", "<aqua><bold>冒險隊伍</bold></aqua>", lore, false);
   }

   public ItemStack contentBookItem(CharacterProfile character) {
      return this.item(Material.KNOWLEDGE_BOOK, "rpgcore_wayfinder_codex", "<light_purple><bold>內容書</bold></light_purple>", this.contentBookLore(character), false);
   }

   public ItemStack skillCrystalItem(CharacterProfile character) {
      int available = this.primarySkillService.availablePoints(character);
      return this.item(Material.EMERALD, "rpgcore_skill_crystal", "<green><bold>技能水晶</bold></green>", List.of("<gray>你有 </gray><green>" + available + "</green><gray> 點技能尚未分配。</gray>", "<dark_gray>五項主屬性會影響實際戰鬥數值。</dark_gray>", "", "<green>左鍵開啟</green>", "<yellow>Shift + 右鍵可在水晶內重置</yellow>"), available > 0);
   }

   public ItemStack abilityTreeItem(CharacterProfile character) {
      int available = this.abilityTreeService.availablePoints(character);
      int earned = this.abilityTreeService.earnedPoints(character);
      List<String> lore = new ArrayList();
      lore.add("<gray>消耗能力點解鎖新的技能分支。</gray>");
      lore.add("");
      lore.add("<aqua>可用能力點：</aqua><white>" + available + "/" + earned + "</white>");
      lore.add("");
      lore.add("<gold>下次獲得能力點：</gold>");
      List<Integer> next = this.abilityTreeService.nextAwardLevels(character, 3);
      if (next.isEmpty()) {
         lore.add("<dark_gray>- 已達目前上限</dark_gray>");
      } else {
         for(int level : next) {
            lore.add("<dark_gray>- </dark_gray><white>戰鬥 Lv. " + level + "</white>");
         }
      }

      lore.add("");
      lore.add("<green>左鍵開啟</green>");
      return this.item(Material.NETHER_STAR, "rpgcore_ability_tree", "<aqua><bold>能力樹</bold></aqua>", lore, available > 0);
   }

   public ItemStack professionItem(CharacterProfile character) {
      return this.item(Material.EXPERIENCE_BOTTLE, "rpgcore_profession_tome", "<green><bold>生活技能資訊</bold></green>", this.professionSummaryLore(character), false);
   }

   private ItemStack profileItem(Player player, CharacterProfile character) {
      var var10000 = this.classRegistry.find(character.classId()).map(CharacterClassDefinition::displayName);
      MiniMessage var10001 = this.miniMessage;
      Objects.requireNonNull(var10001);
      String className = (String)var10000.map(var10001::stripTags).orElse(character.classId());
      ProgressionResult progress = this.progressionService.describe(character);
      int totalLevel = character.level() + this.professionService.totalProfessionLevel(character);
      long completedQuests = character.questProgress().values().stream().filter((quest) -> quest.status() == QuestStatus.COMPLETED).count();
      List<String> lore = new ArrayList();
      lore.add("<white>" + player.getName() + "</white>");
      lore.add("");
      lore.add("<gray>總等級：</gray><white>" + totalLevel + "</white>");
      lore.add("<gray>戰鬥等級：</gray><white>" + character.level() + "</white>");
      lore.add("<gray>職業：</gray><white>" + className + "</white>");
      lore.add("<gray>任務：</gray><white>" + completedQuests + "/" + this.questRegistry.size() + "</white>");
      String var9 = this.progressPercent(progress);
      lore.add("<gray>經驗：</gray><white>" + var9 + "%</white>");
      lore.add("");
      lore.add("<gray>生命、魔力、戰鬥、裝備與成長資料</gray>");
      lore.add("<gray>已整理至獨立角色檔案面板。</gray>");
      lore.add("");
      lore.add("<green>左鍵開啟完整檔案</green>");
      return this.item(Material.PLAYER_HEAD, "rpgcore_character_profile", "<aqua><bold>角色檔案</bold></aqua>", lore, false);
   }

   private List<String> contentBookLore(CharacterProfile character) {
      long completedQuests = character.questProgress().values().stream().filter((progress) -> progress.status() == QuestStatus.COMPLETED).count();
      long activeQuests = character.questProgress().values().stream().filter((progress) -> progress.status() == QuestStatus.ACTIVE).count();
      List<String> lore = new ArrayList();
      lore.add("<gray>右鍵或左鍵查看世界內容。</gray>");
      lore.add("");
      lore.add("<light_purple>任務：</light_purple><white>" + completedQuests + "/" + this.questRegistry.size() + " [" + activeQuests + "]</white>");
      lore.add("");
      lore.add("<light_purple>探索：</light_purple>");
      lore.add(this.discoveryLine(character, DiscoveryCategory.REGION, "地域", "<white>"));
      lore.add(this.discoveryLine(character, DiscoveryCategory.LANDMARK, "世界", "<gold>"));
      lore.add(this.discoveryLine(character, DiscoveryCategory.SECRET, "秘密", "<aqua>"));
      lore.add("");
      lore.add("<green>左鍵開啟</green>");
      return lore;
   }

   private List<String> professionSummaryLore(CharacterProfile character) {
      List<String> lore = new ArrayList();
      lore.add("<gray>角色生活技能總覽。</gray>");
      lore.add("");
      lore.add("<aqua>所屬公會：</aqua>");
      lore.add("<gray>尚未加入生活公會。</gray>");
      lore.add("");

      for(ProfessionCategory category : ProfessionCategory.values()) {
         lore.add(category == ProfessionCategory.GATHERING ? "<gold>採集技能：</gold>" : "<gold>製作技能：</gold>");

         for(ProfessionType profession : ProfessionType.values()) {
            if (profession.category() == category) {
               ProfessionProgress progress = this.professionService.progress(character, profession);
               int var10001 = progress.level();
               lore.add("<dark_gray>- </dark_gray><gray>Lv. " + var10001 + " " + profession.displayName() + " [" + this.professionService.compactProgress(progress) + "]</gray>");
            }
         }

         lore.add("");
      }

      lore.add("<green>左鍵開啟詳細頁</green>");
      return lore;
   }

   private String discoveryLine(CharacterProfile character, DiscoveryCategory category, String label, String color) {
      long total = this.discoveryRegistry.all().stream().filter((discovery) -> discovery.category() == category).count();
      var var10000 = this.discoveryRegistry.all().stream().filter((discovery) -> discovery.category() == category).map(DiscoveryDefinition::id);
      var var10001 = character.discoveredLocations();
      Objects.requireNonNull(var10001);
      long discovered = var10000.filter(var10001::contains).count();
      return "<dark_gray>- </dark_gray>" + color + label + "：</" + color.substring(1) + "<white>" + discovered + "/" + total + "</white>";
   }

   private String progressPercent(ProgressionResult progress) {
      if (progress.requiredLevelExperience() <= 0L) {
         return "MAX";
      } else {
         double percent = (double)100.0F * (double)progress.currentLevelExperience() / Math.max((double)1.0F, (double)progress.requiredLevelExperience());
         return String.format(Locale.ROOT, "%.2f", percent);
      }
   }

   private ItemStack item(Material material, String customItemId, String name, List<String> loreLines, boolean glint) {
      ItemStack item = customItemId != null && !customItemId.isBlank() ? (ItemStack)this.customItems.build(customItemId).orElseGet(() -> new ItemStack(material)) : new ItemStack(material);
      item.setAmount(1);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.messages.text(name).decoration(TextDecoration.ITALIC, false));
      var var10000 = loreLines.stream();
      MessageBundle var10001 = this.messages;
      Objects.requireNonNull(var10001);
      List<Component> lore = var10000.map((x$0) -> var10001.text(x$0)).map((line) -> line.decoration(TextDecoration.ITALIC, false)).toList();
      meta.lore(lore);
      meta.setEnchantmentGlintOverride(glint);
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE});
      item.setItemMeta(meta);
      return item;
   }
}
