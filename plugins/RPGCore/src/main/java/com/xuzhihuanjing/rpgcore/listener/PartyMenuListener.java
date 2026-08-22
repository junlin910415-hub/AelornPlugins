package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.gui.PartyMenuHolder;
import com.xuzhihuanjing.rpgcore.gui.PartyMenuService;
import com.xuzhihuanjing.rpgcore.party.PartyService;
import com.xuzhihuanjing.rpgcore.party.PartySnapshot;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

public final class PartyMenuListener implements Listener {
   private final PartyService parties;
   private final PartyMenuService menu;
   private final MessageBundle messages;

   public PartyMenuListener(PartyService parties, PartyMenuService menu, MessageBundle messages) {
      this.parties = parties;
      this.menu = menu;
      this.messages = messages;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      HumanEntity var4 = event.getWhoClicked();
      if (var4 instanceof Player player) {
         InventoryHolder var5 = event.getInventory().getHolder();
         if (var5 instanceof PartyMenuHolder holder) {
            event.setCancelled(true);
            if (holder.ownerId().equals(player.getUniqueId()) && event.getClickedInventory() == event.getInventory()) {
               if (holder.view() == PartyMenuHolder.View.ROOT) {
                  this.clickRoot(player, event);
                  return;
               }

               if (event.getRawSlot() == 45) {
                  this.menu.openRoot(player);
                  return;
               }

               if (event.getRawSlot() == 46) {
                  this.openPage(player, holder, holder.page() - 1);
                  return;
               }

               if (event.getRawSlot() == 53) {
                  this.openPage(player, holder, holder.page() + 1);
                  return;
               }

               UUID entry = this.menu.entryAt(holder, event.getRawSlot());
               if (entry == null) {
                  return;
               }

               switch (holder.view()) {
                  case INVITE:
                     this.invite(player, entry);
                     break;
                  case FINDER:
                     this.joinListed(player, entry);
                     break;
                  case INVITATIONS:
                     this.answerInvite(player, entry, event.isShiftClick() && event.isRightClick());
                  case ROOT:
               }

               return;
            }

            return;
         }
      }

   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (event.getInventory().getHolder() instanceof PartyMenuHolder) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.parties.leave(event.getPlayer().getUniqueId());
   }

