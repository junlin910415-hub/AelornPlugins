package tw.linsy.aelorn.rpgcore.combat;

import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.combat.CombatStats;
import tw.linsy.aelorn.rpgcore.domain.combat.DamageKind;
import tw.linsy.aelorn.rpgcore.domain.combat.DamageResult;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class DamagePipeline {
    private final CharacterService characterService;
    private final StatService statService;
    private final EquipmentService equipmentService;
    private final CombatFormula formula;
    private final ThreadLocal<Integer> internalDepth = ThreadLocal.withInitial(() -> 0);
    private final ThreadLocal<DamageResult> currentResult = new ThreadLocal<>();

    public DamagePipeline(
            CharacterService characterService,
            StatService statService,
            EquipmentService equipmentService,
            CombatFormula formula) {
        this.characterService = characterService;
        this.statService = statService;
        this.equipmentService = equipmentService;
        this.formula = formula;
    }

    public double basicAttackDamage(Player attacker) {
        return characterService.activeCharacter(attacker.getUniqueId())
                .map(character -> statService.calculate(character, equipmentService.bonuses(attacker, character)))
                .map(stats -> {
                    double damage = varied(stats.attackPower() * stats.basicAttackMultiplier());
                    return roll(stats.criticalChance()) ? formula.criticalDamage(damage, false) : damage;
                })
                .map(formula::toMinecraftDamage)
                .orElse(0.0);
    }

    public DamageResult basicAttack(Player attacker, LivingEntity target) {
        return resolveBasicAttack(attacker, target, DamageKind.PHYSICAL, 1.0);
    }

    public DamageResult resolveBasicAttack(
            Player attacker,
            LivingEntity target,
            DamageKind kind,
            double attackMultiplier) {
        CombatStats stats = characterService.activeCharacter(attacker.getUniqueId())
                .map(character -> statService.calculate(character, equipmentService.bonuses(attacker, character)))
                .orElse(null);
        if (stats == null || attackMultiplier <= 0.0 || !Double.isFinite(attackMultiplier)) {
            return empty(kind);
        }
        double raw = stats.attackPower() * stats.basicAttackMultiplier() * attackMultiplier;
        return resolveOutgoingDamage(attacker, target, raw, kind, false);
    }

    public DamageResult dealBasicAttackDamage(
            Player attacker,
            LivingEntity target,
            DamageKind kind,
            double attackMultiplier) {
        if (!validTarget(target) || !Bukkit.isOwnedByCurrentRegion((Entity) target)) {
            return empty(kind);
        }
        DamageResult result = resolveBasicAttack(attacker, target, kind, attackMultiplier);
        if (result.minecraftDamage() <= 0.0) {
            return result;
        }
        dealInternal(attacker, target, result);
        return result;
    }

    public double mitigateMinecraftDamage(Player defender, double damage, DamageKind kind) {
        CharacterProfile character = characterService.activeCharacter(defender.getUniqueId()).orElse(null);
        if (character == null) {
            return damage;
        }
        CombatStats stats = statService.calculate(character, equipmentService.bonuses(defender, character));
        if (roll(stats.dodgeChance())) {
            return formula.dodgeRetainedDamage(damage);
        }
        double rating = kind == DamageKind.MAGIC ? stats.resistance() : stats.defense();
        return Math.max(0.0,
                damage * stats.damageTakenMultiplier() * (1.0 - formula.mitigation(rating, character.level())));
    }

    public DamageResult resolveOutgoingDamage(
            Player caster,
            LivingEntity target,
            double rpgDamage,
            DamageKind kind,
            boolean ability) {
        CombatStats casterStats = characterService.activeCharacter(caster.getUniqueId())
                .map(character -> statService.calculate(character, equipmentService.bonuses(caster, character)))
                .orElse(null);
        double raw = varied(Math.max(0.0, rpgDamage));
        boolean critical = casterStats != null && roll(casterStats.criticalChance());
        if (critical) {
            raw = formula.criticalDamage(raw, ability);
        }
        return mitigateTarget(target, raw, kind, critical);
    }

    public void dealAbilityDamage(Player caster, LivingEntity target, double rpgDamage, DamageKind kind) {
        if (!validTarget(target) || rpgDamage <= 0.0 || !Bukkit.isOwnedByCurrentRegion((Entity) target)) {
            return;
        }
        DamageResult result = resolveOutgoingDamage(caster, target, rpgDamage, kind, true);
        dealInternal(caster, target, result);
    }

    public boolean isInternalDamage() {
        return internalDepth.get() > 0;
    }

    public Optional<DamageResult> currentResult() {
        return Optional.ofNullable(currentResult.get());
    }

    private void dealInternal(Player caster, LivingEntity target, DamageResult result) {
        int previousDepth = internalDepth.get();
        DamageResult previousResult = currentResult.get();
        internalDepth.set(previousDepth + 1);
        currentResult.set(result);
        try {
            if (Bukkit.isOwnedByCurrentRegion((Entity) caster)) {
                target.damage(result.minecraftDamage(), (Entity) caster);
            } else {
                target.damage(result.minecraftDamage());
            }
        } finally {
            if (previousDepth <= 0) {
                internalDepth.remove();
                currentResult.remove();
            } else {
                internalDepth.set(previousDepth);
                currentResult.set(previousResult);
            }
        }
    }

    private DamageResult mitigateTarget(LivingEntity target, double rawDamage, DamageKind kind, boolean critical) {
        double adjusted = Math.max(0.0, rawDamage);
        double mitigation = 0.0;
        boolean dodged = false;
        if (target instanceof Player defender) {
            CharacterProfile character = characterService.activeCharacter(defender.getUniqueId()).orElse(null);
            if (character != null) {
                CombatStats stats = statService.calculate(character, equipmentService.bonuses(defender, character));
                if (roll(stats.dodgeChance())) {
                    dodged = true;
                    adjusted = formula.dodgeRetainedDamage(adjusted);
                }
                double rating = kind == DamageKind.MAGIC ? stats.resistance() : stats.defense();
                mitigation = formula.mitigation(rating, character.level());
                adjusted = Math.max(0.0,
                        adjusted * stats.damageTakenMultiplier() * (1.0 - mitigation));
            }
        }
        return new DamageResult(
                rawDamage,
                adjusted,
                formula.toMinecraftDamage(adjusted),
                mitigation,
                critical,
                dodged,
                kind);
    }

    private boolean validTarget(LivingEntity target) {
        return target != null && target.isValid() && !target.isDead();
    }

    private DamageResult empty(DamageKind kind) {
        return new DamageResult(0.0, 0.0, 0.0, 0.0, false, false, kind);
    }

    private double varied(double damage) {
        double variance = formula.damageVariance();
        if (damage <= 0.0 || variance <= 0.0) {
            return Math.max(0.0, damage);
        }
        return damage * ThreadLocalRandom.current().nextDouble(1.0 - variance, 1.0 + variance);
    }

    private boolean roll(double chance) {
        return chance > 0.0 && ThreadLocalRandom.current().nextDouble() < chance;
    }
}
