package com.xuzhihuanjing.rpgcore.gui;

import com.xuzhihuanjing.rpgcore.config.DiscoveryRegistry;
import com.xuzhihuanjing.rpgcore.config.InterfaceSettings;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.config.QuestRegistry;
import com.xuzhihuanjing.rpgcore.discovery.DiscoveryService;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.discovery.DiscoveryCategory;
import com.xuzhihuanjing.rpgcore.domain.discovery.DiscoveryDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestObjectiveDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestStatus;
import com.xuzhihuanjing.rpgcore.hud.InternalGuiTitle;
import com.xuzhihuanjing.rpgcore.integration.nexo.HudGlyphProvider;
import com.xuzhihuanjing.rpgcore.quest.QuestService;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ContentBookMenuService {
   public static final int MENU_SIZE = 54;
   public static final int PREVIOUS_PAGE_SLOT = 45;
   public static final int FILTER_SLOT = 47;
   public static final int PAGE_SLOT = 49;
   public static final int SORT_SLOT = 51;
   public static final int NEXT_PAGE_SLOT = 53;
   private static final int[] CONTENT_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
   private final CharacterService characterService;
   private final QuestRegistry questRegistry;
   private final QuestService questService;
   private final DiscoveryRegistry discoveryRegistry;
   private final DiscoveryService discoveryService;
   private final MessageBundle messages;
   private final HudGlyphProvider glyphs;
   private final InterfaceSettings interfaceSettings;
   private final Map<UUID, Preference> preferences = new ConcurrentHashMap();

   public ContentBookMenuService(CharacterService characterService, QuestRegistry questRegistry, QuestService questService, DiscoveryRegistry discoveryRegistry, DiscoveryService discoveryService, MessageBundle messages, HudGlyphProvider glyphs, InterfaceSettings interfaceSettings) {
      this.characterService = characterService;
      this.questRegistry = questRegistry;
      this.questService = questService;
      this.discoveryRegistry = discoveryRegistry;
      this.discoveryService = discoveryService;
      this.messages = messages;
      this.glyphs = glyphs;
      this.interfaceSettings = interfaceSettings;
   }

   public void open(Player player) {
      this.open(player, 0);
   }

   public void open(Player player, int requestedPage) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character == null) {
         player.sendMessage(this.messages.message("no-active-character"));
      } else {
         Preference preference = (Preference)this.preferences.computeIfAbsent(player.getUniqueId(), (ignored) -> ContentBookMenuService.Preference.DEFAULT);
         List<ContentBookEntry> entries = this.entries(character, preference.filter(), preference.sort());
         int pageCount = Math.max(1, (entries.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
         int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
         ContentBookHolder holder = new ContentBookHolder(player.getUniqueId(), character.id(), page, preference.filter(), preference.sort());
         Inventory inventory = Bukkit.createInventory(holder, 54, this.title(player));
         holder.inventory(inventory);
         this.renderHeader(inventory, character);
         this.renderNavigation(inventory, page, pageCount, preference);
         int first = page * CONTENT_SLOTS.length;

         for(int index = 0; index < CONTENT_SLOTS.length && first + index < entries.size(); ++index) {
            ContentBookEntry entry = (ContentBookEntry)entries.get(first + index);
            inventory.setItem(CONTENT_SLOTS[index], entry.type() == ContentBookEntry.Type.QUEST ? this.questItem(character, entry.quest()) : this.discoveryItem(character, entry.discovery()));
         }

         player.openInventory(inventory);
      }
   }

   public void cycleFilter(Player player, int direction) {
      Preference current = (Preference)this.preferences.getOrDefault(player.getUniqueId(), ContentBookMenuService.Preference.DEFAULT);
      this.preferences.put(player.getUniqueId(), new Preference(current.filter().cycle(direction), current.sort()));
      this.open(player, 0);
   }

   public void cycleSort(Player player, int direction) {
      Preference current = (Preference)this.preferences.getOrDefault(player.getUniqueId(), ContentBookMenuService.Preference.DEFAULT);
      this.preferences.put(player.getUniqueId(), new Preference(current.filter(), current.sort().cycle(direction)));
      this.open(player, 0);
   }

   public ContentBookEntry entryAt(CharacterProfile character, ContentBookHolder holder, int slot) {
      List<ContentBookEntry> entries = this.entries(character, holder.filter(), holder.sort());
      int first = holder.page() * CONTENT_SLOTS.length;

      for(int index = 0; index < CONTENT_SLOTS.length && first + index < entries.size(); ++index) {
         if (CONTENT_SLOTS[index] == slot) {
            return (ContentBookEntry)entries.get(first + index);
         }
      }

      return null;
   }

   public boolean trackDiscovery(Player player, CharacterProfile character, DiscoveryDefinition discovery) {
      DiscoveryService.Availability availability = this.discoveryService.availability(character, discovery);
      if (discovery.hiddenUntilDiscovered() && availability != DiscoveryService.Availability.DISCOVERED) {
         player.sendMessage(this.messages.message("discovery-hidden"));
         return false;
      } else if (availability == DiscoveryService.Availability.LOCKED) {
         player.sendMessage(this.messages.message("discovery-locked"));
         return false;
      } else {
         World world = Bukkit.getWorld(discovery.world());
         if (world == null) {
            player.sendMessage(this.messages.message("discovery-world-unavailable"));
            return false;
         } else {
            player.setCompassTarget(new Location(world, discovery.x(), discovery.y(), discovery.z()));
            player.sendMessage(this.messages.message("discovery-tracked", MessageBundle.value("discovery", this.plain(discovery.displayName())), MessageBundle.value("x", Integer.toString((int)Math.round(discovery.x()))), MessageBundle.value("z", Integer.toString((int)Math.round(discovery.z())))));
            return true;
         }
      }
   }

   public void clearPreferences(UUID playerId) {
      this.preferences.remove(playerId);
   }

   private Component title(Player player) {
      return InternalGuiTitle.contentBook();
   }

   private List<ContentBookEntry> entries(CharacterProfile character, ContentBookFilter filter, ContentBookSort sort) {
      Stream<ContentBookEntry> quests = this.questRegistry.all().stream().map(ContentBookEntry::quest);
      Stream<ContentBookEntry> discoveries = this.discoveryRegistry.all().stream().map(ContentBookEntry::discovery);
      Stream var10000;
      switch (filter) {
         case RECOMMENDED -> var10000 = Stream.concat(quests, discoveries);
         case QUESTS -> var10000 = quests;
         case DISCOVERIES -> var10000 = discoveries;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      Stream<ContentBookEntry> selected = var10000;
      Comparator<ContentBookEntry> var8;
      switch (sort) {
         case RECOMMENDED -> var8 = Comparator.comparingInt((ContentBookEntry entry) -> this.statusOrder(character, entry)).thenComparingInt(ContentBookEntry::minimumLevel).thenComparing(ContentBookEntry::id);
         case LEVEL_ASCENDING -> var8 = Comparator.comparingInt(ContentBookEntry::minimumLevel).thenComparing(ContentBookEntry::id);
         case LEVEL_DESCENDING -> var8 = Comparator.comparingInt(ContentBookEntry::minimumLevel).reversed().thenComparing(ContentBookEntry::id);
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      Comparator<ContentBookEntry> comparator = var8;
      return selected.sorted(comparator).toList();
   }

   private int statusOrder(CharacterProfile character, ContentBookEntry entry) {
      if (entry.type() == ContentBookEntry.Type.QUEST) {
         byte var3;
         switch (this.questService.availability(character, entry.quest())) {
            case ACTIVE -> var3 = 0;
            case AVAILABLE -> var3 = 1;
            case LOCKED -> var3 = 3;
            case COMPLETED -> var3 = 4;
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         return var3;
      } else {
         byte var10000;
         switch (this.discoveryService.availability(character, entry.discovery())) {
            case AVAILABLE -> var10000 = 2;
            case LOCKED -> var10000 = 3;
            case DISCOVERED -> var10000 = 4;
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         return var10000;
      }
   }

   private void renderHeader(Inventory inventory, CharacterProfile character) {
      long completedQuests = character.questProgress().values().stream().filter((progress) -> progress.status() == QuestStatus.COMPLETED).count();
      long activeQuests = character.questProgress().values().stream().filter((progress) -> progress.status() == QuestStatus.ACTIVE).count();
      String tracked = (String)this.questRegistry.find(character.trackedQuestId()).map(QuestDefinition::displayName).orElse("<gray>未追蹤</gray>");
      inventory.setItem(4, this.item(Material.KNOWLEDGE_BOOK, "<light_purple>內容書</light_purple>", List.of("<gray>按分類瀏覽角色可進行內容。</gray>", "", "<light_purple>任務：</light_purple><white>" + completedQuests + "/" + this.questRegistry.size() + " [" + activeQuests + "]</white>", "", "<light_purple>探索：</light_purple>", this.discoveryLine(character, DiscoveryCategory.REGION, "地域", "<white>"), this.discoveryLine(character, DiscoveryCategory.LANDMARK, "世界", "<gold>"), this.discoveryLine(character, DiscoveryCategory.SECRET, "秘密", "<aqua>"), "", "<gray>目前追蹤：</gray>" + tracked), false));
   }

   private String discoveryLine(CharacterProfile character, DiscoveryCategory category, String label, String color) {
      long total = this.discoveryRegistry.all().stream().filter((discovery) -> discovery.category() == category).count();
      var var10000 = this.discoveryRegistry.all().stream().filter((discovery) -> discovery.category() == category).map(DiscoveryDefinition::id);
      var var10001 = character.discoveredLocations();
      Objects.requireNonNull(var10001);
      long discovered = var10000.filter(var10001::contains).count();
      return "<dark_gray>- </dark_gray>" + color + label + "：</" + color.substring(1) + "<white>" + discovered + "/" + total + "</white>";
   }

   private void renderNavigation(Inventory inventory, int page, int pageCount, Preference preference) {
      if (page > 0) {
         inventory.setItem(45, this.item(Material.ARROW, "<yellow>上一頁</yellow>", List.of("<gray>返回第 " + page + " 頁</gray>"), false));
      }

      inventory.setItem(47, this.item(Material.COMPASS, "<aqua>內容分類</aqua>", List.of("<gray>目前：</gray><white>" + preference.filter().displayName() + "</white>", "", "<yellow>左鍵下一項 · 右鍵上一項</yellow>"), false));
      inventory.setItem(49, this.item(Material.FILLED_MAP, "<gold>第 " + (page + 1) + " / " + pageCount + " 頁</gold>", List.of("<gray>共用索引會記住你的分類與排序</gray>"), false));
      inventory.setItem(51, this.item(Material.HOPPER, "<aqua>排列方式</aqua>", List.of("<gray>目前：</gray><white>" + preference.sort().displayName() + "</white>", "", "<yellow>左鍵下一項 · 右鍵上一項</yellow>"), false));
      if (page + 1 < pageCount) {
         inventory.setItem(53, this.item(Material.ARROW, "<yellow>下一頁</yellow>", List.of("<gray>前往第 " + (page + 2) + " 頁</gray>"), false));
      }

   }

   private ItemStack questItem(CharacterProfile character, QuestDefinition quest) {
      QuestService.Availability availability = this.questService.availability(character, quest);
      Material icon = Material.matchMaterial(quest.iconMaterial());
      List<String> lore = new ArrayList();
      lore.add(this.questCategoryName(quest));
      lore.add("<gray>建議等級：</gray><white>" + quest.minimumLevel() + "+</white>");
      lore.add("");
      lore.add("<gray>" + quest.description() + "</gray>");
      lore.add("");

      for(QuestObjectiveDefinition objective : quest.objectives()) {
         int current = this.questService.objectiveProgress(character, quest, objective);
         String color = current >= objective.requiredAmount() ? "<green>" : "<white>";
         String closing = current >= objective.requiredAmount() ? "</green>" : "</white>";
         lore.add(color + current + "/" + objective.requiredAmount() + closing + "<gray> · " + objective.description() + "</gray>");
      }

      lore.add("");
      lore.add("<gray>完成經驗：</gray><gold>" + quest.rewardExperience() + "</gold>");
      lore.add("");
      switch (availability) {
         case ACTIVE -> lore.add(character.trackedQuestId().equals(quest.id()) ? "<aqua>目前追蹤中 · 左鍵取消追蹤</aqua>" : "<yellow>左鍵追蹤</yellow>");
         case AVAILABLE -> lore.add("<yellow>左鍵接受並追蹤</yellow>");
         case LOCKED -> lore.add(character.level() < quest.minimumLevel() ? "<red>角色等級不足</red>" : "<red>尚未完成前置內容</red>");
         case COMPLETED -> lore.add("<green>已完成</green>");
      }

      return this.item(icon == null ? Material.BOOK : icon, quest.displayName(), lore, availability == QuestService.Availability.ACTIVE);
   }

   private ItemStack discoveryItem(CharacterProfile character, DiscoveryDefinition discovery) {
      DiscoveryService.Availability availability = this.discoveryService.availability(character, discovery);
      boolean hidden = discovery.hiddenUntilDiscovered() && availability != DiscoveryService.Availability.DISCOVERED;
      if (hidden) {
         return this.item(Material.GRAY_DYE, "<dark_gray>未記載的秘密</dark_gray>", List.of("<gray>在世界中探索，找出這段遺失的紀錄。</gray>", "", "<dark_gray>內容尚未發現</dark_gray>"), false);
      } else {
         List<String> lore = new ArrayList();
         lore.add(this.discoveryCategoryName(discovery));
         lore.add("<gray>建議等級：</gray><white>" + discovery.minimumLevel() + "+</white>");
         lore.add("");
         lore.add("<gray>" + discovery.description() + "</gray>");
         lore.add("");
         lore.add("<gray>首次發現經驗：</gray><gold>" + discovery.rewardExperience() + "</gold>");
         lore.add("");
         String var10001;
         switch (availability) {
            case AVAILABLE -> var10001 = "<yellow>左鍵設定羅盤目標</yellow>";
            case LOCKED -> var10001 = character.level() < discovery.minimumLevel() ? "<red>角色等級不足</red>" : "<red>尚未完成前置內容</red>";
            case DISCOVERED -> var10001 = "<green>已收入檔案庫 · 左鍵設定羅盤</green>";
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         lore.add(var10001);
         Material icon = Material.matchMaterial(discovery.iconMaterial());
         return this.item(icon == null ? Material.COMPASS : icon, discovery.displayName(), lore, availability == DiscoveryService.Availability.DISCOVERED);
      }
   }

   private String questCategoryName(QuestDefinition quest) {
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

   private String discoveryCategoryName(DiscoveryDefinition discovery) {
      String var10000;
      switch (discovery.category()) {
         case REGION -> var10000 = "<green>區域發現</green>";
         case LANDMARK -> var10000 = "<aqua>地標發現</aqua>";
         case SECRET -> var10000 = "<light_purple>秘密發現</light_purple>";
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private String plain(String value) {
      return MiniMessage.miniMessage().stripTags(value);
   }

   private ItemStack item(Material material, String name, List<String> loreLines, boolean glint) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.messages.text(name));
      var var10001 = loreLines.stream();
      MessageBundle var10002 = this.messages;
      Objects.requireNonNull(var10002);
      meta.lore(var10001.map((x$0) -> var10002.text(x$0)).toList());
      meta.setEnchantmentGlintOverride(glint);
      item.setItemMeta(meta);
      return item;
   }

   private static record Preference(ContentBookFilter filter, ContentBookSort sort) {
      private static final Preference DEFAULT;

      static {
         DEFAULT = new Preference(ContentBookFilter.RECOMMENDED, ContentBookSort.RECOMMENDED);
      }
   }
}
