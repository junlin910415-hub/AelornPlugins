package tw.linsy.aelorn.rpgcore.gui;

import tw.linsy.aelorn.rpgcore.config.ClassRegistry;
import tw.linsy.aelorn.rpgcore.config.DiscoveryRegistry;
import tw.linsy.aelorn.rpgcore.config.InterfaceSettings;
import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.config.QuestRegistry;
import tw.linsy.aelorn.rpgcore.domain.character.AccountProfile;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.character.DeletedCharacterBackup;
import tw.linsy.aelorn.rpgcore.domain.classes.ArchetypeDefinition;
import tw.linsy.aelorn.rpgcore.domain.classes.CharacterClassDefinition;
import tw.linsy.aelorn.rpgcore.domain.classes.ClassRatings;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestStatus;
import tw.linsy.aelorn.rpgcore.hud.InternalGuiTitle;
import tw.linsy.aelorn.rpgcore.integration.nexo.HudGlyphProvider;
import tw.linsy.aelorn.rpgcore.platform.RpgScheduler;
import tw.linsy.aelorn.rpgcore.progression.ProgressionResult;
import tw.linsy.aelorn.rpgcore.progression.ProgressionService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class CharacterMenuService {
   public static final int MENU_SIZE = 54;
   public static final int BACKUPS_SLOT = 15;
   public static final int PENDING_DELETIONS_SLOT = 24;
   public static final int SELECTOR_MUSIC_SLOT = 33;
   public static final int AUTO_OPEN_SLOT = 42;
   public static final int INFO_SLOT = 49;
   public static final int MANAGE_PLAY_SLOT = 20;
   public static final int MANAGE_INFO_SLOT = 22;
   public static final int MANAGE_DELETE_SLOT = 24;
   public static final int BACK_SLOT = 49;
   private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.systemDefault());
   private final CharacterService characterService;
   private final ClassRegistry classRegistry;
   private final QuestRegistry questRegistry;
   private final DiscoveryRegistry discoveryRegistry;
   private final ProgressionService progressionService;
   private final MessageBundle messages;
   private final HudGlyphProvider glyphs;
   private final InterfaceSettings interfaceSettings;
   private final CharacterSelectorPresentationService presentation;
   private final int baseSlots;
   private final int maximumSlots;
   private final long deletionGraceMinutes;
   private final RpgScheduler scheduler;

   public CharacterMenuService(RpgScheduler scheduler, CharacterService characterService, ClassRegistry classRegistry, QuestRegistry questRegistry, DiscoveryRegistry discoveryRegistry, ProgressionService progressionService, MessageBundle messages, HudGlyphProvider glyphs, InterfaceSettings interfaceSettings, CharacterSelectorPresentationService presentation, int baseSlots, int maximumSlots, long deletionGraceMinutes) {
      if (!SlotLayout.isValid(54, maximumSlots, classRegistry.size())) {
         throw new IllegalArgumentException("Character menu layout cannot contain the configured entries");
      } else {
         this.scheduler = scheduler;
         this.characterService = characterService;
         this.classRegistry = classRegistry;
         this.questRegistry = questRegistry;
         this.discoveryRegistry = discoveryRegistry;
         this.progressionService = progressionService;
         this.messages = messages;
         this.glyphs = glyphs;
         this.interfaceSettings = interfaceSettings;
         this.presentation = presentation;
         this.baseSlots = baseSlots;
         this.maximumSlots = maximumSlots;
         this.deletionGraceMinutes = deletionGraceMinutes;
      }
   }

   public void openCharacterSelector(Player player) {
      AccountProfile loaded = (AccountProfile)this.characterService.loadedAccount(player.getUniqueId()).orElse(null);
      if (loaded == null) {
         player.sendMessage(this.messages.message("loading"));
         this.characterService.load(player.getUniqueId()).whenComplete((accountx, throwable) -> this.scheduler.executeEntity(player, () -> {
               if (!player.isOnline()) {
                  this.characterService.saveAndUnload(player.getUniqueId());
               } else if (throwable != null) {
                  player.sendMessage(this.messages.message("load-failed"));
               } else {
                  this.openCharacterSelector(player);
               }
            }, () -> this.characterService.saveAndUnload(player.getUniqueId())));
      } else {
         AccountProfile account = this.characterService.finalizeExpiredDeletions(player.getUniqueId());
         CharacterSelectionHolder holder = new CharacterSelectionHolder(player.getUniqueId());
         Inventory inventory = Bukkit.createInventory(holder, 54, this.title(player, "選擇角色"));
         holder.inventory(inventory);
         int availableSlots = this.availableSlots(player, account);

         for(int slot = 0; slot < this.maximumSlots; ++slot) {
            CharacterProfile character = (CharacterProfile)account.characterAt(slot).orElse(null);
            ItemStack item;
            if (character != null) {
               item = account.deletionPending(slot) ? this.pendingDeletionItem(character, (Instant)account.pendingDeletions().get(slot)) : this.characterItem(character, account.activeSlot() == slot);
            } else if (slot < availableSlots) {
               item = this.emptySlotItem(slot);
            } else {
               item = this.lockedSlotItem(slot);
            }

            inventory.setItem(SlotLayout.inventorySlotForCharacter(slot), item);
         }

         inventory.setItem(15, this.backupControl(account));
         inventory.setItem(24, this.pendingControl(account));
         inventory.setItem(33, this.toggleItem(Material.NOTE_BLOCK, "角色選擇音樂", account.selectorMusicEnabled(), "在角色選擇器播放環境音樂"));
         inventory.setItem(42, this.toggleItem(Material.CLOCK, "登入自動開啟", account.autoOpenSelector(), "每次登入後開啟角色選擇器"));
         inventory.setItem(49, this.simpleItem(Material.WRITABLE_BOOK, "<gold>冒險者檔案</gold>", List.of("<gray>左鍵選擇或建立角色</gray>", "<gray>右鍵管理既有角色</gray>", "<dark_gray>可用欄位：</dark_gray><white>" + availableSlots + "/" + this.maximumSlots + "</white>")));
         player.openInventory(inventory);
         this.presentation.start(player, account.selectorMusicEnabled());
      }
   }

   public void openClassSelector(Player player, int characterSlot) {
      AccountProfile account = (AccountProfile)this.characterService.loadedAccount(player.getUniqueId()).orElse(null);
      if (account != null && characterSlot >= 0 && characterSlot < this.availableSlots(player, account) && !account.characterAt(characterSlot).isPresent()) {
         ClassSelectionHolder holder = new ClassSelectionHolder(player.getUniqueId(), characterSlot);
         Inventory inventory = Bukkit.createInventory(holder, 54, this.title(player, "選擇職業"));
         holder.inventory(inventory);
         int index = 0;

         for(CharacterClassDefinition definition : this.classRegistry.all()) {
            inventory.setItem(SlotLayout.inventorySlotForClass(index++), this.classItem(definition));
         }

         inventory.setItem(49, this.simpleItem(Material.ARROW, "<yellow>返回角色選擇</yellow>", List.of()));
         player.openInventory(inventory);
         this.presentation.start(player, account.selectorMusicEnabled());
      } else {
         player.sendMessage(this.messages.message("slot-unavailable"));
      }
   }

   public void openCharacterManager(Player player, int characterSlot) {
      AccountProfile account = (AccountProfile)this.characterService.loadedAccount(player.getUniqueId()).orElse(null);
      CharacterProfile character = account == null ? null : (CharacterProfile)account.characterAt(characterSlot).orElse(null);
      if (character == null) {
         player.sendMessage(this.messages.message("slot-unavailable"));
      } else {
         CharacterManagementHolder holder = new CharacterManagementHolder(player.getUniqueId(), characterSlot);
         Inventory inventory = Bukkit.createInventory(holder, 54, this.title(player, "管理角色"));
         holder.inventory(inventory);
         Material var10003 = Material.LIME_DYE;
         String var10005 = character.name();
         inventory.setItem(20, this.simpleItem(var10003, "<green>使用這個角色</green>", List.of("<gray>進入 " + var10005 + " 的冒險。</gray>")));
         inventory.setItem(22, this.characterItem(character, account.activeSlot() == characterSlot));
         Instant deleteAt = (Instant)account.pendingDeletions().get(characterSlot);
         inventory.setItem(24, deleteAt == null ? this.simpleItem(Material.RED_DYE, "<red>排入刪除</red>", List.of("<gray>角色會先進入等待期，不會立即消失。</gray>", "<yellow>再次點擊後開始 " + this.deletionGraceMinutes + " 分鐘等待。</yellow>")) : this.simpleItem(Material.CLOCK, "<yellow>取消刪除</yellow>", List.of("<gray>剩餘：</gray><white>" + this.remaining(deleteAt) + "</white>", "<green>點擊保留這個角色</green>")));
         inventory.setItem(49, this.simpleItem(Material.ARROW, "<yellow>返回角色選擇</yellow>", List.of()));
         player.openInventory(inventory);
         this.presentation.start(player, account.selectorMusicEnabled());
      }
   }

   public void openBackups(Player player) {
      AccountProfile account = (AccountProfile)this.characterService.loadedAccount(player.getUniqueId()).orElse(null);
      if (account != null) {
         CharacterBackupHolder holder = new CharacterBackupHolder(player.getUniqueId());
         Inventory inventory = Bukkit.createInventory(holder, 54, this.title(player, "角色備份"));
         holder.inventory(inventory);
         int count = Math.min(5, account.backups().size());

         for(int index = 0; index < count; ++index) {
            DeletedCharacterBackup backup = (DeletedCharacterBackup)account.backups().get(index);
            inventory.setItem(SlotLayout.inventorySlotForBackup(index), this.backupItem(backup));
         }

         if (count == 0) {
            inventory.setItem(22, this.simpleItem(Material.GRAY_DYE, "<gray>目前沒有角色備份</gray>", List.of("<dark_gray>完成刪除等待期後，角色會保留在這裡。</dark_gray>")));
         }

         inventory.setItem(49, this.simpleItem(Material.ARROW, "<yellow>返回角色選擇</yellow>", List.of()));
         player.openInventory(inventory);
         this.presentation.start(player, account.selectorMusicEnabled());
      }
   }

   public int maximumSlots() {
      return this.maximumSlots;
   }

   public long deletionGraceMinutes() {
      return this.deletionGraceMinutes;
   }

   public int availableSlots(Player player, AccountProfile account) {
      int result = this.baseSlots;

      for(int slots = this.maximumSlots; slots > this.baseSlots; --slots) {
         if (player.hasPermission("rpgcore.characters.slots." + slots)) {
            result = slots;
            break;
         }
      }

      int highestOccupied = account.characters().keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
      return Math.min(this.maximumSlots, Math.max(result, highestOccupied));
   }

   public OptionalInt firstAvailableSlot(Player player, AccountProfile account) {
      int slots = this.availableSlots(player, account);

      for(int slot = 0; slot < slots; ++slot) {
         if (account.characterAt(slot).isEmpty()) {
            return OptionalInt.of(slot);
         }
      }

      return OptionalInt.empty();
   }

   public CharacterClassDefinition classAtInventorySlot(int inventorySlot) {
      int index = 0;

      for(CharacterClassDefinition definition : this.classRegistry.all()) {
         if (SlotLayout.inventorySlotForClass(index++) == inventorySlot) {
            return definition;
         }
      }

      return null;
   }

   private Component title(Player player, String fallback) {
      return InternalGuiTitle.character(fallback);
   }

   private ItemStack characterItem(CharacterProfile character, boolean active) {
      CharacterClassDefinition definition = (CharacterClassDefinition)this.classRegistry.find(character.classId()).orElse(null);
      Material material = definition == null ? Material.BARRIER : Material.matchMaterial(definition.iconMaterial());
      ProgressionResult progress = this.progressionService.describe(character);
      int percent = progress.requiredLevelExperience() == 0L ? 100 : (int)Math.round((double)progress.currentLevelExperience() * (double)100.0F / (double)progress.requiredLevelExperience());
      int completedQuests = (int)character.questProgress().values().stream().filter((quest) -> quest.status() == QuestStatus.COMPLETED).count();
      int completedContent = completedQuests + character.discoveredLocations().size();
      int totalContent = this.questRegistry.size() + this.discoveryRegistry.size();
      List<String> lore = new ArrayList();
      String var10001 = definition == null ? "<red>資料遺失</red>" : definition.displayName();
      lore.add("<gray>職業：</gray>" + var10001);
      int var11 = character.level();
      lore.add("<gray>戰鬥等級：</gray><white>" + var11 + "</white> <dark_gray>(" + percent + "%)</dark_gray>");
      String var12 = this.playTime(character.playTimeSeconds());
      lore.add("<gray>遊玩時間：</gray><white>" + var12 + "</white>");
      var12 = DATE_FORMAT.format(character.lastPlayedAt());
      lore.add("<gray>最近遊玩：</gray><white>" + var12 + "</white>");
      lore.add("");
      lore.add("<gold>內容進度</gold> <white>" + completedContent + "/" + totalContent + "</white>");
      lore.add(this.progressBar(completedContent, totalContent));
      lore.add("");
      lore.add(active ? "<green>目前使用中的角色</green>" : "<green>左鍵選擇</green>");
      lore.add("<yellow>右鍵管理角色</yellow>");
      return this.simpleItem(material == null ? Material.BARRIER : material, (active ? "<green>" : "<gold>") + character.name() + (active ? "</green>" : "</gold>"), lore);
   }

   private ItemStack pendingDeletionItem(CharacterProfile character, Instant deleteAt) {
      return this.simpleItem(Material.CLOCK, "<red>等待刪除：" + character.name() + "</red>", List.of("<gray>剩餘：</gray><white>" + this.remaining(deleteAt) + "</white>", "<yellow>右鍵開啟管理並取消</yellow>"));
   }

   private ItemStack emptySlotItem(int slot) {
      return this.simpleItem(Material.LIME_DYE, "<green>建立角色 " + (slot + 1) + "</green>", List.of("<gray>這個角色欄位可以使用。</gray>", "<green>左鍵選擇職業</green>"));
   }

   private ItemStack lockedSlotItem(int slot) {
      return this.simpleItem(Material.GRAY_DYE, "<dark_gray>角色欄位 " + (slot + 1) + " 已鎖定</dark_gray>", List.of("<gray>需要權限：</gray><white>rpgcore.characters.slots." + (slot + 1) + "</white>"));
   }

   private ItemStack backupControl(AccountProfile account) {
      return this.simpleItem(Material.CHEST, "<yellow>角色備份</yellow>", List.of("<gray>保存最近完成刪除的角色。</gray>", "<white>目前備份：</white><gold>" + account.backups().size() + "</gold>", "<green>點擊開啟</green>"));
   }

   private ItemStack pendingControl(AccountProfile account) {
      int amount = account.pendingDeletions().size();
      return this.simpleItem(amount == 0 ? Material.GRAY_DYE : Material.RED_DYE, amount == 0 ? "<gray>沒有等待刪除的角色</gray>" : "<red>等待刪除：" + amount + "</red>", List.of("<gray>可從角色管理頁取消刪除。</gray>"));
   }

   private ItemStack toggleItem(Material material, String label, boolean enabled, String description) {
      return this.simpleItem(material, "<gold>" + label + "</gold>", List.of("<gray>" + description + "</gray>", enabled ? "<green>● 已啟用</green>" : "<dark_gray>○ 已停用</dark_gray>", "<yellow>點擊切換</yellow>"));
   }

   private ItemStack backupItem(DeletedCharacterBackup backup) {
      CharacterProfile character = backup.character();
      CharacterClassDefinition definition = (CharacterClassDefinition)this.classRegistry.find(character.classId()).orElse(null);
      Material material = definition == null ? Material.BARRIER : Material.matchMaterial(definition.iconMaterial());
      Material var10001 = material == null ? Material.BARRIER : material;
      String var10002 = "<gold>" + character.name() + "</gold>";
      String var10003 = "<gray>職業：</gray>" + (definition == null ? "<red>未知</red>" : definition.displayName());
      String var10004 = "<gray>等級：</gray><white>" + character.level() + "</white>";
      DateTimeFormatter var10005 = DATE_FORMAT;
      return this.simpleItem(var10001, var10002, List.of(var10003, var10004, "<gray>刪除日期：</gray><white>" + var10005.format(backup.deletedAt()) + "</white>", "", "<green>點擊還原到第一個可用欄位</green>"));
   }

   private ItemStack classItem(CharacterClassDefinition definition) {
      Material material = Material.matchMaterial(definition.iconMaterial());
      return this.simpleItem(material == null ? Material.STONE : material, definition.displayName(), classLore(definition));
   }

   static List<String> classLore(CharacterClassDefinition definition) {
      List<String> lore = new ArrayList(definition.description());
      lore.add("");
      lore.add("<gold><bold>職業概覽</bold></gold>");
      lore.add("<gray>主要武器</gray> <dark_gray>│</dark_gray> <white>" + definition.weapon() + "</white>");
      lore.add("<gray>戰鬥定位</gray> <dark_gray>│</dark_gray> <white>" + definition.role() + "</white>");
      lore.add("<gray>防護特性</gray> <dark_gray>│</dark_gray> " + defenseProfile(definition.balance().damageTakenMultiplier()));
      lore.add("");
      lore.add("<aqua><bold>戰鬥評級</bold></aqua>");
      addRatings(lore, definition.balance().ratings());
      lore.add("");
      lore.add("<light_purple><bold>進階分支</bold></light_purple>");

      for(ArchetypeDefinition archetype : definition.archetypes()) {
         String var10001 = archetype.displayName();
         lore.add(var10001 + " <dark_gray>- " + archetype.description() + "</dark_gray>");
      }

      lore.add("");
      lore.add("<green><bold>左鍵</bold></green><white> 建立此職業</white>");
      return List.copyOf(lore);
   }

   private static void addRatings(List<String> lore, ClassRatings ratings) {
      lore.add(ratingLine("操作難度", ratings.difficulty()));
      lore.add(ratingLine("傷害輸出", ratings.damage()));
      lore.add(ratingLine("生存能力", ratings.defense()));
      lore.add(ratingLine("攻擊距離", ratings.range()));
      lore.add(ratingLine("機動能力", ratings.mobility()));
      lore.add(ratingLine("團隊輔助", ratings.support()));
   }

   private static String ratingLine(String label, int rating) {
      return "<gray>" + label + "</gray> <dark_gray>│</dark_gray> " + ratingBar(rating) + " <gray>" + rating + "/5</gray>";
   }

   private static String ratingBar(int rating) {
      String var10000 = "■".repeat(rating);
      return "<gold>" + var10000 + "</gold><dark_gray>" + "□".repeat(5 - rating) + "</dark_gray>";
   }

   private static String defenseProfile(double multiplier) {
      String profile;
      if (multiplier <= (double)1.0F) {
         profile = "<green>堅韌</green>";
      } else if (multiplier <= 1.2) {
         profile = "<yellow>均衡</yellow>";
      } else if (multiplier <= 1.3) {
         profile = "<gold>偏脆</gold>";
      } else {
         profile = "<red>脆弱</red>";
      }

      return profile + " <dark_gray>(承傷 " + Math.round(multiplier * (double)100.0F) + "%)</dark_gray>";
   }

   private String progressBar(int current, int maximum) {
      int filled = maximum <= 0 ? 0 : (int)Math.round(Math.min((double)1.0F, (double)current / (double)maximum) * (double)18.0F);
      String var10000 = "■".repeat(filled);
      return "<green>" + var10000 + "</green><dark_gray>" + "■".repeat(18 - filled) + "</dark_gray>";
   }

   private String playTime(long seconds) {
      long hours = seconds / 3600L;
      long minutes = seconds % 3600L / 60L;
      return hours + " 小時 " + minutes + " 分";
   }

   private String remaining(Instant deadline) {
      long seconds = Math.max(0L, Duration.between(Instant.now(), deadline).getSeconds());
      long minutes = seconds / 60L;
      return minutes + " 分 " + seconds % 60L + " 秒";
   }

   private ItemStack simpleItem(Material material, String name, List<String> loreLines) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.messages.text(name).decoration(TextDecoration.ITALIC, false));
      var var10000 = loreLines.stream();
      MessageBundle var10001 = this.messages;
      Objects.requireNonNull(var10001);
      List<Component> lore = var10000.map((x$0) -> var10001.text(x$0)).map((line) -> line.decoration(TextDecoration.ITALIC, false)).toList();
      meta.lore(lore);
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
      item.setItemMeta(meta);
      return item;
   }
}
