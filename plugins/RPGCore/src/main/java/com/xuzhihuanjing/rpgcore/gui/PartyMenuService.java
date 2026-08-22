package com.xuzhihuanjing.rpgcore.gui;

import com.xuzhihuanjing.rpgcore.config.ClassRegistry;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.classes.CharacterClassDefinition;
import com.xuzhihuanjing.rpgcore.hud.InternalGuiTitle;
import com.xuzhihuanjing.rpgcore.party.PartyInvite;
import com.xuzhihuanjing.rpgcore.party.PartyService;
import com.xuzhihuanjing.rpgcore.party.PartySnapshot;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class PartyMenuService {
   public static final int MENU_SIZE = 54;
   public static final int CREATE_SLOT = 20;
   public static final int FINDER_SLOT = 22;
   public static final int INVITATIONS_SLOT = 24;
   public static final int INVITE_SLOT = 47;
   public static final int LISTING_SLOT = 49;
   public static final int LEAVE_SLOT = 51;
   public static final int RETURN_SLOT = 45;
   public static final int PREVIOUS_SLOT = 46;
   public static final int NEXT_SLOT = 53;
   private static final int[] CONTENT_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
   private final PartyService parties;
   private final CharacterService characters;
   private final ClassRegistry classes;
   private final MessageBundle messages;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();

   public PartyMenuService(PartyService parties, CharacterService characters, ClassRegistry classes, MessageBundle messages) {
      this.parties = parties;
      this.characters = characters;
      this.classes = classes;
      this.messages = messages;
   }

   public void open(Player player) {
      if (this.characters.activeCharacter(player.getUniqueId()).isEmpty()) {
         player.sendMessage(this.messages.message("no-active-character"));
      } else {
         this.openRoot(player);
      }
   }

   public void openRoot(Player player) {
      PartySnapshot party = (PartySnapshot)this.parties.partyOf(player.getUniqueId()).orElse(null);
      List<UUID> entries = party == null ? List.of() : party.members();
      PartyMenuHolder holder = new PartyMenuHolder(player.getUniqueId(), PartyMenuHolder.View.ROOT, 0, entries);
      Inventory inventory = this.create(holder);
      if (party == null) {
         inventory.setItem(4, this.item(Material.COMPASS, "<aqua><bold>尚未加入隊伍</bold></aqua>", List.of("<gray>建立私人隊伍，或從隊伍搜尋器加入公開隊伍。</gray>", "<dark_gray>隊伍上限：" + this.parties.maximumMembers() + " 人</dark_gray>")));
         inventory.setItem(20, this.item(Material.LIME_DYE, "<green>建立隊伍</green>", List.of("<gray>你將成為隊長。</gray>", "", "<green>左鍵建立</green>")));
         inventory.setItem(22, this.item(Material.SPYGLASS, "<gold>隊伍搜尋器</gold>", List.of("<gray>尋找目前開放加入的隊伍。</gray>", "", "<green>左鍵開啟</green>")));
         PartyService var10005 = this.parties;
         inventory.setItem(24, this.item(Material.WRITABLE_BOOK, "<light_purple>待處理邀請</light_purple>", List.of("<gray>目前有 </gray><white>" + var10005.pendingInvites(player.getUniqueId(), System.currentTimeMillis()).size() + "</white><gray> 個邀請。</gray>", "", "<green>左鍵查看</green>")));
      } else {
         inventory.setItem(4, this.partySummary(party));

         for(int index = 0; index < party.members().size() && index < CONTENT_SLOTS.length; ++index) {
            UUID memberId = (UUID)party.members().get(index);
            inventory.setItem(CONTENT_SLOTS[index], this.memberItem(memberId, party.isLeader(memberId), party.isLeader(player.getUniqueId())));
         }

         inventory.setItem(47, this.item(Material.NAME_TAG, "<green>邀請玩家</green>", List.of("<gray>從線上玩家清單選擇邀請對象。</gray>", "", party.isLeader(player.getUniqueId()) ? "<green>左鍵開啟</green>" : "<red>僅隊長可邀請</red>")));
         inventory.setItem(49, this.item(party.listed() ? Material.LIME_DYE : Material.GRAY_DYE, party.listed() ? "<green>已公開招募</green>" : "<gray>未公開招募</gray>", List.of("<gray>公開後，其他玩家可從搜尋器直接加入。</gray>", "", party.isLeader(player.getUniqueId()) ? "<yellow>左鍵切換</yellow>" : "<red>僅隊長可調整</red>")));
         inventory.setItem(51, this.item(Material.RED_DYE, party.isLeader(player.getUniqueId()) ? "<red>離開或解散隊伍</red>" : "<red>離開隊伍</red>", party.isLeader(player.getUniqueId()) ? List.of("<gray>左鍵：離開並轉移隊長</gray>", "<red>Shift + 右鍵：解散隊伍</red>") : List.of("<gray>左鍵離開目前隊伍。</gray>")));
         inventory.setItem(53, this.item(Material.SPYGLASS, "<gold>瀏覽公開隊伍</gold>", List.of("<green>左鍵開啟搜尋器</green>")));
      }

      inventory.setItem(45, this.item(Material.BARRIER, "<red>關閉</red>", List.of()));
      player.openInventory(inventory);
   }

   public void openInviteCandidates(Player player, int requestedPage) {
      List<UUID> candidates = Bukkit.getOnlinePlayers().stream().filter((candidate) -> !candidate.getUniqueId().equals(player.getUniqueId())).filter((candidate) -> this.parties.partyOf(candidate.getUniqueId()).isEmpty()).sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).map(OfflinePlayer::getUniqueId).toList();
      this.openPaged(player, PartyMenuHolder.View.INVITE, requestedPage, candidates);
   }

   public void openFinder(Player player, int requestedPage) {
      List<UUID> entries = this.parties.listedParties().stream().map(PartySnapshot::id).toList();
      this.openPaged(player, PartyMenuHolder.View.FINDER, requestedPage, entries);
   }

   public void openInvitations(Player player, int requestedPage) {
      List<UUID> entries = this.parties.pendingInvites(player.getUniqueId(), System.currentTimeMillis()).stream().map(PartyInvite::partyId).toList();
      this.openPaged(player, PartyMenuHolder.View.INVITATIONS, requestedPage, entries);
   }

   public UUID entryAt(PartyMenuHolder holder, int rawSlot) {
      int contentIndex = this.contentIndex(rawSlot);
      int entryIndex = holder.page() * CONTENT_SLOTS.length + contentIndex;
      return contentIndex >= 0 && entryIndex < holder.entries().size() ? (UUID)holder.entries().get(entryIndex) : null;
   }

   private void openPaged(Player player, PartyMenuHolder.View view, int requestedPage, List<UUID> entries) {
      int maximumPage = Math.max(0, (entries.size() - 1) / CONTENT_SLOTS.length);
      int page = Math.max(0, Math.min(maximumPage, requestedPage));
      PartyMenuHolder holder = new PartyMenuHolder(player.getUniqueId(), view, page, entries);
      Inventory inventory = this.create(holder);
      inventory.setItem(4, this.pageHeader(view, entries.size(), page, maximumPage));
      int first = page * CONTENT_SLOTS.length;

      for(int index = 0; index < CONTENT_SLOTS.length && first + index < entries.size(); ++index) {
         UUID entryId = (UUID)entries.get(first + index);
         ItemStack var10000;
         switch (view) {
            case INVITE -> var10000 = this.inviteCandidateItem(entryId);
            case FINDER -> var10000 = this.finderItem(entryId);
            case INVITATIONS -> var10000 = this.invitationItem(player.getUniqueId(), entryId);
            case ROOT -> throw new IllegalArgumentException("ROOT is not a paged view");
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         ItemStack rendered = var10000;
         inventory.setItem(CONTENT_SLOTS[index], rendered);
      }

      inventory.setItem(45, this.item(Material.ARROW, "<yellow>返回隊伍</yellow>", List.of("<gray>回到隊伍總覽。</gray>")));
      inventory.setItem(46, this.item(Material.ARROW, "<yellow>上一頁</yellow>", List.of("<gray>第 " + (page + 1) + " 頁</gray>")));
      inventory.setItem(53, this.item(Material.ARROW, "<yellow>下一頁</yellow>", List.of("<gray>第 " + (page + 1) + " 頁</gray>")));
      player.openInventory(inventory);
   }

   private Inventory create(PartyMenuHolder holder) {
      Inventory inventory = Bukkit.createInventory(holder, 54, InternalGuiTitle.party());
      holder.inventory(inventory);
      return inventory;
   }

   private ItemStack partySummary(PartySnapshot party) {
      Material var10001 = Material.NETHER_STAR;
      String var10003 = this.playerName(party.leaderId());
      var10003 = "<gray>隊長：</gray><white>" + var10003 + "</white>";
      int var10004 = party.members().size();
      return this.item(var10001, "<aqua><bold>冒險隊伍</bold></aqua>", List.of(var10003, "<gray>成員：</gray><white>" + var10004 + "/" + this.parties.maximumMembers() + "</white>", "<gray>搜尋器：</gray>" + (party.listed() ? "<green>公開</green>" : "<dark_gray>關閉</dark_gray>"), "", "<dark_gray>隊長可左鍵成員轉讓隊長，Shift + 右鍵移除。</dark_gray>"));
   }

   private ItemStack memberItem(UUID memberId, boolean leader, boolean viewerLeader) {
      CharacterProfile character = (CharacterProfile)this.characters.activeCharacter(memberId).orElse(null);
      String var10000;
      if (character == null) {
         var10000 = "-";
      } else {
         var var7 = this.classes.find(character.classId()).map(CharacterClassDefinition::displayName);
         MiniMessage var10001 = this.miniMessage;
         Objects.requireNonNull(var10001);
         var10000 = (String)var7.map(var10001::stripTags).orElse(character.classId());
      }

      String className = var10000;
      List<String> lore = new ArrayList();
      lore.add(Bukkit.getPlayer(memberId) == null ? "<red>離線</red>" : "<green>線上</green>");
      if (character != null) {
         lore.add("<gray>戰鬥 Lv. </gray><white>" + character.level() + "</white>");
         lore.add("<gray>職業：</gray><white>" + className + "</white>");
      }

      if (viewerLeader && !leader) {
         lore.add("");
         lore.add("<yellow>左鍵：轉讓隊長</yellow>");
         lore.add("<red>Shift + 右鍵：移出隊伍</red>");
      }

      return this.playerHead(memberId, (leader ? "<gold>★ </gold>" : "") + "<white>" + this.playerName(memberId) + "</white>", lore);
   }

   private ItemStack inviteCandidateItem(UUID playerId) {
      CharacterProfile character = (CharacterProfile)this.characters.activeCharacter(playerId).orElse(null);
      List<String> lore = new ArrayList();
      if (character != null) {
         lore.add("<gray>戰鬥 Lv. </gray><white>" + character.level() + "</white>");
      }

      lore.add("");
      lore.add("<green>左鍵送出邀請</green>");
      return this.playerHead(playerId, "<white>" + this.playerName(playerId) + "</white>", lore);
   }

   private ItemStack finderItem(UUID partyId) {
      PartySnapshot party = (PartySnapshot)this.parties.listedParties().stream().filter((candidate) -> candidate.id().equals(partyId)).findFirst().orElse(null);
      if (party == null) {
         return this.item(Material.BARRIER, "<red>隊伍已關閉</red>", List.of("<gray>重新整理後將移除此項目。</gray>"));
      } else {
         List<String> lore = new ArrayList();
         String var10001 = this.playerName(party.leaderId());
         lore.add("<gray>隊長：</gray><white>" + var10001 + "</white>");
         int var4 = party.members().size();
         lore.add("<gray>人數：</gray><white>" + var4 + "/" + this.parties.maximumMembers() + "</white>");
         lore.add("");
         party.members().stream().limit(6L).forEach((member) -> {
            String memberName = this.playerName(member);
            lore.add("<dark_gray>- </dark_gray><gray>" + memberName + "</gray>");
         });
         lore.add("");
         lore.add("<green>左鍵直接加入</green>");
         return this.playerHead(party.leaderId(), "<gold>" + this.playerName(party.leaderId()) + " 的隊伍</gold>", lore);
      }
   }

   private ItemStack invitationItem(UUID targetId, UUID partyId) {
      PartyInvite invite = (PartyInvite)this.parties.pendingInvites(targetId, System.currentTimeMillis()).stream().filter((candidate) -> candidate.partyId().equals(partyId)).findFirst().orElse(null);
      return invite == null ? this.item(Material.BARRIER, "<red>邀請已失效</red>", List.of()) : this.playerHead(invite.inviterId(), "<light_purple>" + this.playerName(invite.inviterId()) + " 的邀請</light_purple>", List.of("<gray>邀請你加入冒險隊伍。</gray>", "", "<green>左鍵接受</green>", "<red>Shift + 右鍵拒絕</red>"));
   }

   private ItemStack pageHeader(PartyMenuHolder.View view, int count, int page, int maximumPage) {
      String var10000;
      switch (view) {
         case INVITE -> var10000 = "邀請玩家";
         case FINDER -> var10000 = "隊伍搜尋器";
         case INVITATIONS -> var10000 = "待處理邀請";
         case ROOT -> var10000 = "冒險隊伍";
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      String title = var10000;
      return this.item(Material.COMPASS, "<aqua><bold>" + title + "</bold></aqua>", List.of("<gray>項目：</gray><white>" + count + "</white>", "<gray>頁數：</gray><white>" + (page + 1) + "/" + (maximumPage + 1) + "</white>"));
   }

   private ItemStack playerHead(UUID playerId, String name, List<String> lore) {
      ItemStack item = this.item(Material.PLAYER_HEAD, name, lore);
      ItemMeta var6 = item.getItemMeta();
      if (var6 instanceof SkullMeta skull) {
         skull.setOwningPlayer(Bukkit.getOfflinePlayer(playerId));
         item.setItemMeta(skull);
      }

      return item;
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

   private String playerName(UUID playerId) {
      Player online = Bukkit.getPlayer(playerId);
      if (online != null) {
         return online.getName();
      } else {
         OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
         return offline.getName() == null ? playerId.toString().substring(0, 8) : offline.getName();
      }
   }

   private int contentIndex(int rawSlot) {
      for(int index = 0; index < CONTENT_SLOTS.length; ++index) {
         if (CONTENT_SLOTS[index] == rawSlot) {
            return index;
         }
      }

      return -1;
   }
}