   private void clickRoot(Player player, InventoryClickEvent event) {
      UUID playerId = player.getUniqueId();
      PartySnapshot party = (PartySnapshot)this.parties.partyOf(playerId).orElse(null);
      int slot = event.getRawSlot();
      if (slot == 45) {
         player.closeInventory();
      } else if (party == null) {
         if (slot == 20) {
            PartyService.Operation result = this.parties.create(playerId, System.currentTimeMillis());
            player.sendMessage(this.messages.message(result.successful() ? "party-created" : this.resultKey(result.result())));
            this.menu.openRoot(player);
         } else if (slot == 22) {
            this.menu.openFinder(player, 0);
         } else if (slot == 24) {
            this.menu.openInvitations(player, 0);
         }

      } else if (slot == 47) {
         if (!party.isLeader(playerId)) {
            player.sendMessage(this.messages.message("party-not-leader"));
         } else {
            this.menu.openInviteCandidates(player, 0);
         }
      } else if (slot == 49) {
         PartyService.Operation result = this.parties.toggleListing(playerId);
         player.sendMessage(this.messages.message(result.successful() ? (result.party().listed() ? "party-listed" : "party-unlisted") : this.resultKey(result.result())));
         this.menu.openRoot(player);
      } else if (slot == 51) {
         PartyService.Operation result = party.isLeader(playerId) && event.isShiftClick() && event.isRightClick() ? this.parties.disband(playerId) : this.parties.leave(playerId);
         player.sendMessage(this.messages.message(result.result() == PartyService.Result.PARTY_DISBANDED ? "party-disbanded" : (result.successful() ? "party-left" : this.resultKey(result.result()))));
         this.menu.openRoot(player);
      } else if (slot == 53) {
         this.menu.openFinder(player, 0);
      } else {
         PartyMenuHolder holder = (PartyMenuHolder)event.getInventory().getHolder();
         UUID memberId = this.menu.entryAt(holder, slot);
         if (memberId != null && !memberId.equals(playerId) && party.isLeader(playerId)) {
            PartyService.Operation result = event.isShiftClick() && event.isRightClick() ? this.parties.kick(playerId, memberId) : this.parties.promote(playerId, memberId);
            player.sendMessage(this.messages.message(result.successful() ? (event.isShiftClick() ? "party-member-kicked" : "party-leader-promoted") : this.resultKey(result.result())));
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && result.successful()) {
               member.sendMessage(this.messages.message(event.isShiftClick() ? "party-you-were-kicked" : "party-you-are-leader"));
            }

            this.menu.openRoot(player);
         }
      }
   }

   private void invite(Player player, UUID targetId) {
      PartyService.Operation result = this.parties.invite(player.getUniqueId(), targetId, System.currentTimeMillis());
      if (!result.successful()) {
         player.sendMessage(this.messages.message(this.resultKey(result.result())));
      } else {
         Player target = Bukkit.getPlayer(targetId);
         player.sendMessage(this.messages.message("party-invite-sent", MessageBundle.value("player", target == null ? "玩家" : target.getName())));
         if (target != null) {
            target.sendMessage(this.messages.message("party-invite-received", MessageBundle.value("player", player.getName())));
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.65F, 1.25F);
         }

         player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55F, 1.2F);
         this.menu.openInviteCandidates(player, 0);
      }
   }

   private void joinListed(Player player, UUID partyId) {
      PartyService.Operation result = this.parties.joinListed(player.getUniqueId(), partyId);
      player.sendMessage(this.messages.message(result.successful() ? "party-joined" : this.resultKey(result.result())));
      this.menu.openRoot(player);
   }

   private void answerInvite(Player player, UUID partyId, boolean decline) {
      if (decline) {
         PartyService.Result result = this.parties.decline(player.getUniqueId(), partyId);
         player.sendMessage(this.messages.message(result == PartyService.Result.SUCCESS ? "party-invite-declined" : this.resultKey(result)));
      } else {
         PartyService.Operation result = this.parties.accept(player.getUniqueId(), partyId, System.currentTimeMillis());
         player.sendMessage(this.messages.message(result.successful() ? "party-joined" : this.resultKey(result.result())));
      }

      this.menu.openRoot(player);
   }

   private void openPage(Player player, PartyMenuHolder holder, int page) {
      switch (holder.view()) {
         case INVITE -> this.menu.openInviteCandidates(player, page);
         case FINDER -> this.menu.openFinder(player, page);
         case INVITATIONS -> this.menu.openInvitations(player, page);
         case ROOT -> this.menu.openRoot(player);
      }

   }

   private String resultKey(PartyService.Result result) {
      String var10000;
      switch (result) {
         case NOT_IN_PARTY:
            var10000 = "party-not-in-party";
            break;
         case NOT_LEADER:
            var10000 = "party-not-leader";
            break;
         case TARGET_IN_PARTY:
         case ALREADY_IN_PARTY:
            var10000 = "party-target-in-party";
            break;
         case TARGET_NOT_IN_PARTY:
            var10000 = "party-target-not-in-party";
            break;
         case PARTY_FULL:
            var10000 = "party-full";
            break;
         case PARTY_NOT_FOUND:
         case PARTY_NOT_LISTED:
            var10000 = "party-not-found";
            break;
         case INVITE_EXPIRED:
            var10000 = "party-invite-expired";
            break;
         case FINDER_DISABLED:
            var10000 = "party-finder-disabled";
            break;
         case PARTY_DISBANDED:
            var10000 = "party-disbanded";
            break;
         case SUCCESS:
            var10000 = "party-created";
            break;
         default:
            throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }
}
