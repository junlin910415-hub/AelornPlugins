package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.ability.AbilityInputService;
import com.xuzhihuanjing.rpgcore.combat.CharacterActivationService;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.gui.CharacterMenuService;
import com.xuzhihuanjing.rpgcore.platform.RpgScheduler;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PlayerSessionListener implements Listener {
   private final RpgScheduler scheduler;
   private final CharacterService characterService;
   private final CharacterMenuService menuService;
   private final MessageBundle messages;
   private final boolean openSelectorOnFirstJoin;
   private final CharacterActivationService activationService;
   private final AbilityInputService abilityInputService;

   public PlayerSessionListener(RpgScheduler scheduler, CharacterService characterService, CharacterMenuService menuService, MessageBundle messages, boolean openSelectorOnFirstJoin, CharacterActivationService activationService, AbilityInputService abilityInputService) {
      this.scheduler = scheduler;
      this.characterService = characterService;
      this.menuService = menuService;
      this.messages = messages;
      this.openSelectorOnFirstJoin = openSelectorOnFirstJoin;
      this.activationService = activationService;
      this.abilityInputService = abilityInputService;
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.characterService.load(player.getUniqueId()).whenComplete((account, throwable) -> this.scheduler.executeEntity(player, () -> {
            if (!player.isOnline()) {
               this.characterService.saveAndUnload(player.getUniqueId());
            } else if (throwable != null) {
               player.sendMessage(this.messages.message("load-failed"));
            } else {
               if (!account.activeCharacter().isEmpty() && (!this.openSelectorOnFirstJoin || !account.autoOpenSelector())) {
                  account.activeCharacter().ifPresent((character) -> {
                     this.characterService.resumeActiveSession(player.getUniqueId());
                     this.activationService.activate(player, character);
                  });
               } else {
                  this.menuService.openCharacterSelector(player);
               }

            }
         }, () -> this.characterService.saveAndUnload(player.getUniqueId())));
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.abilityInputService.clear(event.getPlayer().getUniqueId());
      this.activationService.deactivate(event.getPlayer());
      this.characterService.saveAndUnload(event.getPlayer().getUniqueId());
   }

   @EventHandler
   public void onRespawn(PlayerRespawnEvent event) {
      this.scheduler.runEntityLater(event.getPlayer(), () -> this.characterService.activeCharacter(event.getPlayer().getUniqueId()).ifPresent((character) -> this.activationService.activate(event.getPlayer(), character)), () -> this.characterService.saveAndUnload(event.getPlayer().getUniqueId()), 1L);
   }
}
