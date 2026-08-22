package com.xuzhihuanjing.rpgcore.encounter;

import com.xuzhihuanjing.rpgcore.combat.CharacterActivationService;
import com.xuzhihuanjing.rpgcore.combat.HudNotificationService;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.config.MonsterRegistry;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.encounter.EncounterDefinition;
import com.xuzhihuanjing.rpgcore.domain.encounter.EncounterSpawnDefinition;
import com.xuzhihuanjing.rpgcore.domain.encounter.EncounterWaveDefinition;
import com.xuzhihuanjing.rpgcore.domain.monster.MonsterDefinition;
import com.xuzhihuanjing.rpgcore.monster.MonsterRuntimeService;
import com.xuzhihuanjing.rpgcore.platform.RpgScheduler;
import com.xuzhihuanjing.rpgcore.progression.ExperienceCurve;
import com.xuzhihuanjing.rpgcore.progression.ProgressionResult;
import com.xuzhihuanjing.rpgcore.progression.ProgressionService;
import com.xuzhihuanjing.rpgcore.quest.QuestService;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class EncounterRuntimeService {
   private final MonsterRegistry monsterRegistry;
   private final MonsterRuntimeService monsters;
   private final CharacterService characterService;
   private final ProgressionService progressionService;
   private final ExperienceCurve experienceCurve;
   private final CharacterActivationService activationService;
   private final HudNotificationService notifications;
   private final MessageBundle messages;
   private final RpgScheduler scheduler;
   private final Logger logger;
   private final QuestService quests;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();
   private final Map<UUID, ActiveEncounter> active = new ConcurrentHashMap();
   private final Map<UUID, UUID> mobOwners = new ConcurrentHashMap();
   private final Map<CooldownKey, Long> cooldowns = new ConcurrentHashMap();

   public EncounterRuntimeService(MonsterRegistry monsterRegistry, MonsterRuntimeService monsters, CharacterService characterService, ProgressionService progressionService, ExperienceCurve experienceCurve, CharacterActivationService activationService, HudNotificationService notifications, MessageBundle messages, RpgScheduler scheduler, Logger logger, QuestService quests) {
      this.monsterRegistry = monsterRegistry;
      this.monsters = monsters;
      this.characterService = characterService;
      this.progressionService = progressionService;
      this.experienceCurve = experienceCurve;
      this.activationService = activationService;
      this.notifications = notifications;
      this.messages = messages;
      this.scheduler = scheduler;
      this.logger = logger;
      this.quests = quests;
   }

   public synchronized EncounterStartResult start(Location center, EncounterDefinition definition, int level, UUID starterId) {
      if (level >= definition.minimumLevel() && level <= definition.maximumLevel()) {
         long now = System.currentTimeMillis();
         if (starterId != null) {
            Long expiresAt = (Long)this.cooldowns.get(new CooldownKey(starterId, definition.id()));
            if (expiresAt != null && expiresAt > now) {
               long seconds = Math.max(1L, (expiresAt - now + 999L) / 1000L);
               return EncounterStartResult.rejected(EncounterStartResult.Status.COOLDOWN, seconds);
            }
         }

         boolean overlapping = this.active.values().stream().anyMatch((runx) -> this.overlaps(center, definition.arenaRadius(), runx.center, runx.definition.arenaRadius()));
         if (overlapping) {
            return EncounterStartResult.rejected(EncounterStartResult.Status.OVERLAPPING, 0L);
         } else {
            ActiveEncounter run = new ActiveEncounter(UUID.randomUUID(), definition, level, center);
            this.active.put(run.id, run);
            if (starterId != null && definition.cooldownSeconds() > 0L) {
               this.cooldowns.put(new CooldownKey(starterId, definition.id()), now + definition.cooldownSeconds() * 1000L);
            }

            run.timeoutTask = this.scheduler.runGlobalLater(() -> this.fail(run), definition.timeoutTicks());
            this.spawnWave(run);
            return EncounterStartResult.started(run.id);
         }
      } else {
         return EncounterStartResult.rejected(EncounterStartResult.Status.INVALID_LEVEL, 0L);
      }
   }

   public void recordContribution(UUID monsterId, UUID playerId) {
      UUID encounterId = (UUID)this.mobOwners.get(monsterId);
      ActiveEncounter run = encounterId == null ? null : (ActiveEncounter)this.active.get(encounterId);
      if (run != null) {
         run.participants.add(playerId);
      }

   }

   public void monsterDefeated(UUID monsterId) {
      UUID encounterId = (UUID)this.mobOwners.remove(monsterId);
      ActiveEncounter run = encounterId == null ? null : (ActiveEncounter)this.active.get(encounterId);
      if (run != null) {
         this.handleTransition(run, run.monsterGone(monsterId));
      }

   }

   public List<EncounterSnapshot> snapshots() {
      return this.active.values().stream().map(ActiveEncounter::snapshot).sorted(Comparator.comparing(EncounterSnapshot::definitionId).thenComparing(EncounterSnapshot::runId)).toList();
   }

   public int activeCount() {
      return this.active.size();
   }

   public synchronized boolean executeIfIdle(Runnable action) {
      if (!this.active.isEmpty()) {
         return false;
      } else {
         action.run();
         return true;
      }
   }

   public int cancelAll() {
      List<ActiveEncounter> runs = List.copyOf(this.active.values());
      runs.forEach((run) -> this.terminate(run, true));
      return runs.size();
   }

   public boolean cancel(UUID runId) {
      ActiveEncounter run = (ActiveEncounter)this.active.get(runId);
      if (run == null) {
         return false;
      } else {
         this.terminate(run, true);
         return true;
      }
   }

   public boolean cancelByPrefix(String runIdPrefix) {
      String prefix = runIdPrefix.toLowerCase();
      List<ActiveEncounter> matches = this.active.values().stream().filter((run) -> run.id.toString().startsWith(prefix)).limit(2L).toList();
      if (matches.size() != 1) {
         return false;
      } else {
         this.terminate((ActiveEncounter)matches.getFirst(), true);
         return true;
      }
   }

   public boolean cancelNearest(Location location) {
      Optional<ActiveEncounter> nearest = this.active.values().stream().filter((run) -> run.center.getWorld().getUID().equals(location.getWorld().getUID())).filter((run) -> run.center.distanceSquared(location) <= run.definition.arenaRadius() * run.definition.arenaRadius()).min(Comparator.comparingDouble((run) -> run.center.distanceSquared(location)));
      nearest.ifPresent((run) -> this.terminate(run, true));
      return nearest.isPresent();
   }

   public void shutdown() {
      for(ActiveEncounter run : List.copyOf(this.active.values())) {
         Closure closure = this.close(run);
         var var10000 = closure.entities();
         MonsterRuntimeService var10001 = this.monsters;
         Objects.requireNonNull(var10001);
         var10000.forEach(var10001::stop);
      }

      this.active.clear();
      this.mobOwners.clear();
      this.cooldowns.clear();
   }

   private void spawnWave(ActiveEncounter run) {
      if (this.active.containsKey(run.id)) {
         int nextIndex = run.progress.currentWave() + 1;
         EncounterWaveDefinition wave = (EncounterWaveDefinition)run.definition.waves().get(nextIndex);
         int waveIndex = run.beginWave(wave.monsterCount());
         this.notifyParticipants(run, "encounter-wave", MessageBundle.value("wave", Integer.toString(waveIndex + 1)), MessageBundle.value("total", Integer.toString(run.definition.waves().size())), MessageBundle.value("name", this.miniMessage.stripTags(wave.displayName())));
         int spawnIndex = 0;

         for(EncounterSpawnDefinition spawn : wave.spawns()) {
            MonsterDefinition monster = (MonsterDefinition)this.monsterRegistry.find(spawn.monsterId()).orElseThrow();
            int monsterLevel = Math.max(monster.minimumLevel(), Math.min(monster.maximumLevel(), run.level + spawn.levelOffset()));

            for(int count = 0; count < spawn.amount(); ++count) {
               Location location = this.spawnLocation(run, spawnIndex++, wave.monsterCount());
               this.scheduler.executeRegion(location, () -> this.spawnMonster(run, location, monster, monsterLevel));
            }
         }

      }
   }

   private void spawnMonster(ActiveEncounter run, Location location, MonsterDefinition definition, int level) {
      if (!this.active.containsKey(run.id)) {
         this.handleTransition(run, run.monsterGone((UUID)null));
      } else {
         try {
            LivingEntity entity = this.monsters.spawn(location, definition, level);
            if (!run.register(entity)) {
               this.monsters.remove(entity);
               return;
            }

            this.mobOwners.put(entity.getUniqueId(), run.id);
         } catch (RuntimeException exception) {
            this.logger.log(Level.WARNING, "Could not spawn encounter monster " + definition.id(), exception);
            this.handleTransition(run, run.monsterGone((UUID)null));
         }

      }
   }

   private Location spawnLocation(ActiveEncounter run, int index, int total) {
      double angle = (Math.PI * 2D) * (double)index / (double)Math.max(1, total);
      double ring = Math.min(run.definition.arenaRadius() - (double)1.0F, (double)2.5F + (double)(index % 3) * (double)1.25F);
      return run.center.clone().add(Math.cos(angle) * ring, 0.2, Math.sin(angle) * ring);
   }

   private void handleTransition(ActiveEncounter run, EncounterProgress.Transition transition) {
      if (transition == EncounterProgress.Transition.WAVE_CLEARED) {
         ScheduledTask next = this.scheduler.runGlobalLater(() -> this.spawnWave(run), run.definition.waveDelayTicks());
         run.setNextWaveTask(next);
      } else if (transition == EncounterProgress.Transition.COMPLETED) {
         this.complete(run);
      }

   }

   private void complete(ActiveEncounter run) {
      Closure closure = this.close(run);
      if (closure.closed()) {
         this.scheduler.executeGlobal(() -> {
            for(UUID participantId : run.participants) {
               Player player = Bukkit.getPlayer(participantId);
               if (player != null && player.isOnline()) {
                  this.scheduler.executeEntity(player, () -> this.reward(player, run), () -> {
                  });
               }
            }

         });
      }
   }

   private void reward(Player player, ActiveEncounter run) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character != null) {
         double levelScale = (double)1.0F + 0.04 * (double)(run.level - run.definition.minimumLevel());
         long requested = Math.max(1L, Math.round((double)run.definition.completionExperience() * levelScale * this.experienceCurve.rewardMultiplier(character.level(), run.level)));
         ProgressionResult result = this.progressionService.grantExperience(player.getUniqueId(), requested);
         String name = this.miniMessage.stripTags(run.definition.displayName());
         this.notifications.show(player.getUniqueId(), this.messages.content("encounter-reward", MessageBundle.value("experience", Long.toString(result.awardedExperience())), MessageBundle.value("name", name)));
         player.sendMessage(this.messages.message("encounter-complete", MessageBundle.value("name", name), MessageBundle.value("experience", Long.toString(result.awardedExperience()))));
         if (result.leveledUp()) {
            player.sendMessage(this.messages.message("level-up", MessageBundle.value("level", Integer.toString(result.level()))));
            this.characterService.activeCharacter(player.getUniqueId()).ifPresent((updated) -> this.activationService.activate(player, updated));
         }

         this.quests.recordEncounterCompletion(player, run.definition.id());
      }
   }

   private void fail(ActiveEncounter run) {
      if (this.active.containsKey(run.id)) {
         this.terminate(run, false);
      }
   }

   private void terminate(ActiveEncounter run, boolean cancelled) {
      Closure closure = this.close(run);
      if (closure.closed()) {
         var var10000 = closure.entities();
         MonsterRuntimeService var10001 = this.monsters;
         Objects.requireNonNull(var10001);
         var10000.forEach(var10001::remove);
         if (cancelled) {
            this.notifyParticipants(run, "encounter-cancelled", MessageBundle.value("name", this.miniMessage.stripTags(run.definition.displayName())));
         } else {
            this.notifyParticipants(run, "encounter-failed", MessageBundle.value("name", this.miniMessage.stripTags(run.definition.displayName())));
         }

      }
   }

   private Closure close(ActiveEncounter run) {
      Collection<LivingEntity> remaining = run.close();
      if (remaining == null) {
         return new Closure(false, List.of());
      } else {
         this.active.remove(run.id, run);
         this.scheduler.cancel(run.timeoutTask);
         this.scheduler.cancel(run.nextWaveTask);
         remaining.forEach((entity) -> this.mobOwners.remove(entity.getUniqueId(), run.id));
         return new Closure(true, remaining);
      }
   }

   private void notifyParticipants(ActiveEncounter run, String key, TagResolver... resolvers) {
      if (!run.participants.isEmpty()) {
         Component content = this.messages.content(key, resolvers);
         Component chat = this.messages.message(key, resolvers);
         this.scheduler.executeGlobal(() -> {
            for(UUID playerId : run.participants) {
               Player player = Bukkit.getPlayer(playerId);
               if (player != null && player.isOnline()) {
                  this.scheduler.executeEntity(player, () -> {
                     this.notifications.show(playerId, content);
                     if (key.equals("encounter-failed") || key.equals("encounter-cancelled")) {
                        player.sendMessage(chat);
                     }

                  }, () -> {
                  });
               }
            }

         });
      }
   }

   private boolean overlaps(Location left, double leftRadius, Location right, double rightRadius) {
      if (!left.getWorld().getUID().equals(right.getWorld().getUID())) {
         return false;
      } else {
         double distance = leftRadius + rightRadius;
         return left.distanceSquared(right) < distance * distance;
      }
   }

   private static record CooldownKey(UUID playerId, String encounterId) {
   }

   private static record Closure(boolean closed, Collection<LivingEntity> entities) {
   }

   private static final class ActiveEncounter {
      private final UUID id;
      private final EncounterDefinition definition;
      private final int level;
      private final Location center;
      private final EncounterProgress progress;
      private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
      private final Map<UUID, LivingEntity> living = new HashMap();
      private ScheduledTask timeoutTask;
      private ScheduledTask nextWaveTask;
      private boolean closed;

      private ActiveEncounter(UUID id, EncounterDefinition definition, int level, Location center) {
         this.id = id;
         this.definition = definition;
         this.level = level;
         this.center = center.clone();
         this.progress = new EncounterProgress(definition.waves().size());
      }

      private synchronized int beginWave(int monsterCount) {
         if (this.closed) {
            throw new IllegalStateException("Encounter is closed");
         } else {
            return this.progress.beginNextWave(monsterCount);
         }
      }

      private synchronized boolean register(LivingEntity entity) {
         if (!this.closed && !this.progress.terminal()) {
            this.living.put(entity.getUniqueId(), entity);
            return true;
         } else {
            return false;
         }
      }

      private synchronized EncounterProgress.Transition monsterGone(UUID monsterId) {
         if (monsterId != null) {
            this.living.remove(monsterId);
         }

         return this.closed ? EncounterProgress.Transition.NONE : this.progress.monsterGone();
      }

      private synchronized void setNextWaveTask(ScheduledTask task) {
         if (this.closed) {
            task.cancel();
         } else {
            this.nextWaveTask = task;
         }

      }

      private synchronized Collection<LivingEntity> close() {
         if (this.closed) {
            return null;
         } else {
            this.closed = true;
            this.progress.terminate();
            List<LivingEntity> result = new ArrayList(this.living.values());
            this.living.clear();
            return result;
         }
      }

      private synchronized EncounterSnapshot snapshot() {
         return new EncounterSnapshot(this.id, this.definition.id(), this.definition.displayName(), this.level, Math.max(0, this.progress.currentWave() + 1), this.definition.waves().size(), this.progress.remainingMonsters(), this.participants.size(), this.center.getWorld().getName(), this.center.getBlockX(), this.center.getBlockY(), this.center.getBlockZ());
      }
   }
}
