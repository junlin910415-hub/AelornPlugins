package tw.linsy.aelorn.rpgcore.gui;

import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.config.QuestRegistry;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestDefinition;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestObjectiveDefinition;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestProgress;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestReward;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestStage;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestStatus;
import tw.linsy.aelorn.rpgcore.hud.InternalGuiTitle;
import tw.linsy.aelorn.rpgcore.quest.QuestService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class QuestJournalMenuService {
   public static final int MENU_SIZE = 54;
   public static final int PREVIOUS_PAGE_SLOT = 45;
   public static final int NEXT_PAGE_SLOT = 53;
   private static final int[] QUEST_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
   private final CharacterService characterService;
   private final QuestRegistry registry;
   private final QuestService questService;
   private final MessageBundle messages;

   public QuestJournalMenuService(CharacterService characterService, QuestRegistry registry, QuestService questService, MessageBundle messages) {
      this.characterService = characterService;
      this.registry = registry;
      this.questService = questService;
      this.messages = messages;
   }

   public void open(Player player) {
      this.open(player, 0);
   }

   public void open(Player player, int requestedPage) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character == null) {
         player.sendMessage(this.messages.message("no-active-character"));
      } else {
         List<QuestDefinition> quests = this.sortedQuests(character);
         int pageCount = Math.max(1, (quests.size() + QUEST_SLOTS.length - 1) / QUEST_SLOTS.length);
         int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
         QuestJournalHolder holder = new QuestJournalHolder(player.getUniqueId(), character.id(), page);
         Inventory inventory = Bukkit.createInventory(holder, 54, InternalGuiTitle.questJournal());
         holder.inventory(inventory);
         this.renderHeader(inventory, character);
         this.renderPagination(inventory, page, pageCount);
         int firstQuest = page * QUEST_SLOTS.length;

         for(int index = 0; index < QUEST_SLOTS.length && firstQuest + index < quests.size(); ++index) {
            inventory.setItem(QUEST_SLOTS[index], this.questItem(character, (QuestDefinition)quests.get(firstQuest + index)));
         }

         player.openInventory(inventory);
      }
   }

   public QuestDefinition questAt(CharacterProfile character, int page, int slot) {
      List<QuestDefinition> quests = this.sortedQuests(character);
      int firstQuest = Math.max(0, page) * QUEST_SLOTS.length;

      for(int index = 0; index < QUEST_SLOTS.length && firstQuest + index < quests.size(); ++index) {
         if (QUEST_SLOTS[index] == slot) {
            return (QuestDefinition)quests.get(firstQuest + index);
         }
      }

      return null;
   }

   private List<QuestDefinition> sortedQuests(CharacterProfile character) {
      return this.registry.all().stream().sorted(Comparator.comparing((QuestDefinition quest) -> this.statusOrder(this.questService.availability(character, quest))).thenComparingInt(QuestDefinition::minimumLevel).thenComparing(QuestDefinition::id)).toList();
   }

   private void renderPagination(Inventory inventory, int page, int pageCount) {
      if (page > 0) {
         inventory.setItem(45, this.item(Material.ARROW, "<yellow>上一頁</yellow>", List.of("<gray>返回第 " + page + " 頁</gray>"), false));
      }

      inventory.setItem(49, this.item(Material.MAP, "<gold>第 " + (page + 1) + " / " + pageCount + " 頁</gold>", List.of("<gray>任務會依狀態與建議等級排列</gray>"), false));
      if (page + 1 < pageCount) {
         inventory.setItem(53, this.item(Material.ARROW, "<yellow>下一頁</yellow>", List.of("<gray>前往第 " + (page + 2) + " 頁</gray>"), false));
      }

   }

   private void renderHeader(Inventory inventory, CharacterProfile character) {
      long active = character.questProgress().values().stream().filter((progress) -> progress.status() == QuestStatus.ACTIVE).count();
      long completed = character.questProgress().values().stream().filter((progress) -> progress.status() == QuestStatus.COMPLETED).count();
      String tracked = (String)this.registry.find(character.trackedQuestId()).map(QuestDefinition::displayName).orElse("<gray>未追蹤</gray>");
      inventory.setItem(4, this.item(Material.WRITABLE_BOOK, "<gold>冒險進度</gold>", List.of("<gray>角色等級：</gray><white>" + character.level() + "</white>", "<gray>進行中：</gray><white>" + active + "</white>", "<gray>已完成：</gray><white>" + completed + "</white>", "", "<gray>目前追蹤：</gray>" + tracked), false));
   }

   private ItemStack questItem(CharacterProfile character, QuestDefinition quest) {
      QuestService.Availability availability = this.questService.availability(character, quest);
      Material icon = Material.matchMaterial(quest.iconMaterial());
      List<String> lore = new ArrayList();
      lore.add(this.categoryName(quest));
      lore.add("<gray>建議等級：</gray><white>" + quest.minimumLevel() + "+</white>");
      lore.add("");
      lore.add("<gray>" + quest.description() + "</gray>");
      lore.add("");

      // 多階段任務只攤開「目前這一步」,後面的步驟保持未知——這是劇情感的來源
      QuestProgress progress = character.questProgress().get(quest.id());
      QuestStage stage = progress == null ? quest.stage(0) : quest.stageFor(progress);
      if (quest.multiStage() && stage != null) {
         int stageNumber = (progress == null ? 0 : progress.stageIndex()) + 1;
         lore.add("<yellow>步驟 " + Math.min(stageNumber, quest.stageCount()) + "/" + quest.stageCount() + "</yellow><gray> · </gray><white>" + stage.description() + "</white>");
         lore.add("");
      }

      List<QuestObjectiveDefinition> visible = stage == null ? quest.objectives() : stage.objectives();
      for(QuestObjectiveDefinition objective : visible) {
         int current = this.questService.objectiveProgress(character, quest, objective);
         String color = current >= objective.requiredAmount() ? "<green>" : "<white>";
         String closing = current >= objective.requiredAmount() ? "</green>" : "</white>";
         lore.add(color + current + "/" + objective.requiredAmount() + closing + "<gray> · " + objective.description() + "</gray>");
      }

      lore.add("");
      lore.add("<gray>完成經驗：</gray><gold>" + quest.reward().experience() + "</gold>");
      if (quest.reward().hasItems()) {
         lore.add("<gray>物品獎勵：</gray>");
         for(QuestReward.ItemReward item : quest.reward().items()) {
            String label = item.displayName() == null || item.displayName().isBlank()
               ? item.material().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
               : item.displayName();
            lore.add("<dark_gray>· </dark_gray><white>" + label + "</white><gray> ×" + item.amount() + "</gray>");
         }
      }
      if (!quest.prerequisites().isEmpty()) {
         lore.add("<gray>前置內容：</gray>");

         for(String prerequisite : quest.prerequisites()) {
            String name = (String)this.registry.find(prerequisite).map(QuestDefinition::displayName).orElse(prerequisite);
            lore.add(" <dark_gray>•</dark_gray> " + name);
         }
      }

      lore.add("");
      switch (availability) {
         case AVAILABLE -> lore.add("<yellow>左鍵接受並追蹤</yellow>");
         case ACTIVE -> lore.add(character.trackedQuestId().equals(quest.id()) ? "<aqua>目前追蹤中 · 左鍵取消追蹤</aqua>" : "<yellow>左鍵追蹤</yellow>");
         case COMPLETED -> lore.add("<green>已完成</green>");
         case LOCKED -> lore.add(character.level() < quest.minimumLevel() ? "<red>角色等級不足</red>" : "<red>尚未完成前置內容</red>");
      }

      return this.item(icon == null ? Material.BOOK : icon, quest.displayName(), lore, availability == QuestService.Availability.ACTIVE);
   }

   private int statusOrder(QuestService.Availability availability) {
      byte var10000;
      switch (availability) {
         case AVAILABLE -> var10000 = 1;
         case ACTIVE -> var10000 = 0;
         case COMPLETED -> var10000 = 3;
         case LOCKED -> var10000 = 2;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private String categoryName(QuestDefinition quest) {
      String var10000;
      switch (quest.category()) {
         case STORY -> var10000 = "<gold>故事任務</gold>";
         case SIDE -> var10000 = "<green>支線任務</green>";
         case DUNGEON -> var10000 = "<red>遭遇任務</red>";
         case EXPLORATION -> var10000 = "<aqua>探索任務</aqua>";
         case CHALLENGE -> var10000 = "<light_purple>挑戰任務</light_purple>";
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private ItemStack item(Material material, String name, List<String> loreLines, boolean glint) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.messages.text(name));
      var var10000 = loreLines.stream();
      MessageBundle var10001 = this.messages;
      Objects.requireNonNull(var10001);
      List<Component> lore = var10000.map((x$0) -> var10001.text(x$0)).toList();
      meta.lore(lore);
      meta.setEnchantmentGlintOverride(glint);
      item.setItemMeta(meta);
      return item;
   }
}
