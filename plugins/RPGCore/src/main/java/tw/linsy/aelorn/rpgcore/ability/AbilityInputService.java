package tw.linsy.aelorn.rpgcore.ability;

import tw.linsy.aelorn.rpgcore.combat.HudNotificationService;
import tw.linsy.aelorn.rpgcore.combat.TrainingWeaponService;
import tw.linsy.aelorn.rpgcore.config.AbilityRegistry;
import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.domain.ability.AbilityDefinition;
import tw.linsy.aelorn.rpgcore.domain.ability.InputToken;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class AbilityInputService {
   private final CharacterService characterService;
   private final AbilityRegistry abilityRegistry;
   private final AbilityCastService abilityCastService;
   private final TrainingWeaponService trainingWeaponService;
   private final MessageBundle messages;
   private final HudNotificationService notifications;
   private final long comboTimeoutMillis;
   private final Map<UUID, ComboBuffer> buffers = new ConcurrentHashMap();

   public AbilityInputService(CharacterService characterService, AbilityRegistry abilityRegistry, AbilityCastService abilityCastService, TrainingWeaponService trainingWeaponService, MessageBundle messages, HudNotificationService notifications, long comboTimeoutMillis) {
      this.characterService = characterService;
      this.abilityRegistry = abilityRegistry;
      this.abilityCastService = abilityCastService;
      this.trainingWeaponService = trainingWeaponService;
      this.messages = messages;
      this.notifications = notifications;
      this.comboTimeoutMillis = comboTimeoutMillis;
   }

   public boolean input(Player player, InputToken token) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character != null && this.trainingWeaponService.isActiveWeapon(player, player.getInventory().getItemInMainHand(), character)) {
         if (this.abilityRegistry.abilitiesFor(character.classId()).isEmpty()) {
            if (token == InputToken.RIGHT) {
               this.notifications.show(player.getUniqueId(), this.messages.content("ability-unavailable"));
               return true;
            } else {
               return false;
            }
         } else {
            long now = System.currentTimeMillis();
            ComboBuffer buffer = (ComboBuffer)this.buffers.computeIfAbsent(player.getUniqueId(), (ignored) -> new ComboBuffer(this.comboTimeoutMillis));
            List<InputToken> input = buffer.accept(token, now);
            if (input.isEmpty()) {
               return false;
            } else {
               AbilityDefinition ability = (AbilityDefinition)this.abilityRegistry.findByInput(character.classId(), input).orElse(null);
               if (ability != null) {
                  buffer.clear();
                  this.abilityCastService.cast(player, character, ability);
                  return true;
               } else if (this.abilityRegistry.hasPrefix(character.classId(), input)) {
                  return true;
               } else {
                  if (token == InputToken.RIGHT) {
                     buffer.restartWithRight(now);
                  } else {
                     buffer.clear();
                     this.notifications.show(player.getUniqueId(), this.messages.content("combo-invalid"));
                  }

                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   public boolean hasPending(Player player) {
      ComboBuffer buffer = (ComboBuffer)this.buffers.get(player.getUniqueId());
      return buffer != null && buffer.isPending(System.currentTimeMillis());
   }

   public List<InputToken> pendingInput(Player player) {
      ComboBuffer buffer = (ComboBuffer)this.buffers.get(player.getUniqueId());
      return buffer == null ? List.of() : buffer.snapshot(System.currentTimeMillis());
   }

   public void clear(UUID playerId) {
      this.buffers.remove(playerId);
      this.notifications.clear(playerId);
   }

   public void clearAll() {
      this.buffers.clear();
      this.notifications.clearAll();
   }
}
