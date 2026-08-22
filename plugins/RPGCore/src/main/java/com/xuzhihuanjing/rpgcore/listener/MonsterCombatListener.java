package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.combat.CharacterActivationService;
import com.xuzhihuanjing.rpgcore.combat.DamagePipeline;
import com.xuzhihuanjing.rpgcore.combat.HudNotificationService;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.combat.DamageResult;
import com.xuzhihuanjing.rpgcore.domain.monster.MonsterDefinition;
import com.xuzhihuanjing.rpgcore.domain.monster.ScaledMonsterStats;
import com.xuzhihuanjing.rpgcore.encounter.EncounterRuntimeService;
import com.xuzhihuanjing.rpgcore.monster.ContributionLedger;
import com.xuzhihuanjing.rpgcore.monster.MonsterLootService;
import com.xuzhihuanjing.rpgcore.monster.MonsterRuntimeService;
import com.xuzhihuanjing.rpgcore.platform.RpgScheduler;
import com.xuzhihuanjing.rpgcore.progression.ExperienceCurve;
import com.xuzhihuanjing.rpgcore.progression.ProgressionResult;
import com.xuzhihuanjing.rpgcore.progression.ProgressionService;
import com.xuzhihuanjing.rpgcore.quest.QuestService;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class MonsterCombatListener implements Listener {
   private static final long CONTRIBUTION_WINDOW_MILLIS = 30000L;
   private static final double MINIMUM_REWARD_SHARE = 0.05;
   private final MonsterRuntimeService monsters;
   private final ContributionLedger contributions;
   private final MonsterLootService lootService;
   private final CharacterService characterService;
   private final ProgressionService progressionService;
   private final ExperienceCurve experienceCurve;
   private final CharacterActivationService activationService;
   private final DamagePipeline damagePipeline;
   private final HudNotificationService notifications;
   private final MessageBundle messages;
   private final RpgScheduler scheduler;
   private final EncounterRuntimeService encounters;
   private final QuestService quests;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();

   public MonsterCombatListener(MonsterRuntimeService monsters, ContributionLedger contributions, MonsterLootService lootService, CharacterService characterService, ProgressionService progressionService, ExperienceCurve experienceCurve, CharacterActivationService activationService, DamagePipeline damagePipeline, HudNotificationService notifications, MessageBundle messages, RpgScheduler scheduler, EncounterRuntimeService encounters, QuestService quests) {
      this.monsters = monsters;
      this.contributions = contributions;
      this.lootService = lootService;
      this.characterService = characterService;
      this.progressionService = progressionService;
      this.experienceCurve = experienceCurve;
      this.activationService = activationService;
      this.damagePipeline = damagePipeline;
      this.notifications = notifications;
      this.messages = messages;
      this.scheduler = scheduler;
      this.encounters = encounters;
      this.quests = quests;
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onMonsterAttack(EntityDamageByEntityEvent event) {
      Entity damager = event.getDamager();
      if (damager instanceof Projectile projectile) {
         Double damage = (Double)this.monsters.projectileDamage(projectile).orElse(null);
         if (damage == null) {
            ProjectileSource var5 = projectile.getShooter();
            if (var5 instanceof LivingEntity) {
               LivingEntity source = (LivingEntity)var5;
               if (this.monsters.isManaged(source)) {
                  damage = (Double)this.monsters.stats(source).map(ScaledMonsterStats::damage).orElse(null);
               }
            }
         }

         if (damage != null) {
            event.setDamage(damage);
         }

      } else {
         Entity meleeSource = event.getDamager();
         if (meleeSource instanceof LivingEntity source) {
            if (this.monsters.isManaged(source)) {
               this.monsters.animateAttack(source);
               double damage = (Double)this.monsters.activeAbilityDamage().orElseGet(() -> (Double)this.monsters.stats(source).map(ScaledMonsterStats::damage).orElse(event.getDamage()));
               event.setDamage(damage);
            }
         }

      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onMonsterDamaged(EntityDamageByEntityEvent event) {
      Entity var3 = event.getEntity();
      if (var3 instanceof LivingEntity monster) {
         if (this.monsters.isManaged(monster)) {
            Player attacker = this.playerSource(event.getDamager());
            if (attacker != null && !this.characterService.activeCharacter(attacker.getUniqueId()).isEmpty()) {
               ScaledMonsterStats stats = (ScaledMonsterStats)this.monsters.stats(monster).orElse(null);
               if (stats == null) {
                  return;
               }

               double rating = (stats.defense() + stats.resistance()) * (double)0.5F;
               double mitigation = Math.min(0.7, rating / (rating + (double)55.0F + (double)3.0F * (double)stats.level()));
               double finalDamage = Math.max(0.05, event.getDamage() * ((double)1.0F - mitigation));
               event.setDamage(finalDamage);
               this.monsters.animateHurt(monster);
               boolean critical = (Boolean)this.damagePipeline.currentResult().map(DamageResult::critical).orElse(finalDamage >= Math.max((double)2.0F, stats.maximumHealth() * 0.06));
               this.showHitFeedback(attacker, monster, finalDamage, mitigation, critical);
               this.contributions.record(monster.getUniqueId(), attacker.getUniqueId(), Math.min(monster.getHealth(), finalDamage), System.currentTimeMillis());
               this.encounters.recordContribution(monster.getUniqueId(), attacker.getUniqueId());
               return;
            }

            return;
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onMonsterDeath(EntityDeathEvent event) {
      LivingEntity monster = event.getEntity();
      if (this.monsters.isManaged(monster)) {
         MonsterDefinition definition = (MonsterDefinition)this.monsters.definition(monster).orElse(null);
         ScaledMonsterStats stats = (ScaledMonsterStats)this.monsters.stats(monster).orElse(null);
         this.monsters.animateDeath(monster);
         this.monsters.stop(monster);
         this.encounters.monsterDefeated(monster.getUniqueId());
         if (definition != null && stats != null) {
            event.getDrops().clear();
            event.getDrops().addAll(this.lootService.roll(definition, stats.level()));
            event.setDroppedExp(0);
            Map<UUID, Double> shares = this.contributions.settle(monster.getUniqueId(), System.currentTimeMillis(), 30000L);
            Player killer = monster.getKiller();
            UUID killerId = killer == null ? null : killer.getUniqueId();
            UUID topContributor = (UUID)shares.keySet().stream().findFirst().orElse(killerId);

            for(Map.Entry<UUID, Double> entry : shares.entrySet()) {
               if (!((Double)entry.getValue() < 0.05) || ((UUID)entry.getKey()).equals(killerId) || ((UUID)entry.getKey()).equals(topContributor)) {
                  Player player = Bukkit.getPlayer((UUID)entry.getKey());
                  if (player != null && player.isOnline()) {
                     CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter((UUID)entry.getKey()).orElse(null);
                     if (character != null) {
                        double contributionMultiplier = Math.max(0.2, Math.min((double)1.0F, (Double)entry.getValue() * (double)1.25F));
                        long reward = Math.max(1L, Math.round((double)stats.experience() * contributionMultiplier * this.experienceCurve.rewardMultiplier(character.level(), stats.level())));
                        this.scheduler.executeEntity(player, () -> this.reward(player, definition, reward), () -> {
                        });
                     }
                  }
               }
            }

         } else {
            this.contributions.clear(monster.getUniqueId());
         }
      }
   }

   private void reward(Player player, MonsterDefinition definition, long experience) {
      if (player.isOnline() && !this.characterService.activeCharacter(player.getUniqueId()).isEmpty()) {
         ProgressionResult result = this.progressionService.grantExperience(player.getUniqueId(), experience);
         String monsterName = this.miniMessage.stripTags(definition.displayName());
         this.notifications.show(player.getUniqueId(), this.messages.content("monster-xp", MessageBundle.value("experience", Long.toString(result.awardedExperience())), MessageBundle.value("monster", monsterName)));
         if (result.leveledUp()) {
            player.sendMessage(this.messages.message("level-up", MessageBundle.value("level", Integer.toString(result.level()))));
            this.characterService.activeCharacter(player.getUniqueId()).ifPresent((character) -> this.activationService.activate(player, character));
         }

         this.quests.recordMonsterKill(player, definition.id());
      }
   }

   private Player playerSource(Entity damager) {
      if (damager instanceof Player player) {
         return player;
      } else {
         if (damager instanceof Projectile projectile) {
            ProjectileSource var4 = projectile.getShooter();
            if (var4 instanceof Player player) {
               return player;
            }
         }

         return null;
      }
   }

   private void showHitFeedback(Player attacker, LivingEntity monster, double finalDamage, double mitigation, boolean critical) {
      double impact = finalDamage / Math.max((double)1.0F, monster.getAttribute(Attribute.MAX_HEALTH) == null ? monster.getHealth() : monster.getAttribute(Attribute.MAX_HEALTH).getValue());
      int damageParticles = Math.max(2, Math.min(14, (int)Math.ceil(finalDamage * (double)0.75F)));
      monster.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, monster.getLocation().add((double)0.0F, monster.getHeight() * 0.65, (double)0.0F), damageParticles, (double)0.25F, (double)0.25F, (double)0.25F, 0.02);
      if (critical) {
         monster.getWorld().spawnParticle(Particle.CRIT, monster.getEyeLocation(), 18, 0.35, 0.35, 0.35, 0.08);
      }

      Sound sound = critical ? Sound.ENTITY_PLAYER_ATTACK_CRIT : (impact >= 0.045 ? Sound.ENTITY_PLAYER_ATTACK_STRONG : Sound.ENTITY_PLAYER_ATTACK_WEAK);
      float pitch = critical ? 1.35F : (impact >= 0.045 ? 1.05F : 0.85F);
      monster.getWorld().playSound(monster.getLocation(), sound, 0.65F, pitch);
      String prefix = critical ? "<gold>暴擊</gold> " : "";
      this.notifications.show(attacker.getUniqueId(), this.miniMessage.deserialize(prefix + "<red>" + this.formatDamage(finalDamage) + "</red><gray> 傷害</gray>" + (mitigation > (double)0.0F ? " <dark_gray>減傷 " + Math.round(mitigation * (double)100.0F) + "%</dark_gray>" : "")));
   }

   private String formatDamage(double damage) {
      return damage >= (double)100.0F ? Long.toString(Math.round(damage)) : String.format(Locale.US, "%.1f", damage);
   }
}
