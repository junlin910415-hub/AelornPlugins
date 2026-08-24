package tw.linsy.aelorn.rpgcore.ability;

import tw.linsy.aelorn.rpgcore.api.event.RpgAbilityCastEvent;
import tw.linsy.aelorn.rpgcore.combat.CombatStateService;
import tw.linsy.aelorn.rpgcore.combat.HudNotificationService;
import tw.linsy.aelorn.rpgcore.combat.PlayerCombatState;
import tw.linsy.aelorn.rpgcore.combat.StatService;
import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.domain.ability.AbilityDefinition;
import tw.linsy.aelorn.rpgcore.domain.ability.AbilityModifiers;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.combat.CombatStats;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentService;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class AbilityCastService {
   private final CombatStateService combatStateService;
   private final AbilityExecutor abilityExecutor;
   private final StatService statService;
   private final EquipmentService equipmentService;
   private final MessageBundle messages;
   private final AbilityModifierService modifierService;
   private final HudNotificationService notifications;

   public AbilityCastService(CombatStateService combatStateService, AbilityExecutor abilityExecutor, StatService statService, EquipmentService equipmentService, MessageBundle messages, AbilityModifierService modifierService, HudNotificationService notifications) {
      this.combatStateService = combatStateService;
      this.abilityExecutor = abilityExecutor;
      this.statService = statService;
      this.equipmentService = equipmentService;
      this.messages = messages;
      this.modifierService = modifierService;
      this.notifications = notifications;
   }

   public boolean cast(Player player, CharacterProfile character, AbilityDefinition ability) {
      if (!ability.classId().equals(character.classId())) {
         return false;
      } else {
         PlayerCombatState state = (PlayerCombatState)this.combatStateService.state(player.getUniqueId()).orElse(null);
         if (state != null && state.characterId().equals(character.id())) {
            long now = System.currentTimeMillis();
            AbilityModifiers modifiers = this.modifierService.modifiers(character);
            CombatStats stats = this.statService.calculate(character, this.equipmentService.bonuses(player, character));
            double manaCost = ability.manaCost() * ((double)1.0F - modifiers.manaReduction()) * ((double)1.0F - stats.spellCostReduction());
            manaCost = Math.max((double)1.0F, manaCost);
            double cooldownSeconds = ability.cooldownSeconds() * ((double)1.0F - modifiers.cooldownReduction());
            long remaining = state.cooldownRemainingMillis(ability.id(), now);
            if (remaining > 0L) {
               this.notifications.show(player.getUniqueId(), this.messages.content("ability-cooldown", MessageBundle.value("ability", this.plainDisplayName(ability.displayName())), MessageBundle.value("seconds", String.format(Locale.ROOT, "%.1f", (double)remaining / (double)1000.0F))));
               return false;
            } else if (!state.spendMana(manaCost)) {
               this.notifications.show(player.getUniqueId(), this.messages.content("ability-no-mana", MessageBundle.value("mana", Integer.toString((int)Math.ceil(manaCost)))));
               return false;
            } else {
               RpgAbilityCastEvent event = new RpgAbilityCastEvent(player, character, ability);
               Bukkit.getPluginManager().callEvent(event);
               if (event.isCancelled()) {
                  state.refundMana(manaCost);
                  return false;
               } else {
                  this.abilityExecutor.execute(player, character, ability);
                  state.startCooldown(ability.id(), cooldownSeconds, now);
                  this.notifications.show(player.getUniqueId(), this.messages.content("ability-cast", MessageBundle.value("ability", this.plainDisplayName(ability.displayName()))));
                  return true;
               }
            }
         } else {
            return false;
         }
      }
   }

   private String plainDisplayName(String miniMessage) {
      return miniMessage.replaceAll("<[^>]+>", "");
   }
}
