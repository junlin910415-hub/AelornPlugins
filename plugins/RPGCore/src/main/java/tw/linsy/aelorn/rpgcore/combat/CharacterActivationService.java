package tw.linsy.aelorn.rpgcore.combat;

import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.combat.CombatStats;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentService;
import tw.linsy.aelorn.rpgcore.gui.WayfinderCodexService;
import java.util.Optional;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public final class CharacterActivationService {
   private final StatService statService;
   private final EquipmentService equipmentService;
   private final CombatFormula combatFormula;
   private final CombatStateService combatStateService;
   private final TrainingWeaponService trainingWeaponService;
   private final CombatHudService combatHudService;
   private final WayfinderCodexService codexService;

   public CharacterActivationService(StatService statService, EquipmentService equipmentService, CombatFormula combatFormula, CombatStateService combatStateService, TrainingWeaponService trainingWeaponService, CombatHudService combatHudService, WayfinderCodexService codexService) {
      this.statService = statService;
      this.equipmentService = equipmentService;
      this.combatFormula = combatFormula;
      this.combatStateService = combatStateService;
      this.trainingWeaponService = trainingWeaponService;
      this.combatHudService = combatHudService;
      this.codexService = codexService;
   }

   public CombatStats activate(Player player, CharacterProfile character) {
      CombatStats stats = this.applyRuntimeStats(player, character, true);
      player.setAbsorptionAmount((double)0.0F);
      this.combatStateService.activate(player.getUniqueId(), character, stats);
      this.trainingWeaponService.ensure(player, character);
      this.codexService.ensure(player, character);
      this.combatHudService.start(player);
      return stats;
   }

   public Optional<CombatStats> activeStats(Player player, CharacterProfile character) {
      return Optional.of(this.statService.calculate(character, this.equipmentService.bonuses(player, character)));
   }

   public Optional<CombatStats> refreshAttributes(Player player, CharacterProfile character) {
      return character == null ? Optional.empty() : Optional.of(this.applyRuntimeStats(player, character, false));
   }

   public void deactivate(Player player) {
      this.combatHudService.stop(player);
      this.combatStateService.remove(player.getUniqueId());
      this.applyAttribute(player, Attribute.MAX_HEALTH, (double)20.0F);
      this.applyAttribute(player, Attribute.MOVEMENT_SPEED, 0.1);
      if (player.getHealth() > (double)20.0F) {
         player.setHealth((double)20.0F);
      }

      player.setAbsorptionAmount((double)0.0F);
      player.setLevel(0);
      player.setExp(0.0F);
   }

   private void applyAttribute(Player player, Attribute attribute, double value) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance != null) {
         instance.setBaseValue(value);
      }

   }

   private CombatStats applyRuntimeStats(Player player, CharacterProfile character, boolean refillHealth) {
      CombatStats stats = this.statService.calculate(character, this.equipmentService.bonuses(player, character));
      AttributeInstance maximumHealth = player.getAttribute(Attribute.MAX_HEALTH);
      double previousMaximumHealth = maximumHealth == null ? (double)20.0F : maximumHealth.getValue();
      double previousHealthRatio = previousMaximumHealth <= (double)0.0F ? (double)1.0F : player.getHealth() / previousMaximumHealth;
      double nextMaximumHealth = Math.min((double)2048.0F, this.combatFormula.toMinecraftHealth(stats.maximumHealth()));
      this.applyAttribute(player, Attribute.MAX_HEALTH, nextMaximumHealth);
      this.applyAttribute(player, Attribute.MOVEMENT_SPEED, Math.max(0.05, Math.min(0.24, 0.1 * stats.speed() / (double)100.0F)));
      AttributeInstance refreshedMaximumHealth = player.getAttribute(Attribute.MAX_HEALTH);
      if (refreshedMaximumHealth != null) {
         if (refillHealth) {
            player.setHealth(refreshedMaximumHealth.getValue());
         } else {
            player.setHealth(Math.max((double)1.0F, Math.min(refreshedMaximumHealth.getValue(), previousHealthRatio * refreshedMaximumHealth.getValue())));
         }
      }

      this.combatStateService.refresh(player.getUniqueId(), character, stats);
      return stats;
   }
}
