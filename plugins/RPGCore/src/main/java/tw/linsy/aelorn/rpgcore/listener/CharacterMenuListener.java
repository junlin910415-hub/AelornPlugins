package tw.linsy.aelorn.rpgcore.listener;

import tw.linsy.aelorn.rpgcore.ability.AbilityInputService;
import tw.linsy.aelorn.rpgcore.combat.CharacterActivationService;
import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.domain.character.AccountProfile;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.character.DeletedCharacterBackup;
import tw.linsy.aelorn.rpgcore.domain.classes.CharacterClassDefinition;
import tw.linsy.aelorn.rpgcore.gui.CharacterBackupHolder;
import tw.linsy.aelorn.rpgcore.gui.CharacterManagementHolder;
import tw.linsy.aelorn.rpgcore.gui.CharacterMenuService;
import tw.linsy.aelorn.rpgcore.gui.CharacterSelectionHolder;
import tw.linsy.aelorn.rpgcore.gui.CharacterSelectorHolder;
import tw.linsy.aelorn.rpgcore.gui.CharacterSelectorPresentationService;
import tw.linsy.aelorn.rpgcore.gui.ClassSelectionHolder;
import tw.linsy.aelorn.rpgcore.gui.SlotLayout;
import tw.linsy.aelorn.rpgcore.platform.RpgScheduler;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.OptionalInt;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public final class CharacterMenuListener implements Listener {
   private final CharacterService characterService;
   private final CharacterMenuService menuService;
   private final MessageBundle messages;
   private final CharacterActivationService activationService;
   private final AbilityInputService abilityInputService;
   private final CharacterSelectorPresentationService presentation;
   private final RpgScheduler scheduler;

   public CharacterMenuListener(CharacterService characterService, CharacterMenuService menuService, MessageBundle messages, CharacterActivationService activationService, AbilityInputService abilityInputService, CharacterSelectorPresentationService presentation, RpgScheduler scheduler) {
      this.characterService = characterService;
      this.menuService = menuService;
      this.messages = messages;
      this.activationService = activationService;
      this.abilityInputService = abilityInputService;
      this.presentation = presentation;
      this.scheduler = scheduler;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      HumanEntity var4 = event.getWhoClicked();
      if (var4 instanceof Player player) {
         InventoryHolder var6 = event.getInventory().getHolder();
         if (var6 instanceof CharacterSelectorHolder holder) {
            event.setCancelled(true);
            if (holder.ownerId().equals(player.getUniqueId()) && event.getClickedInventory() == event.getInventory()) {
               if (holder instanceof CharacterSelectionHolder) {
                  this.clickSelector(player, event);
               } else if (holder instanceof ClassSelectionHolder) {
                  ClassSelectionHolder classHolder = (ClassSelectionHolder)holder;
                  this.clickClassSelector(player, event, classHolder);
               } else if (holder instanceof CharacterManagementHolder) {
                  CharacterManagementHolder managementHolder = (CharacterManagementHolder)holder;
                  this.clickManagement(player, event, managementHolder);
               } else if (holder instanceof CharacterBackupHolder) {
                  this.clickBackups(player, event);
               }

               return;
            }

            return;
         }
      }

   }

   private void clickSelector(Player player, InventoryClickEvent event) {
      AccountProfile account = (AccountProfile)this.characterService.loadedAccount(player.getUniqueId()).orElse(null);
      if (account != null) {
         switch (event.getRawSlot()) {
            case 15:
               this.menuService.openBackups(player);
               return;
            case 33:
               this.characterService.setSelectorMusicEnabled(player.getUniqueId(), !account.selectorMusicEnabled());
               this.menuService.openCharacterSelector(player);
               return;
            case 42:
               this.characterService.setAutoOpenSelector(player.getUniqueId(), !account.autoOpenSelector());
               this.menuService.openCharacterSelector(player);
               return;
            default:
               int slot = SlotLayout.characterSlotAt(event.getRawSlot(), this.menuService.maximumSlots());
               if (slot >= 0) {
                  CharacterProfile character = (CharacterProfile)account.characterAt(slot).orElse(null);
                  if (character == null) {
                     if (slot >= this.menuService.availableSlots(player, account)) {
                        player.sendMessage(this.messages.message("character-slot-locked"));
                     } else {
                        this.menuService.openClassSelector(player, slot);
                     }
                  } else {
                     if (!event.isRightClick() && !account.deletionPending(slot)) {
                        this.select(player, slot);
                     } else {
                        this.menuService.openCharacterManager(player, slot);
                     }

                  }
               }
         }
      }
   }

   private void clickClassSelector(Player player, InventoryClickEvent event, ClassSelectionHolder holder) {
      if (event.getRawSlot() == 49) {
         this.menuService.openCharacterSelector(player);
      } else {
         CharacterClassDefinition definition = this.menuService.classAtInventorySlot(event.getRawSlot());
         if (definition != null) {
            try {
               CharacterProfile character = this.characterService.createCharacter(player.getUniqueId(), player.getName(), holder.characterSlot(), definition.id());
               this.abilityInputService.clear(player.getUniqueId());
               this.activationService.activate(player, character);
               player.closeInventory();
               player.sendMessage(this.messages.message("character-created", MessageBundle.value("character", character.name())));
            } catch (IllegalStateException | IllegalArgumentException var6) {
               player.sendMessage(this.messages.message("slot-unavailable"));
            }

         }
      }
   }

   private void clickManagement(Player player, InventoryClickEvent event, CharacterManagementHolder holder) {
      if (event.getRawSlot() == 49) {
         this.menuService.openCharacterSelector(player);
      } else {
         AccountProfile account = (AccountProfile)this.characterService.loadedAccount(player.getUniqueId()).orElse(null);
         if (account != null && !account.characterAt(holder.characterSlot()).isEmpty()) {
            if (event.getRawSlot() == 20) {
               if (account.deletionPending(holder.characterSlot())) {
                  player.sendMessage(this.messages.message("character-deletion-pending"));
               } else {
                  this.select(player, holder.characterSlot());
               }
            } else if (event.getRawSlot() == 24) {
               if (account.deletionPending(holder.characterSlot())) {
                  this.characterService.cancelDeletion(player.getUniqueId(), holder.characterSlot());
                  player.sendMessage(this.messages.message("character-deletion-cancelled"));
               } else {
                  if (account.activeSlot() == holder.characterSlot()) {
                     this.activationService.deactivate(player);
                     this.abilityInputService.clear(player.getUniqueId());
                  }

                  Instant deleteAt = Instant.now().plus(this.menuService.deletionGraceMinutes(), ChronoUnit.MINUTES);
                  this.characterService.scheduleDeletion(player.getUniqueId(), holder.characterSlot(), deleteAt);
                  player.sendMessage(this.messages.message("character-deletion-scheduled", MessageBundle.value("minutes", Long.toString(this.menuService.deletionGraceMinutes()))));
               }

               this.menuService.openCharacterManager(player, holder.characterSlot());
            }
         } else {
            this.menuService.openCharacterSelector(player);
         }
      }
   }

   private void clickBackups(Player player, InventoryClickEvent event) {
      if (event.getRawSlot() == 49) {
         this.menuService.openCharacterSelector(player);
      } else {
         AccountProfile account = (AccountProfile)this.characterService.loadedAccount(player.getUniqueId()).orElse(null);
         if (account != null) {
            int index = SlotLayout.backupIndexAt(event.getRawSlot(), Math.min(5, account.backups().size()));
            if (index >= 0) {
               OptionalInt targetSlot = this.menuService.firstAvailableSlot(player, account);
               if (targetSlot.isEmpty()) {
                  player.sendMessage(this.messages.message("character-backup-no-slot"));
               } else {
                  DeletedCharacterBackup backup = (DeletedCharacterBackup)account.backups().get(index);
                  CharacterProfile character = this.characterService.restoreBackup(player.getUniqueId(), backup.character().id(), targetSlot.getAsInt());
                  this.abilityInputService.clear(player.getUniqueId());
                  this.activationService.activate(player, character);
                  player.closeInventory();
                  player.sendMessage(this.messages.message("character-restored", MessageBundle.value("character", character.name())));
               }
            }
         }
      }
   }

   private void select(Player player, int slot) {
      try {
         CharacterProfile selected = this.characterService.selectCharacter(player.getUniqueId(), slot);
         this.abilityInputService.clear(player.getUniqueId());
         this.activationService.activate(player, selected);
         player.closeInventory();
         player.sendMessage(this.messages.message("character-selected", MessageBundle.value("character", selected.name())));
      } catch (IllegalArgumentException var4) {
         player.sendMessage(this.messages.message("character-deletion-pending"));
      }

   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (event.getInventory().getHolder() instanceof CharacterSelectorHolder) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      HumanEntity var3 = event.getPlayer();
      if (var3 instanceof Player player) {
         if (event.getInventory().getHolder() instanceof CharacterSelectorHolder) {
            this.scheduler.runEntityLater(player, () -> {
               if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof CharacterSelectorHolder)) {
                  this.presentation.stop(player);
               }

            }, () -> this.presentation.stop(player), 1L);
            return;
         }
      }

   }
}
