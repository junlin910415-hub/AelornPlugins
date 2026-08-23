package com.xuzhihuanjing.rpgcore.combat;

import com.xuzhihuanjing.rpgcore.config.CombatCoreSettings;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.integration.mythiccore.MythicCadenceBridge;
import com.xuzhihuanjing.rpgcore.integration.mythiccore.MythicCadenceBridge.Timeline;
import com.xuzhihuanjing.rpgcore.monster.MonsterRuntimeService;
import com.xuzhihuanjing.rpgcore.platform.RpgScheduler;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Folia-safe, server-authoritative primary attack runtime.
 *
 * One short entity-scheduler task exists only while a player is attacking. Repeated input can
 * reserve one next attack during late recovery, but can never create additional tasks.
 */
public final class WeaponCombatService {
    private final CombatCoreSettings settings;
    private final CharacterService characters;
    private final TrainingWeaponService weapons;
    private final DamagePipeline damagePipeline;
    private final MonsterRuntimeService monsters;
    private final CombatEffectsService effects;
    private final RpgScheduler scheduler;
    private final MythicCadenceBridge cadence;
    private final Map<UUID, ActiveAttack> activeAttacks = new ConcurrentHashMap<>();
    private final Map<UUID, ComboMemory> comboMemory = new ConcurrentHashMap<>();

    public WeaponCombatService(
            CombatCoreSettings settings,
            CharacterService characters,
            TrainingWeaponService weapons,
            DamagePipeline damagePipeline,
            MonsterRuntimeService monsters,
            CombatEffectsService effects,
            RpgScheduler scheduler,
            MythicCadenceBridge cadence) {
        this.settings = settings;
        this.characters = characters;
        this.weapons = weapons;
        this.damagePipeline = damagePipeline;
        this.monsters = monsters;
        this.effects = effects;
        this.scheduler = scheduler;
        this.cadence = cadence;
    }

