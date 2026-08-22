package com.xuzhihuanjing.rpgcore.combat;

import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.combat.CombatStats;
import com.xuzhihuanjing.rpgcore.domain.combat.DamageKind;
import com.xuzhihuanjing.rpgcore.domain.combat.DamageResult;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentService;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class DamagePipeline {
   private final CharacterService characterService;
   private final StatService statService;
   private final EquipmentService equipmentService;
   private final CombatFormula formula;
   private final ThreadLocal<Integer> internalDepth = ThreadLocal.withInitial(() -> 0);
   private final ThreadLocal<DamageResult> currentResult = new ThreadLocal();

   public DamagePipeline(CharacterService characterService, StatService statService, EquipmentService equipmentService, CombatFormula formula) {
      this.characterService = characterService;
      this.statService = statService;
      this.equipmentService = equipmentService;
      this.formula = formula;
   }

   public double basicAttackDamage(Player attacker) {
      Optional<Double> var10000 = this.characterService.activeCharacter(attacker.getUniqueId()).map((character) -> this.statService.calculate(character, this.equipmentService.bonuses(attacker, character))).map((stats) -> {
         double damage = this.varied(stats.attackPower() * stats.basicAttackMultiplier());
         return this.roll(stats.criticalChance()) ? this.formula.criticalDamage(damage, false) : damage;
      });
      CombatFormula formula = this.formula;
      return var10000.map(formula::toMinecraftDamage).orElse(0.0D);
   }

   public DamageResult basicAttack(Player attacker, LivingEntity target) {
      CombatStats stats = (CombatStats)this.characterService.activeCharacter(attacker.getUniqueId()).map((character) -> this.statService.calculate(character, this.equipmentService.bonuses(attacker, character))).orElse(null);
      if (stats == null) {
         return this.empty(DamageKind.PHYSICAL);
      } else {
         double raw = stats.attackPower() * stats.basicAttackMultiplier();
         return this.resolveOutgoingDamage(attacker, target, raw, DamageKind.PHYSICAL, false);
      }
   }

   public double mitigateMinecraftDamage(Player defender, double damage, DamageKind kind) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(defender.getUniqueId()).orElse(null);
      if (character == null) {
         return damage;
      } else {
         CombatStats stats = this.statService.calculate(character, this.equipmentService.bonuses(defender, character));
         if (this.roll(stats.dodgeChance())) {
            return this.formula.dodgeRetainedDamage(damage);
         } else {
            double rating = kind == DamageKind.MAGIC ? stats.resistance() : stats.defense();
            return Math.max((double)0.0F, damage * stats.damageTakenMultiplier() * ((double)1.0F - this.formula.mitigation(rating, character.level())));
         }
      }
   }

   public DamageResult resolveOutgoingDamage(Player caster, LivingEntity target, double rpgDamage, DamageKind kind, boolean ability) {
      CombatStats casterStats = (CombatStats)this.characterService.activeCharacter(caster.getUniqueId()).map((character) -> this.statService.calculate(character, this.equipmentService.bonuses(caster, character))).orElse(null);
      double raw = this.varied(Math.max((double)0.0F, rpgDamage));
      boolean critical = casterStats != null && this.roll(casterStats.criticalChance());
      if (critical) {
         raw = this.formula.criticalDamage(raw, ability);
      }

      return this.mitigateTarget(target, raw, kind, critical);
   }

   public void dealAbilityDamage(Player caster, LivingEntity target, double rpgDamage, DamageKind kind) {
      if (target.isValid() && !target.isDead() && !(rpgDamage <= (double)0.0F)) {
         DamageResult result = this.resolveOutgoingDamage(caster, target, rpgDamage, kind, true);
         this.internalDepth.set((Integer)this.internalDepth.get() + 1);
         this.currentResult.set(result);
         boolean var11 = false;

         try {
            var11 = true;
            if (Bukkit.isOwnedByCurrentRegion(caster)) {
               target.damage(result.minecraftDamage(), caster);
               var11 = false;
            } else {
               target.damage(result.minecraftDamage());
               var11 = false;
            }
         } finally {
            if (var11) {
               int nextDepth = (Integer)this.internalDepth.get() - 1;
               if (nextDepth <= 0) {
                  this.internalDepth.remove();
                  this.currentResult.remove();
               } else {
                  this.internalDepth.set(nextDepth);
               }

            }
         }

         int nextDepth = (Integer)this.internalDepth.get() - 1;
         if (nextDepth <= 0) {
            this.internalDepth.remove();
            this.currentResult.remove();
         } else {
            this.internalDepth.set(nextDepth);
         }

      }
   }

   public boolean isInternalDamage() {
      return (Integer)this.internalDepth.get() > 0;
   }

   public Optional<DamageResult> currentResult() {
      return Optional.ofNullable((DamageResult)this.currentResult.get());
   }

   private DamageResult mitigateTarget(LivingEntity target, double rawDamage, DamageKind kind, boolean critical) {
      double adjusted = Math.max((double)0.0F, rawDamage);
      double mitigation = (double)0.0F;
      boolean dodged = false;
      if (target instanceof Player defender) {
         CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(defender.getUniqueId()).orElse(null);
         if (character != null) {
            CombatStats stats = this.statService.calculate(character, this.equipmentService.bonuses(defender, character));
            if (this.roll(stats.dodgeChance())) {
               dodged = true;
               adjusted = this.formula.dodgeRetainedDamage(adjusted);
            }

            double rating = kind == DamageKind.MAGIC ? stats.resistance() : stats.defense();
            mitigation = this.formula.mitigation(rating, character.level());
            adjusted = Math.max((double)0.0F, adjusted * stats.damageTakenMultiplier() * ((double)1.0F - mitigation));
         }
      }

      return new DamageResult(rawDamage, adjusted, this.formula.toMinecraftDamage(adjusted), mitigation, critical, dodged, kind);
   }

   private DamageResult empty(DamageKind kind) {
      return new DamageResult((double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F, false, false, kind);
   }

   private double varied(double damage) {
      double variance = this.formula.damageVariance();
      return !(damage <= (double)0.0F) && !(variance <= (double)0.0F) ? damage * ThreadLocalRandom.current().nextDouble((double)1.0F - variance, (double)1.0F + variance) : Math.max((double)0.0F, damage);
   }

   private boolean roll(double chance) {
      return chance > (double)0.0F && ThreadLocalRandom.current().nextDouble() < chance;
   }
}
