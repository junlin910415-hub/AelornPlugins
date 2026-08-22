package com.xuzhihuanjing.rpgcore.service;

import com.xuzhihuanjing.rpgcore.config.ClassRegistry;
import com.xuzhihuanjing.rpgcore.domain.character.AccountProfile;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterRepository;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CharacterService implements AutoCloseable {
   private final CharacterRepository repository;
   private final ClassRegistry classRegistry;
   private final int maximumSlots;
   private final int backupLimit;
   private final Logger logger;
   private final ExecutorService storageExecutor;
   private final Map<UUID, AccountProfile> accounts;
   private final Map<UUID, CompletableFuture<AccountProfile>> pendingLoads;
   private final Map<UUID, Instant> activeSessions;

   public CharacterService(CharacterRepository repository, ClassRegistry classRegistry, int maximumSlots, Logger logger) {
      this(repository, classRegistry, maximumSlots, 5, logger);
   }

   public CharacterService(CharacterRepository repository, ClassRegistry classRegistry, int maximumSlots, int backupLimit, Logger logger) {
      this.accounts = new ConcurrentHashMap();
      this.pendingLoads = new ConcurrentHashMap();
      this.activeSessions = new ConcurrentHashMap();
      this.repository = repository;
      this.classRegistry = classRegistry;
      this.maximumSlots = maximumSlots;
      this.backupLimit = Math.max(1, backupLimit);
      this.logger = logger;
      this.storageExecutor = Executors.newSingleThreadExecutor((runnable) -> {
         Thread thread = new Thread(runnable, "rpgcore-storage");
         thread.setDaemon(true);
         return thread;
      });
   }

   public CompletableFuture<AccountProfile> load(UUID ownerId) {
      AccountProfile cached = (AccountProfile)this.accounts.get(ownerId);
      if (cached != null) {
         return CompletableFuture.completedFuture(cached);
      } else {
         CompletableFuture<AccountProfile> future = (CompletableFuture)this.pendingLoads.computeIfAbsent(ownerId, (ignored) -> CompletableFuture.supplyAsync(() -> {
               try {
                  AccountProfile loaded = ((AccountProfile)this.repository.find(ownerId).orElseGet(() -> AccountProfile.empty(ownerId))).finalizeExpiredDeletions(Instant.now(), this.backupLimit);
                  this.accounts.put(ownerId, loaded);
                  return loaded;
               } catch (RuntimeException | IOException exception) {
                  throw new IllegalStateException("Could not load account " + String.valueOf(ownerId), exception);
               }
            }, this.storageExecutor));
         future.whenComplete((account, throwable) -> this.pendingLoads.remove(ownerId, future));
         return future;
      }
   }

   public Optional<AccountProfile> loadedAccount(UUID ownerId) {
      return Optional.ofNullable((AccountProfile)this.accounts.get(ownerId));
   }

   public Optional<CharacterProfile> activeCharacter(UUID ownerId) {
      return this.loadedAccount(ownerId).flatMap(AccountProfile::activeCharacter);
   }

   public CharacterProfile createCharacter(UUID ownerId, String playerName, int slot, String classId) {
      this.requireSlot(slot);
      if (this.classRegistry.find(classId).isEmpty()) {
         throw new IllegalArgumentException("Unknown class: " + classId);
      } else {
         Instant now = Instant.now();
         CharacterProfile character = new CharacterProfile(UUID.randomUUID(), slot, playerName + " #" + (slot + 1), classId, 1, 0L, Set.of(), now, now);
         this.update(ownerId, (account) -> {
            if (account.characterAt(slot).isPresent()) {
               throw new IllegalStateException("Character slot is already occupied");
            } else {
               return account.withCharacter(character);
            }
         });
         this.activeSessions.put(ownerId, now);
         this.save(ownerId);
         return character;
      }
   }

   public CharacterProfile selectCharacter(UUID ownerId, int slot) {
      this.requireSlot(slot);
      Instant now = Instant.now();
      this.checkpointSession(ownerId, now);
      AccountProfile updated = this.update(ownerId, (account) -> {
         CharacterProfile character = ((CharacterProfile)account.characterAt(slot).orElseThrow(() -> new IllegalArgumentException("Character slot is empty"))).playedAt(now);
         return account.withCharacter(character).withActiveSlot(slot);
      });
      this.activeSessions.put(ownerId, now);
      this.save(ownerId);
      return (CharacterProfile)updated.activeCharacter().orElseThrow();
   }

   public CharacterProfile updateActiveCharacter(UUID ownerId, UnaryOperator<CharacterProfile> mutation) {
      AccountProfile updated = this.update(ownerId, (account) -> {
         CharacterProfile current = (CharacterProfile)account.activeCharacter().orElseThrow(() -> new IllegalStateException("Account has no active character"));
         CharacterProfile changed = (CharacterProfile)mutation.apply(current);
         if (changed.id().equals(current.id()) && changed.slot() == current.slot() && changed.classId().equals(current.classId())) {
            return account.withCharacter(changed);
         } else {
            throw new IllegalArgumentException("Character identity cannot be changed by a state mutation");
         }
      });
      this.save(ownerId);
      return (CharacterProfile)updated.activeCharacter().orElseThrow();
   }

   public void resumeActiveSession(UUID ownerId) {
      if (this.activeCharacter(ownerId).isPresent()) {
         this.activeSessions.putIfAbsent(ownerId, Instant.now());
      }

   }

   public AccountProfile setSelectorMusicEnabled(UUID ownerId, boolean enabled) {
      AccountProfile updated = this.update(ownerId, (account) -> account.withSelectorMusicEnabled(enabled));
      this.save(ownerId);
      return updated;
   }

   public AccountProfile setAutoOpenSelector(UUID ownerId, boolean enabled) {
      AccountProfile updated = this.update(ownerId, (account) -> account.withAutoOpenSelector(enabled));
      this.save(ownerId);
      return updated;
   }

   public AccountProfile scheduleDeletion(UUID ownerId, int slot, Instant deleteAt) {
      this.requireSlot(slot);
      AccountProfile before = (AccountProfile)this.loadedAccount(ownerId).orElseThrow(() -> new IllegalStateException("Account is not loaded"));
      if (before.activeSlot() == slot) {
         this.checkpointSession(ownerId, Instant.now());
         this.activeSessions.remove(ownerId);
      }

      AccountProfile updated = this.update(ownerId, (account) -> account.scheduleDeletion(slot, deleteAt));
      this.save(ownerId);
      return updated;
   }

   public AccountProfile cancelDeletion(UUID ownerId, int slot) {
      this.requireSlot(slot);
      AccountProfile updated = this.update(ownerId, (account) -> account.cancelDeletion(slot));
      this.save(ownerId);
      return updated;
   }

   public AccountProfile finalizeExpiredDeletions(UUID ownerId) {
      AccountProfile updated = this.update(ownerId, (account) -> account.finalizeExpiredDeletions(Instant.now(), this.backupLimit));
      this.save(ownerId);
      return updated;
   }

   public CharacterProfile restoreBackup(UUID ownerId, UUID characterId, int targetSlot) {
      this.requireSlot(targetSlot);
      AccountProfile updated = this.update(ownerId, (account) -> account.restoreBackup(characterId, targetSlot));
      this.activeSessions.put(ownerId, Instant.now());
      this.save(ownerId);
      return (CharacterProfile)updated.activeCharacter().orElseThrow();
   }

   public void saveAndUnload(UUID ownerId) {
      this.checkpointSession(ownerId, Instant.now());
      this.activeSessions.remove(ownerId);
      AccountProfile account = (AccountProfile)this.accounts.remove(ownerId);
      if (account != null) {
         this.submitSave(account);
      }

   }

   private void save(UUID ownerId) {
      AccountProfile account = (AccountProfile)this.accounts.get(ownerId);
      if (account != null) {
         this.submitSave(account);
      }

   }

   private void submitSave(AccountProfile account) {
      this.storageExecutor.execute(() -> {
         try {
            this.repository.save(account);
         } catch (IOException exception) {
            this.logger.log(Level.SEVERE, "Could not save account " + String.valueOf(account.ownerId()), exception);
         }

      });
   }

   private AccountProfile update(UUID ownerId, UnaryOperator<AccountProfile> mutation) {
      return (AccountProfile)this.accounts.compute(ownerId, (ignored, account) -> {
         if (account == null) {
            throw new IllegalStateException("Account is not loaded");
         } else {
            return (AccountProfile)mutation.apply(account);
         }
      });
   }

   private void requireSlot(int slot) {
      if (slot < 0 || slot >= this.maximumSlots) {
         throw new IllegalArgumentException("Character slot is outside the available range");
      }
   }

   private void checkpointSession(UUID ownerId, Instant now) {
      Instant startedAt = (Instant)this.activeSessions.remove(ownerId);
      if (startedAt != null) {
         long elapsedSeconds = Math.max(0L, Duration.between(startedAt, now).getSeconds());
         if (elapsedSeconds != 0L && this.accounts.containsKey(ownerId)) {
            this.update(ownerId, (account) -> (AccountProfile)account.activeCharacter().map((character) -> account.withCharacter(character.withPlayTimeSeconds(this.saturatingAdd(character.playTimeSeconds(), elapsedSeconds)))).orElse(account));
         }
      }
   }

   private long saturatingAdd(long left, long right) {
      return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
   }

   public void close() {
      Instant now = Instant.now();

      for(UUID ownerId : List.copyOf(this.activeSessions.keySet())) {
         this.checkpointSession(ownerId, now);
      }

      for(AccountProfile account : this.accounts.values()) {
         this.submitSave(account);
      }

      this.accounts.clear();
      this.activeSessions.clear();
      this.storageExecutor.shutdown();

      try {
         if (!this.storageExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
            this.logger.warning("Timed out while saving RPGCore character data");
         }
      } catch (InterruptedException var4) {
         Thread.currentThread().interrupt();
         this.logger.warning("Interrupted while saving RPGCore character data");
      }

   }
}
