package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.ability.AbilityInputService;
import com.xuzhihuanjing.rpgcore.combat.DamagePipeline;
import com.xuzhihuanjing.rpgcore.combat.TrainingWeaponService;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class AbilityInputListener implements Listener {
   private final AbilityInputService abilityInputService;
   private final CharacterService characterService;
   private final TrainingWeaponService trainingWeaponService;
   private final DamagePipeline damagePipeline;

   public AbilityInputListener(AbilityInputService abilityInputService, CharacterService characterService, TrainingWeaponService trainingWeaponService, DamagePipeline damagePipeline) {
      this.abilityInputService = abilityInputService;
      this.characterService = characterService;
      this.trainingWeaponService = trainingWeaponService;
      this.damagePipeline = damagePipeline;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onInteract(PlayerInteractEvent event) {
      if (event.getHand() == EquipmentSlot.HAND) {
         InputToken var10000;
         switch (event.getAction()) {
            case RIGHT_CLICK_AIR:
            case RIGHT_CLICK_BLOCK:
               var10000 = InputToken.RIGHT;
               break;
            case LEFT_CLICK_AIR:
            case LEFT_CLICK_BLOCK:
               var10000 = InputToken.LEFT;
               break;
            default:
               var10000 = null;
         }

         InputToken token = var10000;
         if (token != null && this.abilityInputService.input(event.getPlayer(), token)) {
            event.setCancelled(true);
         }

      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onDamageByEntity(EntityDamageByEntityEvent event) {
      if (!this.damagePipeline.isInternalDamage()) {
         Entity damager = event.getDamager();
         if (damager instanceof Player) {
            Player attacker = (Player)damager;
            if (Bukkit.isOwnedByCurrentRegion(attacker)) {
               CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(attacker.getUniqueId()).orElse(null);
               if (character != null && this.trainingWeaponService.isActiveWeapon(attacker, attacker.getInventory().getItemInMainHand(), character)) {
                  if (this.abilityInputService.hasPending(attacker)) {
                     if (this.abilityInputService.input(attacker, InputToken.LEFT)) {
                        event.setCancelled(true);
                        return;
                     }
                  } else {
                     Entity var5 = event.getEntity();
                     if (var5 instanceof LivingEntity) {
                        LivingEntity target = (LivingEntity)var5;
                        DamageResult result = this.damagePipeline.basicAttack(attacker, target);
                        if (result.minecraftDamage() > (double)0.0F) {
                           event.setDamage(result.minecraftDamage());
                        }
                     } else {
                        double damage = this.damagePipeline.basicAttackDamage(attacker);
                        if (damage > (double)0.0F) {
                           event.setDamage(damage);
                        }
                     }
                  }
               }
            }
         }

         if (!event.isCancelled()) {
            Entity victim = event.getEntity();
            if (victim instanceof Player) {
               Player defender = (Player)victim;
               event.setDamage(this.damagePipeline.mitigateMinecraftDamage(defender, event.getDamage(), DamageKind.PHYSICAL));
            }
         }

      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onOtherDamage(EntityDamageEvent event) {
      if (!(event instanceof EntityDamageByEntityEvent) && !this.damagePipeline.isInternalDamage()) {
         Entity var3 = event.getEntity();
         if (var3 instanceof Player) {
            Player defender = (Player)var3;
            event.setDamage(this.damagePipeline.mitigateMinecraftDamage(defender, event.getDamage(), DamageKind.PHYSICAL));
            return;
         }
      }

   }

   @EventHandler
   public void onDrop(PlayerDropItemEvent event) {
      if (this.trainingWeaponService.isTrainingWeapon(event.getItemDrop().getItemStack())) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onItemDamage(PlayerItemDamageEvent event) {
      if (this.trainingWeaponService.isTrainingWeapon(event.getItem())) {
         event.setCancelled(true);
      }

   }
}
