package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.equipment.EmeraldCurrencyService;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentIdentificationQuote;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentIdentifyResult;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentService;
import com.xuzhihuanjing.rpgcore.gui.IdentificationMenuHolder;
import com.xuzhihuanjing.rpgcore.gui.IdentificationMenuService;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class IdentificationMenuListener implements Listener {
   private final CharacterService characterService;
   private final IdentificationMenuService menuService;
   private final EquipmentService equipmentService;
   private final EmeraldCurrencyService currencyService;
   private final MessageBundle messages;

   public IdentificationMenuListener(CharacterService characterService, IdentificationMenuService menuService, EquipmentService equipmentService, EmeraldCurrencyService currencyService, MessageBundle messages) {
      this.characterService = characterService;
      this.menuService = menuService;
      this.equipmentService = equipmentService;
      this.currencyService = currencyService;
      this.messages = messages;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      InventoryHolder var4 = event.getView().getTopInventory().getHolder();
      if (var4 instanceof IdentificationMenuHolder holder) {
         HumanEntity var8 = event.getWhoClicked();
         if (var8 instanceof Player player) {
            event.setCancelled(true);
            if (!holder.ownerId().equals(player.getUniqueId())) {
               return;
            }

            CharacterProfile character = this.activeCharacter(player, holder);
            if (character == null) {
               player.closeInventory();
               return;
            }

            int rawSlot = event.getRawSlot();
            if (rawSlot < 36) {
               if (this.menuService.isInputSlot(rawSlot)) {
                  this.menuService.returnInput(player, holder, rawSlot);
               } else if (rawSlot == 16) {
                  this.confirm(player, holder, character);
               } else if (rawSlot == 31) {
                  player.closeInventory();
               }

               return;
            }

            ItemStack source = event.getCurrentItem();
            if (source != null && !source.getType().isAir()) {
               EquipmentIdentificationQuote quote = this.equipmentService.quote(source);
               if (!quote.ready()) {
                  this.sendQuoteFailure(player, quote);
                  return;
               }

               if (!this.menuService.addOne(holder, source)) {
                  player.sendMessage(this.messages.message("identify-menu-full"));
                  return;
               }

               if (source.getAmount() <= 1) {
                  event.setCurrentItem((ItemStack)null);
               } else {
                  source.setAmount(source.getAmount() - 1);
                  event.setCurrentItem(source);
               }

               this.menuService.refresh(player, holder);
               return;
            }

            return;
         }
      }

   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof IdentificationMenuHolder) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      InventoryHolder var4 = event.getInventory().getHolder();
      if (var4 instanceof IdentificationMenuHolder holder) {
         HumanEntity var5 = event.getPlayer();
         if (var5 instanceof Player player) {
            this.menuService.returnAll(player, holder);
         }
      }

   }

   private void confirm(Player player, IdentificationMenuHolder holder, CharacterProfile character) {
      List<ItemStack> items = this.menuService.inputItems(holder);
      if (items.isEmpty()) {
         player.sendMessage(this.messages.message("identify-empty-selection"));
      } else {
         var var10000 = items.stream();
         EquipmentService var10001 = this.equipmentService;
         Objects.requireNonNull(var10001);
         List<EquipmentIdentificationQuote> quotes = var10000.map(var10001::quote).toList();
         EquipmentIdentificationQuote invalid = (EquipmentIdentificationQuote)quotes.stream().filter((quote) -> !quote.ready()).findFirst().orElse(null);
         if (invalid != null) {
            this.sendQuoteFailure(player, invalid);
            this.menuService.refresh(player, holder);
         } else {
            int totalCost = this.menuService.totalCost(quotes);
            int balance = this.currencyService.balance(player);
            if (balance < totalCost) {
               player.sendMessage(this.messages.message("identify-not-enough-currency", MessageBundle.value("cost", Integer.toString(totalCost)), MessageBundle.value("balance", Integer.toString(balance))));
               this.menuService.refresh(player, holder);
            } else {
               List<ItemStack> prepared = items.stream().map(ItemStack::clone).toList();
               int completed = 0;

               for(ItemStack item : prepared) {
                  EquipmentIdentifyResult result = this.equipmentService.identify(item, character);
                  if (result.status() == EquipmentIdentifyResult.Status.SUCCESS) {
                     ++completed;
                  }
               }

               if (completed != items.size()) {
                  player.sendMessage(this.messages.message("identify-invalid-data"));
               } else if (!this.currencyService.withdraw(player, totalCost)) {
                  player.sendMessage(this.messages.message("identify-not-enough-currency", MessageBundle.value("cost", Integer.toString(totalCost)), MessageBundle.value("balance", Integer.toString(this.currencyService.balance(player)))));
                  this.menuService.refresh(player, holder);
               } else {
                  this.menuService.replaceInputItems(holder, prepared);
                  player.sendMessage(this.messages.message("identify-batch-success", MessageBundle.value("amount", Integer.toString(completed)), MessageBundle.value("cost", Integer.toString(totalCost))));
                  this.menuService.refresh(player, holder);
               }
            }
         }
      }
   }

   private CharacterProfile activeCharacter(Player player, IdentificationMenuHolder holder) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      return character != null && holder.characterId().equals(character.id()) ? character : null;
   }

   private void sendQuoteFailure(Player player, EquipmentIdentificationQuote quote) {
      switch (quote.status()) {
         case READY:
         default:
            break;
         case NOT_EQUIPMENT:
            player.sendMessage(this.messages.message("identify-not-equipment"));
            break;
         case UNKNOWN_TEMPLATE:
            player.sendMessage(this.messages.message("identify-unknown-template", MessageBundle.value("template", quote.templateId())));
            break;
         case INVALID_DATA:
            player.sendMessage(this.messages.message("identify-invalid-data"));
            break;
         case REROLL_LIMIT:
            player.sendMessage(this.messages.message("identify-reroll-limit"));
      }

   }
}