    public boolean attack(Player player, LivingEntity explicitTarget) {
        if (!settings.enabled() || !Bukkit.isOwnedByCurrentRegion((Entity) player)) {
            return false;
        }
        AttackContext context = context(player);
        if (context == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        ActiveAttack active = activeAttacks.get(playerId);
        if (active != null) {
            if (active.cadenceProfileId.equals(context.weapon.cadenceProfileId())
                    && AttackCadenceStateMachine.acceptsBufferedInput(
                            active.timeline, active.elapsedTicks, active.queued)) {
                active.queued = true;
                active.queuedTarget = explicitTarget;
                effects.bufferedAttack(player, active.archetype);
            }
            return true;
        }

        startAttack(player, context, explicitTarget);
        return true;
    }

    public boolean ownsAttack(Player player) {
        if (!settings.enabled() || !Bukkit.isOwnedByCurrentRegion((Entity) player)) {
            return false;
        }
        CharacterProfile character = characters.activeCharacter(player.getUniqueId()).orElse(null);
        return character != null && weapons.isActiveWeapon(
                player, player.getInventory().getItemInMainHand(), character);
    }

    public void interruptOnDamage(Player player, double damage) {
        ActiveAttack active = activeAttacks.get(player.getUniqueId());
        if (active == null
                || AttackCadenceStateMachine.phase(active.timeline, active.elapsedTicks)
                        != AttackCadenceStateMachine.Phase.WINDUP
                || !active.timeline.interruptible()
                || !Double.isFinite(damage)
                || damage <= 0.0) {
            return;
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double healthCap = maxHealth == null ? Math.max(1.0, player.getHealth()) : maxHealth.getValue();
        double threshold = healthCap * active.timeline.interruptDamagePercent() / 100.0;
        if (damage >= threshold) {
            interrupt(player, active, true);
        }
    }

    public void clear(UUID playerId) {
        activeAttacks.remove(playerId);
        comboMemory.remove(playerId);
    }

    public void clearAll() {
        activeAttacks.clear();
        comboMemory.clear();
    }

    private AttackContext context(Player player) {
        CharacterProfile character = characters.activeCharacter(player.getUniqueId()).orElse(null);
        if (character == null) {
            return null;
        }
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        WeaponProfileResolver.ResolvedWeapon weapon =
                weapons.resolveActiveWeapon(player, heldItem, character).orElse(null);
        if (weapon == null) {
            return null;
        }
        return new AttackContext(heldItem.clone(), weapon);
    }

    private void startAttack(Player player, AttackContext context, LivingEntity preferredTarget) {
        WeaponArchetype archetype = context.weapon.archetype();
        CombatCoreSettings.AttackProfile geometry = settings.attack(archetype);
        String profileId = context.weapon.cadenceProfileId();
        double attackSpeedRating = cadence.attackSpeedRating(
                player, context.weapon.attackSpeedRating());
        Timeline first = cadence.timeline(
                profileId, 1, attackSpeedRating, geometry.cooldownMillis());
        ComboMemory previous = comboMemory.get(player.getUniqueId());
        int comboStep = previous == null ? 1 : AttackCadenceStateMachine.nextComboStep(
                profileId,
                previous.profileId,
                previous.step,
                System.nanoTime() - previous.completedAtNanos,
                first);
        Timeline timeline = comboStep == 1 ? first : cadence.timeline(
                profileId, comboStep, attackSpeedRating, geometry.cooldownMillis());

        ActiveAttack active = new ActiveAttack(
                archetype, profileId, context.item, timeline, preferredTarget);
        activeAttacks.put(player.getUniqueId(), active);
        effects.beginWindup(player, archetype, profileId, timeline.windupTicks());
        player.setCooldown(context.item.getType(), timeline.totalTicks());

        ScheduledTask task = scheduler.runEntityAtFixedRate(
                player,
                scheduled -> tickAttack(player, active, geometry, scheduled),
                () -> activeAttacks.remove(player.getUniqueId(), active),
                1L,
                1L);
        if (task == null) {
            activeAttacks.remove(player.getUniqueId(), active);
        }
    }

    private void tickAttack(
            Player player,
            ActiveAttack active,
            CombatCoreSettings.AttackProfile geometry,
            ScheduledTask task) {
        if (activeAttacks.get(player.getUniqueId()) != active || !player.isValid()) {
            task.cancel();
            return;
        }

        active.elapsedTicks++;
        if (active.elapsedTicks == active.timeline.activeStartTick()) {
            executeAttack(player, active, geometry);
            if (activeAttacks.get(player.getUniqueId()) != active) {
                task.cancel();
                return;
            }
        }
        if (active.elapsedTicks >= active.timeline.totalTicks()) {
            completeAttack(player, active, task);
        }
    }

    private void executeAttack(
            Player player,
            ActiveAttack active,
            CombatCoreSettings.AttackProfile geometry) {
        AttackContext current = context(player);
        if (current == null
                || current.weapon.archetype() != active.archetype
                || !current.weapon.cadenceProfileId().equals(active.cadenceProfileId)
                || !current.item.isSimilar(active.weaponSnapshot)) {
            interrupt(player, active, false);
            return;
        }

        effects.executeAttack(player, active.archetype, active.cadenceProfileId);
        double range = geometry.range() * active.timeline.rangeMultiplier();
        List<LivingEntity> targets = targets(
                player, active.preferredTarget, active.archetype, geometry, range);
        Location start = player.getEyeLocation().clone();
        Location end = targets.isEmpty()
                ? start.clone().add(start.getDirection().normalize().multiply(range))
                : targets.get(0).getLocation().add(0.0, targets.get(0).getHeight() * 0.62, 0.0);
        if (active.archetype == WeaponArchetype.RANGER_BOW
                || active.archetype == WeaponArchetype.ARCANE_FOCUS
                || active.archetype == WeaponArchetype.WARDEN_TOTEM) {
            effects.drawTrail(player, active.archetype, start, end);
        }

        for (int index = 0; index < targets.size(); index++) {
            LivingEntity target = targets.get(index);
            double secondaryMultiplier = index == 0 ? 1.0 : Math.pow(0.68, index);
            damagePipeline.dealBasicAttackDamage(
                    player,
                    target,
                    active.archetype.damageKind(),
                    geometry.damageMultiplier()
                            * active.timeline.damageMultiplier()
                            * secondaryMultiplier);
        }
    }

    private void completeAttack(Player player, ActiveAttack active, ScheduledTask task) {
        task.cancel();
        if (!activeAttacks.remove(player.getUniqueId(), active)) {
            return;
        }
        comboMemory.put(player.getUniqueId(), new ComboMemory(
                active.cadenceProfileId, active.timeline.comboStep(), System.nanoTime()));
        if (active.queued) {
            AttackContext next = context(player);
            if (next != null && next.weapon.cadenceProfileId().equals(active.cadenceProfileId)) {
                startAttack(player, next, active.queuedTarget);
            }
        }
    }

    private void interrupt(Player player, ActiveAttack active, boolean showEffect) {
        if (!activeAttacks.remove(player.getUniqueId(), active)) {
            return;
        }
        comboMemory.remove(player.getUniqueId());
        if (showEffect) {
            effects.interruptedAttack(player, active.archetype);
        }
    }

    private List<LivingEntity> targets(
            Player player,
            LivingEntity explicitTarget,
            WeaponArchetype archetype,
            CombatCoreSettings.AttackProfile profile,
            double range) {
        LinkedHashSet<LivingEntity> selected = new LinkedHashSet<>();
        if (eligible(player, explicitTarget) && withinRange(player, explicitTarget, range)) {
            selected.add(explicitTarget);
        } else {
            traceTarget(player, profile, range).ifPresent(selected::add);
        }

        if (archetype == WeaponArchetype.VANGUARD_BLADE && profile.maximumTargets() > 1) {
            selected.addAll(arcTargets(player, profile, range, selected));
        }
        return selected.stream().limit(profile.maximumTargets()).toList();
    }

    private java.util.Optional<LivingEntity> traceTarget(
            Player player,
            CombatCoreSettings.AttackProfile profile,
            double range) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        RayTraceResult result = player.getWorld().rayTrace(
                start,
                direction,
                range,
                FluidCollisionMode.NEVER,
                true,
                profile.hitRadius(),
                entity -> entity instanceof LivingEntity living && eligible(player, living));
        if (result == null || !(result.getHitEntity() instanceof LivingEntity target)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(target);
    }

    private List<LivingEntity> arcTargets(
            Player player,
            CombatCoreSettings.AttackProfile profile,
            double range,
            LinkedHashSet<LivingEntity> excluded) {
        Vector facing = player.getEyeLocation().getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) {
            return List.of();
        }
        facing.normalize();
        double minimumDot = Math.cos(Math.toRadians(profile.arcDegrees() * 0.5));
        ArrayList<LivingEntity> matches = new ArrayList<>();
        for (Entity nearby : player.getNearbyEntities(range, 2.75, range)) {
            if (!(nearby instanceof LivingEntity target)
                    || excluded.contains(target)
                    || !eligible(player, target)) {
                continue;
            }
            Vector offset = target.getLocation().toVector()
                    .subtract(player.getLocation().toVector()).setY(0.0);
            if (offset.lengthSquared() < 1.0E-6 || offset.normalize().dot(facing) < minimumDot) {
                continue;
            }
            matches.add(target);
        }
        matches.sort(Comparator.comparingDouble(
                target -> target.getLocation().distanceSquared(player.getLocation())));
        return matches;
    }

    private boolean eligible(Player player, LivingEntity target) {
        return target != null
                && target != player
                && Bukkit.isOwnedByCurrentRegion((Entity) target)
                && target.isValid()
                && !target.isDead()
                && target.getWorld() == player.getWorld()
                && monsters.isManaged((Entity) target);
    }

    private boolean withinRange(Player player, LivingEntity target, double range) {
        return player.getEyeLocation().distanceSquared(target.getEyeLocation()) <= range * range;
    }

    private record AttackContext(
            ItemStack item,
            WeaponProfileResolver.ResolvedWeapon weapon) {
    }

    private record ComboMemory(String profileId, int step, long completedAtNanos) {
    }

    private static final class ActiveAttack {
        private final WeaponArchetype archetype;
        private final String cadenceProfileId;
        private final ItemStack weaponSnapshot;
        private final Timeline timeline;
        private final LivingEntity preferredTarget;
        private int elapsedTicks;
        private boolean queued;
        private LivingEntity queuedTarget;

        private ActiveAttack(
                WeaponArchetype archetype,
                String cadenceProfileId,
                ItemStack weaponSnapshot,
                Timeline timeline,
                LivingEntity preferredTarget) {
            this.archetype = archetype;
            this.cadenceProfileId = cadenceProfileId;
            this.weaponSnapshot = weaponSnapshot;
            this.timeline = timeline;
            this.preferredTarget = preferredTarget;
        }
    }
}
