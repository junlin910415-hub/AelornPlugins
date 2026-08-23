package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.ability.AbilityInputService;
import com.xuzhihuanjing.rpgcore.combat.DamagePipeline;
import com.xuzhihuanjing.rpgcore.combat.TrainingWeaponService;
import com.xuzhihuanjing.rpgcore.combat.WeaponCombatService;
import com.xuzhihuanjing.rpgcore.domain.ability.InputToken;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.combat.DamageKind;
import com.xuzhihuanjing.rpgcore.domain.combat.DamageResult;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class AbilityInputListener implements Listener {
    private final AbilityInputService abilityInputService;
    private final CharacterService characterService;
    private final TrainingWeaponService trainingWeaponService;
    private final DamagePipeline damagePipeline;
    private final WeaponCombatService weaponCombat;

    public AbilityInputListener(
            AbilityInputService abilityInputService,
            CharacterService characterService,
            TrainingWeaponService trainingWeaponService,
            DamagePipeline damagePipeline) {
        this(abilityInputService, characterService, trainingWeaponService, damagePipeline, null);
    }

    public AbilityInputListener(
            AbilityInputService abilityInputService,
            CharacterService characterService,
            TrainingWeaponService trainingWeaponService,
            DamagePipeline damagePipeline,
            WeaponCombatService weaponCombat) {
        this.abilityInputService = abilityInputService;
        this.characterService = characterService;
        this.trainingWeaponService = trainingWeaponService;
        this.damagePipeline = damagePipeline;
        this.weaponCombat = weaponCombat;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        InputToken token = switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> InputToken.RIGHT;
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> InputToken.LEFT;
            default -> null;
        };
        if (token == null) {
            return;
        }
        if (abilityInputService.input(event.getPlayer(), token)) {
            event.setCancelled(true);
            return;
        }
        if (token == InputToken.LEFT && weaponCombat != null && weaponCombat.attack(event.getPlayer(), null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING
                && weaponCombat != null
                && weaponCombat.ownsAttack(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (damagePipeline.isInternalDamage()) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Player attacker && Bukkit.isOwnedByCurrentRegion((Entity) attacker)) {
            CharacterProfile character = characterService.activeCharacter(attacker.getUniqueId()).orElse(null);
            if (character != null && trainingWeaponService.isActiveWeapon(
                    attacker, attacker.getInventory().getItemInMainHand(), character)) {
                if (abilityInputService.hasPending(attacker) && abilityInputService.input(attacker, InputToken.LEFT)) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getEntity() instanceof LivingEntity target
                        && weaponCombat != null
                        && weaponCombat.attack(attacker, target)) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getEntity() instanceof LivingEntity target) {
                    DamageResult result = damagePipeline.basicAttack(attacker, target);
                    if (result.minecraftDamage() > 0.0) {
                        event.setDamage(result.minecraftDamage());
                    }
                } else {
                    double damage = damagePipeline.basicAttackDamage(attacker);
                    if (damage > 0.0) {
                        event.setDamage(damage);
                    }
                }
            }
        }

        if (!event.isCancelled() && event.getEntity() instanceof Player defender) {
            double damage = damagePipeline.mitigateMinecraftDamage(
                    defender, event.getDamage(), DamageKind.PHYSICAL);
            event.setDamage(damage);
            if (weaponCombat != null) {
                weaponCombat.interruptOnDamage(defender, damage);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOtherDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent
                || damagePipeline.isInternalDamage()
                || !(event.getEntity() instanceof Player defender)) {
            return;
        }
        double damage = damagePipeline.mitigateMinecraftDamage(
                defender, event.getDamage(), DamageKind.PHYSICAL);
        event.setDamage(damage);
        if (weaponCombat != null) {
            weaponCombat.interruptOnDamage(defender, damage);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (trainingWeaponService.isTrainingWeapon(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (trainingWeaponService.isTrainingWeapon(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (weaponCombat != null) {
            weaponCombat.clear(event.getPlayer().getUniqueId());
        }
    }
}
