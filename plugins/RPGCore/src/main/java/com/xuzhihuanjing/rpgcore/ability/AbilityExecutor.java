package com.xuzhihuanjing.rpgcore.ability;

import com.xuzhihuanjing.rpgcore.combat.CombatFormula;
import com.xuzhihuanjing.rpgcore.combat.DamagePipeline;
import com.xuzhihuanjing.rpgcore.combat.StatService;
import com.xuzhihuanjing.rpgcore.domain.ability.AbilityDefinition;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.combat.CombatStats;
import com.xuzhihuanjing.rpgcore.domain.combat.DamageKind;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentService;
import com.xuzhihuanjing.rpgcore.platform.RpgScheduler;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class AbilityExecutor {
   private final StatService statService;
   private final CombatFormula formula;
   private final DamagePipeline damagePipeline;
   private final EquipmentService equipmentService;
   private final AbilityModifierService modifierService;
   private final RpgScheduler scheduler;

   public AbilityExecutor(StatService statService, CombatFormula formula, DamagePipeline damagePipeline, EquipmentService equipmentService, AbilityModifierService modifierService, RpgScheduler scheduler) {
      this.statService = statService;
      this.formula = formula;
      this.damagePipeline = damagePipeline;
      this.equipmentService = equipmentService;
      this.modifierService = modifierService;
      this.scheduler = scheduler;
   }

   public void execute(Player player, CharacterProfile character, AbilityDefinition ability) {
      CombatStats stats = this.statService.calculate(character, this.equipmentService.bonuses(player, character));
      double power = this.formula.abilityPower(stats, ability.coefficient(), ability.flatPower()) * ((double)1.0F + this.modifierService.modifiers(character).powerBonus());
      switch (ability.effectType()) {
         case DASH_STRIKE -> this.dashStrike(player, ability, power);
         case WAR_CRY -> this.warCry(player, ability, power);
         case GROUND_SLAM -> this.groundSlam(player, ability, power);
         case BULWARK -> this.bulwark(player, ability);
         case BLINK -> this.blink(player, ability);
         case ARCANE_BOLT -> this.arcaneBolt(player, ability, power);
         case RESTORE -> this.restore(player, stats, ability);
         case FROST_NOVA -> this.frostNova(player, ability, power);
         case BACKSTEP -> this.backstep(player);
         case RAPID_VOLLEY -> this.rapidVolley(player, ability, power);
         case EXPLOSIVE_SHOT -> this.explosiveShot(player, ability, power);
         case ARROW_GUARD -> this.arrowGuard(player, ability);
         case SHADOW_DASH -> this.shadowDash(player, ability, power);
         case WHIRLWIND -> this.whirlwind(player, ability, power);
         case MULTI_HIT -> this.multiHit(player, ability, power);
         case SMOKE_FIELD -> this.smokeField(player, ability, power);
         case WARDEN_FIELD -> this.wardenField(player, ability, power);
         case RESONANCE_WAVE -> this.resonanceWave(player, ability, power);
         case ANCHOR_LEAP -> this.anchorLeap(player, ability, power);
         case RENEWAL_PULSE -> this.renewalPulse(player, stats, ability);
      }

   }

   private void dashStrike(Player player, AbilityDefinition ability, double power) {
      Vector direction = player.getLocation().getDirection().normalize();
      player.setVelocity(direction.multiply((double)1.25F).setY((double)0.25F));
      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
      player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 10, 0.8, (double)0.5F, 0.8);
      this.scheduler.runEntityLater(player, () -> this.damageNearby(player, ability.radius(), power, DamageKind.PHYSICAL, true), () -> {
      }, 5L);
   }

   private void warCry(Player player, AbilityDefinition ability, double power) {
      Location origin = player.getLocation().clone();
      player.getWorld().playSound(origin, Sound.ENTITY_RAVAGER_ROAR, 0.8F, 1.2F);
      player.getWorld().spawnParticle(Particle.GUST, origin.clone().add((double)0.0F, (double)1.0F, (double)0.0F), 24, (double)2.0F, 0.8, (double)2.0F, 0.05);
      this.affectNearby(player, ability.radius(), (target) -> {
         this.damagePipeline.dealAbilityDamage(player, target, power, DamageKind.PHYSICAL);
         Vector away = target.getLocation().toVector().subtract(origin.toVector());
         if (away.lengthSquared() > 1.0E-4) {
            target.setVelocity(away.normalize().multiply(0.85).setY((double)0.25F));
         }

         target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ability.durationTicks(), 0, false, true, true));
      });
   }

   private void groundSlam(Player player, AbilityDefinition ability, double power) {
      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 0.65F);
      player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 4, (double)1.0F, 0.2, (double)1.0F);
      this.affectNearby(player, ability.radius(), (target) -> {
         this.damagePipeline.dealAbilityDamage(player, target, power, DamageKind.PHYSICAL);
         target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ability.durationTicks(), 1, false, true, true));
      });
   }

   private void bulwark(Player player, AbilityDefinition ability) {
      player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ability.durationTicks(), 1, false, true, true));
      player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ability.durationTicks(), 1, false, true, true));
      player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.8F);
      player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 40, 0.8, (double)1.0F, 0.8, 0.1);
   }

   private void blink(Player player, AbilityDefinition ability) {
      this.teleportSafely(player, ability, () -> {
      });
   }

   private void teleportSafely(Player player, AbilityDefinition ability, Runnable onArrival) {
      Location start = player.getLocation();
      Vector direction = start.getDirection().normalize();
      Location destination = start.clone();

      for(double distance = (double)0.5F; distance <= ability.range(); distance += (double)0.5F) {
         Location candidate = start.clone().add(direction.clone().multiply(distance));
         if (!candidate.getBlock().isPassable() || !candidate.clone().add((double)0.0F, (double)1.0F, (double)0.0F).getBlock().isPassable()) {
            break;
         }

         destination = candidate;
      }

      player.getWorld().spawnParticle(Particle.PORTAL, start.clone().add((double)0.0F, (double)1.0F, (double)0.0F), 35, (double)0.5F, 0.8, (double)0.5F, (double)0.25F);
      Location target = destination.clone();
      player.teleportAsync(target).thenAccept((success) -> {
         if (success) {
            this.scheduler.executeEntity(player, () -> {
               Location arrival = player.getLocation();
               player.getWorld().spawnParticle(Particle.PORTAL, arrival.clone().add((double)0.0F, (double)1.0F, (double)0.0F), 35, (double)0.5F, 0.8, (double)0.5F, (double)0.25F);
               player.getWorld().playSound(arrival, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8F, 1.3F);
               onArrival.run();
            }, () -> {
            });
         }
      });
   }

   private void arcaneBolt(Player player, AbilityDefinition ability, double power) {
      Location start = player.getEyeLocation();
      Vector direction = start.getDirection().normalize();
      RayTraceResult blocks = player.getWorld().rayTraceBlocks(start, direction, ability.range(), FluidCollisionMode.NEVER, true);
      double range = blocks == null ? ability.range() : blocks.getHitPosition().distance(start.toVector());
      RayTraceResult entities = player.getWorld().rayTraceEntities(start, direction, range, ability.radius(), (entity) -> entity instanceof LivingEntity && entity != player && this.isHostile(player, entity));

      for(double distance = (double)0.0F; distance <= range; distance += (double)0.5F) {
         Location point = start.clone().add(direction.clone().multiply(distance));
         player.getWorld().spawnParticle(Particle.END_ROD, point, 1, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F);
      }

      if (entities != null) {
         Entity var12 = entities.getHitEntity();
         if (var12 instanceof LivingEntity) {
            LivingEntity target = (LivingEntity)var12;
            this.affectTarget(player, target, (owned) -> this.damagePipeline.dealAbilityDamage(player, owned, power, DamageKind.MAGIC));
         }
      }

      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.7F, 1.6F);
   }

   private void restore(Player player, CombatStats stats, AbilityDefinition ability) {
      double rpgHealing = stats.attackPower() * 0.6 + ability.flatPower();
      this.heal(player, this.formula.toMinecraftHealth(rpgHealing));
      player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 10, 0.7, 0.7, 0.7, 0.05);
      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.6F);
   }

   private void frostNova(Player player, AbilityDefinition ability, double power) {
      player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add((double)0.0F, (double)0.5F, (double)0.0F), 80, (double)2.5F, (double)0.5F, (double)2.5F, 0.08);
      player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8F, 0.75F);
      this.affectNearby(player, ability.radius(), (target) -> {
         this.damagePipeline.dealAbilityDamage(player, target, power, DamageKind.MAGIC);
         target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ability.durationTicks(), 2, false, true, true));
      });
   }

   private void backstep(Player player) {
      Vector direction = player.getLocation().getDirection().normalize().multiply(-1.15).setY(0.42);
      player.setVelocity(direction);
      player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 18, (double)0.5F, 0.2, (double)0.5F, 0.04);
      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8F, 1.5F);
   }

   private void rapidVolley(Player player, AbilityDefinition ability, double power) {
      for(int shot = 0; shot < 5; ++shot) {
         int delay = Math.max(1, shot * 2);
         this.scheduler.runEntityLater(player, () -> {
            this.rayDamage(player, ability.range(), ability.radius(), power / (double)5.0F, DamageKind.PHYSICAL, Particle.CRIT);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.45F, 1.35F);
         }, () -> {
         }, (long)delay);
      }

   }

   private void explosiveShot(Player player, AbilityDefinition ability, double power) {
      Location impact = this.rayEnd(player, ability.range());
      player.getWorld().spawnParticle(Particle.EXPLOSION, impact, 3, 0.35, 0.35, 0.35);
      player.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.65F, 1.3F);
      this.damageAt(player, impact, ability.radius(), power, DamageKind.PHYSICAL, false);
   }

   private void arrowGuard(Player player, AbilityDefinition ability) {
      player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ability.durationTicks(), 0, false, true, true));
      player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ability.durationTicks(), 0, false, true, true));
      player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 36, (double)1.0F, (double)1.0F, (double)1.0F, 0.08);
      player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8F, 1.4F);
   }

   private void shadowDash(Player player, AbilityDefinition ability, double power) {
      this.teleportSafely(player, ability, () -> {
         player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 8, 0.8, (double)0.5F, 0.8);
         this.damageNearby(player, ability.radius(), power, DamageKind.PHYSICAL, false);
      });
   }

   private void whirlwind(Player player, AbilityDefinition ability, double power) {
      player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 18, (double)1.5F, 0.7, (double)1.5F);
      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 1.25F);
      this.damageNearby(player, ability.radius(), power, DamageKind.PHYSICAL, true);
   }

   private void multiHit(Player player, AbilityDefinition ability, double power) {
      for(int strike = 0; strike < 5; ++strike) {
         int delay = Math.max(1, strike * 2);
         this.scheduler.runEntityLater(player, () -> {
            this.damageNearby(player, ability.radius(), power / (double)5.0F, DamageKind.PHYSICAL, false);
            player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 12, (double)1.0F, 0.7, (double)1.0F, 0.08);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.45F, 1.5F);
         }, () -> {
         }, (long)delay);
      }

   }

   private void smokeField(Player player, AbilityDefinition ability, double power) {
      player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, ability.durationTicks(), 0, false, false, true));
      player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ability.durationTicks(), 1, false, true, true));
      player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.getLocation().add((double)0.0F, (double)0.5F, (double)0.0F), 45, 1.8, (double)0.5F, 1.8, 0.02);
      player.getWorld().playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8F, 0.8F);
      this.affectNearby(player, ability.radius(), (target) -> {
         this.damagePipeline.dealAbilityDamage(player, target, power, DamageKind.MAGIC);
         target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ability.durationTicks(), 0, false, true, true));
      });
   }

   private void wardenField(Player player, AbilityDefinition ability, double power) {
      Location center = player.getLocation().clone();
      int pulses = Math.max(1, ability.durationTicks() / 20);
      AtomicInteger remaining = new AtomicInteger(pulses);
      this.scheduler.runRegionAtFixedRate(center, (task) -> {
         int pulse = remaining.getAndDecrement();
         if (pulse <= 0) {
            this.scheduler.cancel(task);
         } else {
            center.getWorld().spawnParticle(Particle.ENCHANT, center.clone().add((double)0.0F, 0.3, (double)0.0F), 30, ability.radius() * 0.55, 0.2, ability.radius() * 0.55, 0.03);
            this.damageAt(player, center, ability.radius(), power / (double)pulses, DamageKind.MAGIC, false);
            center.getWorld().playSound(center, Sound.BLOCK_BEACON_AMBIENT, 0.35F, 1.3F);
            if (pulse == 1) {
               this.scheduler.cancel(task);
            }

         }
      }, 1L, 20L);
   }

   private void resonanceWave(Player player, AbilityDefinition ability, double power) {
      Location origin = player.getLocation().clone();
      player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, origin.clone().add((double)0.0F, 0.8, (double)0.0F), 65, ability.radius() * 0.55, 0.7, ability.radius() * 0.55, 0.12);
      player.getWorld().playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8F, 0.9F);
      this.affectNearby(player, ability.radius(), (target) -> {
         this.damagePipeline.dealAbilityDamage(player, target, power, DamageKind.MAGIC);
         Vector away = target.getLocation().toVector().subtract(origin.toVector());
         if (away.lengthSquared() > 1.0E-4) {
            target.setVelocity(away.normalize().multiply(0.55).setY(0.2));
         }

      });
   }

   private void anchorLeap(Player player, AbilityDefinition ability, double power) {
      this.damageNearby(player, ability.radius(), power, DamageKind.MAGIC, true);
      Vector direction = player.getLocation().getDirection().normalize().multiply(0.8).setY(0.85);
      player.setVelocity(direction);
      player.getWorld().spawnParticle(Particle.GUST, player.getLocation(), 25, 1.2, 0.3, 1.2, 0.05);
      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8F, 1.1F);
   }

   private void renewalPulse(Player player, CombatStats stats, AbilityDefinition ability) {
      double rpgHealing = stats.attackPower() * ability.coefficient() + ability.flatPower();
      double healing = this.formula.toMinecraftHealth(rpgHealing);
      this.heal(player, healing);

      for(Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), ability.radius(), ability.radius(), ability.radius())) {
         if (entity instanceof Player ally) {
            if (ally != player && this.sameTeam(player, ally)) {
               this.affectTarget(player, ally, (owned) -> this.heal(ally, healing * (double)0.75F));
            }
         }
      }

      player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 35, 1.4, 0.8, 1.4, 0.08);
      player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.5F);
   }

   private void rayDamage(Player player, double range, double radius, double power, DamageKind kind, Particle particle) {
      Location start = player.getEyeLocation();
      Vector direction = start.getDirection().normalize();
      RayTraceResult result = player.getWorld().rayTraceEntities(start, direction, range, radius, (entity) -> entity instanceof LivingEntity && entity != player && this.isHostile(player, entity));

      for(double distance = (double)0.0F; distance <= range; distance += (double)0.75F) {
         player.getWorld().spawnParticle(particle, start.clone().add(direction.clone().multiply(distance)), 1, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F);
      }

      if (result != null) {
         Entity var14 = result.getHitEntity();
         if (var14 instanceof LivingEntity) {
            LivingEntity target = (LivingEntity)var14;
            this.affectTarget(player, target, (owned) -> this.damagePipeline.dealAbilityDamage(player, owned, power, kind));
         }
      }

   }

   private Location rayEnd(Player player, double range) {
      Location start = player.getEyeLocation();
      Vector direction = start.getDirection().normalize();
      RayTraceResult result = player.getWorld().rayTrace(start, direction, range, FluidCollisionMode.NEVER, true, 0.4, (entity) -> entity instanceof LivingEntity && entity != player && this.isHostile(player, entity));
      return result == null ? start.clone().add(direction.multiply(range)) : result.getHitPosition().toLocation(player.getWorld());
   }

   private void damageAt(Player player, Location center, double radius, double power, DamageKind kind, boolean knockback) {
      for(Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
         if (entity instanceof LivingEntity target) {
            if (target != player && this.isHostile(player, target)) {
               this.affectTarget(player, target, (owned) -> {
                  this.damagePipeline.dealAbilityDamage(player, owned, power, kind);
                  if (knockback) {
                     Vector away = owned.getLocation().toVector().subtract(center.toVector());
                     if (away.lengthSquared() > 1.0E-4) {
                        owned.setVelocity(away.normalize().multiply(0.45).setY(0.2));
                     }
                  }

               });
            }
         }
      }

   }

   private void heal(Player player, double amount) {
      AttributeInstance maximumHealth = player.getAttribute(Attribute.MAX_HEALTH);
      double limit = maximumHealth == null ? (double)20.0F : maximumHealth.getValue();
      player.setHealth(Math.min(limit, player.getHealth() + amount));
   }

   private void damageNearby(Player player, double radius, double power, DamageKind kind, boolean knockback) {
      Location origin = player.getLocation().clone();
      this.affectNearby(player, radius, (target) -> {
         this.damagePipeline.dealAbilityDamage(player, target, power, kind);
         if (knockback) {
            Vector away = target.getLocation().toVector().subtract(origin.toVector());
            if (away.lengthSquared() > 1.0E-4) {
               target.setVelocity(away.normalize().multiply(0.4).setY(0.2));
            }
         }

      });
   }

   private void affectNearby(Player player, double radius, Consumer<LivingEntity> action) {
      for(LivingEntity target : this.nearbyTargets(player, radius)) {
         this.affectTarget(player, target, action);
      }

   }

   private void affectTarget(Player source, LivingEntity target, Consumer<LivingEntity> action) {
      this.scheduler.executeEntity(target, () -> {
         if (target.isValid() && !target.isDead()) {
            action.accept(target);
         }

      }, () -> {
      });
   }

   private Collection<LivingEntity> nearbyTargets(Player player, double radius) {
      var var10000 = player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius).stream().filter((entity) -> entity != player);
      Objects.requireNonNull(LivingEntity.class);
      var10000 = var10000.filter(LivingEntity.class::isInstance).filter((entity) -> this.isHostile(player, entity));
      Objects.requireNonNull(LivingEntity.class);
      return var10000.map(LivingEntity.class::cast).toList();
   }

   private boolean isHostile(Player source, Entity target) {
      boolean var10000;
      if (target instanceof Player player) {
         if (this.sameTeam(source, player)) {
            var10000 = false;
            return var10000;
         }
      }

      var10000 = true;
      return var10000;
   }

   private boolean sameTeam(Player first, Player second) {
      if (this.scheduler.isFolia()) {
         return false;
      } else {
         Team firstTeam = first.getScoreboard().getEntryTeam(first.getName());
         Team secondTeam = second.getScoreboard().getEntryTeam(second.getName());
         return firstTeam != null && firstTeam.equals(secondTeam);
      }
   }
}
