package tw.linsy.aelorn.rpgcore.monster;

import tw.linsy.aelorn.rpgcore.config.MonsterRegistry;
import tw.linsy.aelorn.rpgcore.domain.monster.MonsterDefinition;
import tw.linsy.aelorn.rpgcore.domain.monster.MonsterRank;
import tw.linsy.aelorn.rpgcore.domain.monster.ScaledMonsterStats;
import tw.linsy.aelorn.rpgcore.platform.RpgScheduler;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class MonsterRuntimeService {
   private final MonsterRegistry registry;
   private final MonsterScalingFormula scaling;
   private final CharacterService characterService;
   private final RpgScheduler scheduler;
   private final MythicMobsBridge mythicMobs;
   private final ModelEngineBridge modelEngine;
   private final NamespacedKey monsterIdKey;
   private final NamespacedKey monsterLevelKey;
   private final NamespacedKey projectileDamageKey;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();
   private final Map<UUID, ScheduledTask> behaviorTasks = new ConcurrentHashMap();
   private final ThreadLocal<Double> activeAbilityDamage = new ThreadLocal();

   public MonsterRuntimeService(Plugin plugin, MonsterRegistry registry, MonsterScalingFormula scaling, CharacterService characterService, RpgScheduler scheduler, MythicMobsBridge mythicMobs, ModelEngineBridge modelEngine) {
      this.registry = registry;
      this.scaling = scaling;
      this.characterService = characterService;
      this.scheduler = scheduler;
      this.mythicMobs = mythicMobs;
      this.modelEngine = modelEngine;
      this.monsterIdKey = new NamespacedKey(plugin, "monster_id");
      this.monsterLevelKey = new NamespacedKey(plugin, "monster_level");
      this.projectileDamageKey = new NamespacedKey(plugin, "monster_projectile_damage");
   }

   public LivingEntity spawn(Location location, MonsterDefinition definition, int requestedLevel) {
      ScaledMonsterStats stats = this.scaling.scale(definition, requestedLevel);
      LivingEntity mythicEntity = this.mythicMobs.available() ? (LivingEntity)this.mythicMobs.spawn(definition.mythicMobId(), location, stats.level()).orElse(null) : null;
      if (mythicEntity != null) {
         this.markManaged(mythicEntity, definition, stats);
         this.applyCombatAttributes(mythicEntity, definition, stats);
         this.attachModel(mythicEntity, definition);
         return mythicEntity;
      } else {
         Entity entity = location.getWorld().spawnEntity(location, EntityType.valueOf(definition.entityType()));
         if (entity instanceof Mob) {
            Mob mob = (Mob)entity;
            this.markManaged(mob, definition, stats);
            mob.setCanPickupItems(false);
            mob.setRemoveWhenFarAway(definition.rank() != MonsterRank.BOSS);
            mob.setPersistent(false);
            this.applyCombatAttributes(mob, definition, stats);
            this.attachModel(mob, definition);
            this.startBehavior(mob, definition);
            return mob;
         } else {
            entity.remove();
            throw new IllegalStateException("Configured monster type is not a Mob: " + definition.entityType());
         }
      }
   }

   public boolean isManaged(Entity entity) {
      return entity.getPersistentDataContainer().has(this.monsterIdKey, PersistentDataType.STRING);
   }

   public Optional<MonsterDefinition> definition(Entity entity) {
      String id = (String)entity.getPersistentDataContainer().get(this.monsterIdKey, PersistentDataType.STRING);
      return id == null ? Optional.empty() : this.registry.find(id);
   }

   public Optional<ScaledMonsterStats> stats(Entity entity) {
      MonsterDefinition definition = (MonsterDefinition)this.definition(entity).orElse(null);
      Integer level = (Integer)entity.getPersistentDataContainer().get(this.monsterLevelKey, PersistentDataType.INTEGER);
      return definition != null && level != null ? Optional.of(this.scaling.scale(definition, level)) : Optional.empty();
   }

   public Optional<Double> projectileDamage(Projectile projectile) {
      return Optional.ofNullable((Double)projectile.getPersistentDataContainer().get(this.projectileDamageKey, PersistentDataType.DOUBLE));
   }

   public Optional<Double> activeAbilityDamage() {
      return Optional.ofNullable((Double)this.activeAbilityDamage.get());
   }

   public void animateAttack(LivingEntity entity) {
      this.definition(entity).ifPresent((definition) -> this.modelEngine.play(entity, definition.modelId(), definition.attackAnimation()));
   }

   public void animateHurt(LivingEntity entity) {
      this.modelEngine.markHurt(entity);
      this.definition(entity).ifPresent((definition) -> this.modelEngine.play(entity, definition.modelId(), definition.hurtAnimation()));
   }

   public void animateDeath(LivingEntity entity) {
      this.definition(entity).ifPresent((definition) -> this.modelEngine.play(entity, definition.modelId(), definition.deathAnimation()));
   }

   public void stop(Entity entity) {
      this.scheduler.cancel((ScheduledTask)this.behaviorTasks.remove(entity.getUniqueId()));
   }

   public void remove(Entity entity) {
      this.stop(entity);
      this.scheduler.executeEntity(entity, () -> {
         if (entity.isValid()) {
            entity.remove();
         }

      }, () -> {
      });
   }

   public void shutdown() {
      var var10000 = this.behaviorTasks.values();
      RpgScheduler var10001 = this.scheduler;
      Objects.requireNonNull(var10001);
      var10000.forEach(var10001::cancel);
      this.behaviorTasks.clear();
      this.activeAbilityDamage.remove();
   }

   private Component displayName(MonsterDefinition definition, int level) {
      return this.miniMessage.deserialize(definition.displayName()).append(Component.space()).append(Component.text("[", NamedTextColor.DARK_GRAY)).append(Component.text("Lv " + level, NamedTextColor.WHITE)).append(Component.text("]", NamedTextColor.DARK_GRAY));
   }

   private void markManaged(LivingEntity entity, MonsterDefinition definition, ScaledMonsterStats stats) {
      entity.getPersistentDataContainer().set(this.monsterIdKey, PersistentDataType.STRING, definition.id());
      entity.getPersistentDataContainer().set(this.monsterLevelKey, PersistentDataType.INTEGER, stats.level());
      entity.customName(this.displayName(definition, stats.level()));
      entity.setCustomNameVisible(true);
   }

   private void applyCombatAttributes(LivingEntity entity, MonsterDefinition definition, ScaledMonsterStats stats) {
      this.applyAttribute(entity, Attribute.MAX_HEALTH, Math.min((double)2048.0F, stats.maximumHealth()));
      if (entity instanceof Mob mob) {
         this.applyAttribute(mob, Attribute.MOVEMENT_SPEED, definition.movementSpeed());
         this.applyAttribute(mob, Attribute.FOLLOW_RANGE, definition.followRange());
      }

      Attribute var10002 = Attribute.KNOCKBACK_RESISTANCE;
      double var10003;
      switch (definition.rank()) {
         case COMMON -> var10003 = (double)0.0F;
         case VETERAN -> var10003 = 0.1;
         case ELITE -> var10003 = (double)0.25F;
         case BOSS -> var10003 = 0.45;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      this.applyAttribute(entity, var10002, var10003);
      entity.setHealth(Math.min(stats.maximumHealth(), this.maximumHealth(entity)));
   }

   private void startBehavior(Mob mob, MonsterDefinition definition) {
      ScheduledTask task = this.scheduler.runEntityAtFixedRate(mob, (ignored) -> this.useAbility(mob, definition), () -> this.behaviorTasks.remove(mob.getUniqueId()), definition.abilityCooldownTicks(), definition.abilityCooldownTicks());
      if (task != null) {
         this.behaviorTasks.put(mob.getUniqueId(), task);
      }

   }

   private void useAbility(Mob mob, MonsterDefinition definition) {
      if (mob.isValid() && !mob.isDead()) {
         Player target = (Player)this.target(mob, definition.followRange()).orElse(null);
         if (target != null) {
            mob.setTarget(target);
            ScaledMonsterStats stats = (ScaledMonsterStats)this.stats(mob).orElse(null);
            if (stats != null) {
               this.animateAttack(mob);
               switch (definition.archetype()) {
                  case BRUISER -> this.telegraphedSlam(mob, stats.damage() * definition.abilityPower());
                  case SKIRMISHER -> this.dash(mob, target, definition.abilityPower());
                  case CASTER -> this.castProjectile(mob, target, stats.damage() * definition.abilityPower());
                  case SUPPORT -> this.healAllies(mob, definition.abilityPower());
               }

            }
         }
      } else {
         this.stop(mob);
      }
   }

   private Optional<Player> target(Mob mob, double range) {
      LivingEntity var5 = mob.getTarget();
      if (var5 instanceof Player player) {
         if (this.isEligibleTarget(player) && player.getLocation().distanceSquared(mob.getLocation()) <= range * range) {
            return Optional.of(player);
         }
      }

      var var10000 = mob.getNearbyEntities(range, Math.min((double)16.0F, range), range).stream();
      Objects.requireNonNull(Player.class);
      var10000 = var10000.filter(Player.class::isInstance);
      Objects.requireNonNull(Player.class);
      return var10000.map(Player.class::cast).filter(this::isEligibleTarget).min(Comparator.comparingDouble((playerx) -> playerx.getLocation().distanceSquared(mob.getLocation())));
   }

   private boolean isEligibleTarget(Player player) {
      return !player.isDead() && !player.getGameMode().isInvulnerable() && this.characterService.activeCharacter(player.getUniqueId()).isPresent();
   }

   private void telegraphedSlam(Mob mob, double damage) {
      Location center = mob.getLocation();
      center.getWorld().spawnParticle(Particle.CRIT, center.clone().add((double)0.0F, 0.2, (double)0.0F), 18, 1.2, 0.1, 1.2, 0.05);
      center.getWorld().playSound(center, Sound.ENTITY_IRON_GOLEM_REPAIR, 0.7F, 0.6F);
      this.scheduler.runEntityLater(mob, () -> {
         if (mob.isValid() && !mob.isDead()) {
            Location impact = mob.getLocation();
            impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 2, (double)0.5F, 0.1, (double)0.5F, (double)0.0F);
            impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 0.8F);

            for(Entity nearby : mob.getNearbyEntities((double)3.5F, (double)2.5F, (double)3.5F)) {
               if (nearby instanceof Player) {
                  Player player = (Player)nearby;
                  if (this.isEligibleTarget(player)) {
                     this.dealAbilityDamage(mob, player, damage);
                     Vector knockback = player.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize().multiply(0.55).setY(0.3);
                     player.setVelocity(knockback);
                  }
               }
            }

         }
      }, () -> {
      }, 16L);
   }

   private void dash(Mob mob, Player target, double power) {
      Vector direction = target.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize();
      mob.setVelocity(direction.multiply(Math.max(0.65, Math.min(1.15, power))).setY(0.2));
      mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation(), 10, 0.3, 0.1, 0.3, 0.02);
      mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.7F, 1.3F);
   }

   private void castProjectile(Mob mob, Player target, double damage) {
      Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector()).normalize().multiply((double)1.25F);
      Snowball projectile = (Snowball)mob.launchProjectile(Snowball.class, direction);
      projectile.getPersistentDataContainer().set(this.projectileDamageKey, PersistentDataType.DOUBLE, Math.max((double)0.0F, damage));
      mob.getWorld().spawnParticle(Particle.ENCHANT, mob.getEyeLocation(), 12, (double)0.25F, (double)0.25F, (double)0.25F, 0.05);
      mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.7F, 1.25F);
   }

   private void healAllies(Mob mob, double fraction) {
      boolean healed = false;

      for(Entity nearby : mob.getNearbyEntities((double)7.0F, (double)4.0F, (double)7.0F)) {
         if (nearby instanceof LivingEntity ally) {
            if (this.isManaged(ally) && !ally.isDead()) {
               double maximum = this.maximumHealth(ally);
               double next = Math.min(maximum, ally.getHealth() + maximum * Math.min(0.3, fraction));
               if (next > ally.getHealth()) {
                  ally.setHealth(next);
                  ally.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, ally.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 6, 0.3, 0.4, 0.3, (double)0.0F);
                  healed = true;
               }
            }
         }
      }

      if (healed) {
         mob.getWorld().playSound(mob.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.2F);
      }

   }

   private void dealAbilityDamage(Mob source, Player target, double damage) {
      this.activeAbilityDamage.set(damage);

      try {
         target.damage(damage, source);
      } finally {
         this.activeAbilityDamage.remove();
      }

   }

   private void applyAttribute(LivingEntity entity, Attribute attribute, double value) {
      AttributeInstance instance = entity.getAttribute(attribute);
      if (instance != null) {
         instance.setBaseValue(value);
      }

   }

   private void attachModel(LivingEntity entity, MonsterDefinition definition) {
      if (this.modelEngine.attach(entity, definition.modelId())) {
         this.modelEngine.play(entity, definition.modelId(), definition.idleAnimation());
      }

   }

   private double maximumHealth(LivingEntity entity) {
      AttributeInstance instance = entity.getAttribute(Attribute.MAX_HEALTH);
      return instance == null ? (double)20.0F : instance.getValue();
   }
}
