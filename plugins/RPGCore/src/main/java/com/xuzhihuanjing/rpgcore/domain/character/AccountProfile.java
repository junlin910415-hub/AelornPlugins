package com.xuzhihuanjing.rpgcore.domain.character;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;

public final class AccountProfile {
   private final UUID ownerId;
   private final int activeSlot;
   private final Map<Integer, CharacterProfile> characters;
   private final boolean selectorMusicEnabled;
   private final boolean autoOpenSelector;
   private final Map<Integer, Instant> pendingDeletions;
   private final List<DeletedCharacterBackup> backups;

   public AccountProfile(UUID ownerId, int activeSlot, Map<Integer, CharacterProfile> characters) {
      this(ownerId, activeSlot, characters, true, false, Map.of(), List.of());
   }

   public AccountProfile(UUID ownerId, int activeSlot, Map<Integer, CharacterProfile> characters, boolean selectorMusicEnabled, boolean autoOpenSelector, Map<Integer, Instant> pendingDeletions, List<DeletedCharacterBackup> backups) {
      this.ownerId = (UUID)Objects.requireNonNull(ownerId, "ownerId");
      this.activeSlot = activeSlot;
      this.characters = Map.copyOf(new LinkedHashMap(characters));
      this.selectorMusicEnabled = selectorMusicEnabled;
      this.autoOpenSelector = autoOpenSelector;
      this.pendingDeletions = Map.copyOf(new LinkedHashMap(pendingDeletions));
      this.backups = List.copyOf(backups);
      this.characters.forEach((slot, character) -> {
         if (slot != character.slot()) {
            throw new IllegalArgumentException("Character map key does not match its slot");
         }
      });
      if (activeSlot >= 0 && !this.characters.containsKey(activeSlot)) {
         throw new IllegalArgumentException("Active slot does not contain a character");
      } else if (this.pendingDeletions.keySet().stream().anyMatch((slot) -> !this.characters.containsKey(slot))) {
         throw new IllegalArgumentException("Pending deletion does not contain a character");
      }
   }

   public static AccountProfile empty(UUID ownerId) {
      return new AccountProfile(ownerId, -1, Map.of());
   }

   public UUID ownerId() {
      return this.ownerId;
   }

   public int activeSlot() {
      return this.activeSlot;
   }

   public Map<Integer, CharacterProfile> characters() {
      return this.characters;
   }

   public boolean selectorMusicEnabled() {
      return this.selectorMusicEnabled;
   }

   public boolean autoOpenSelector() {
      return this.autoOpenSelector;
   }

   public Map<Integer, Instant> pendingDeletions() {
      return this.pendingDeletions;
   }

   public List<DeletedCharacterBackup> backups() {
      return this.backups;
   }

   public Optional<CharacterProfile> activeCharacter() {
      return Optional.ofNullable((CharacterProfile)this.characters.get(this.activeSlot));
   }

   public Optional<CharacterProfile> characterAt(int slot) {
      return Optional.ofNullable((CharacterProfile)this.characters.get(slot));
   }

   public boolean deletionPending(int slot) {
      return this.pendingDeletions.containsKey(slot);
   }

   public AccountProfile withCharacter(CharacterProfile character) {
      Map<Integer, CharacterProfile> updated = new LinkedHashMap(this.characters);
      updated.put(character.slot(), character);
      Map<Integer, Instant> deletions = new LinkedHashMap(this.pendingDeletions);
      deletions.remove(character.slot());
      return this.copy(character.slot(), updated, this.selectorMusicEnabled, this.autoOpenSelector, deletions, this.backups);
   }

   public AccountProfile withActiveSlot(int slot) {
      if (!this.characters.containsKey(slot)) {
         throw new IllegalArgumentException("Cannot select an empty character slot");
      } else if (this.pendingDeletions.containsKey(slot)) {
         throw new IllegalArgumentException("Cannot select a character waiting for deletion");
      } else {
         return this.copy(slot, this.characters, this.selectorMusicEnabled, this.autoOpenSelector, this.pendingDeletions, this.backups);
      }
   }

   public AccountProfile withSelectorMusicEnabled(boolean enabled) {
      return this.copy(this.activeSlot, this.characters, enabled, this.autoOpenSelector, this.pendingDeletions, this.backups);
   }

   public AccountProfile withAutoOpenSelector(boolean enabled) {
      return this.copy(this.activeSlot, this.characters, this.selectorMusicEnabled, enabled, this.pendingDeletions, this.backups);
   }

   public AccountProfile scheduleDeletion(int slot, Instant deleteAt) {
      if (!this.characters.containsKey(slot)) {
         throw new IllegalArgumentException("Cannot delete an empty character slot");
      } else {
         Map<Integer, Instant> updated = new LinkedHashMap(this.pendingDeletions);
         updated.put(slot, (Instant)Objects.requireNonNull(deleteAt, "deleteAt"));
         return this.copy(this.activeSlot == slot ? -1 : this.activeSlot, this.characters, this.selectorMusicEnabled, this.autoOpenSelector, updated, this.backups);
      }
   }

   public AccountProfile cancelDeletion(int slot) {
      Map<Integer, Instant> updated = new LinkedHashMap(this.pendingDeletions);
      updated.remove(slot);
      return this.copy(this.activeSlot, this.characters, this.selectorMusicEnabled, this.autoOpenSelector, updated, this.backups);
   }

   public AccountProfile finalizeExpiredDeletions(Instant now, int backupLimit) {
      Map<Integer, CharacterProfile> remaining = new LinkedHashMap(this.characters);
      Map<Integer, Instant> waiting = new LinkedHashMap(this.pendingDeletions);
      List<DeletedCharacterBackup> updatedBackups = new ArrayList(this.backups);
      this.pendingDeletions.entrySet().stream().filter((entry) -> !((Instant)entry.getValue()).isAfter(now)).sorted(Entry.comparingByValue()).forEach((entry) -> {
         CharacterProfile removed = (CharacterProfile)remaining.remove(entry.getKey());
         waiting.remove(entry.getKey());
         if (removed != null) {
            updatedBackups.add(new DeletedCharacterBackup(removed, now));
         }

      });
      updatedBackups.sort(Comparator.comparing(DeletedCharacterBackup::deletedAt).reversed());
      List<DeletedCharacterBackup> retainedBackups = updatedBackups;
      if (updatedBackups.size() > backupLimit) {
         retainedBackups = new ArrayList(updatedBackups.subList(0, backupLimit));
      }

      int selected = remaining.containsKey(this.activeSlot) ? this.activeSlot : -1;
      return this.copy(selected, remaining, this.selectorMusicEnabled, this.autoOpenSelector, waiting, retainedBackups);
   }

   public AccountProfile restoreBackup(UUID characterId, int targetSlot) {
      if (this.characters.containsKey(targetSlot)) {
         throw new IllegalArgumentException("Restore target slot is occupied");
      } else {
         DeletedCharacterBackup backup = (DeletedCharacterBackup)this.backups.stream().filter((candidate) -> candidate.character().id().equals(characterId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Character backup does not exist"));
         Map<Integer, CharacterProfile> updatedCharacters = new LinkedHashMap(this.characters);
         updatedCharacters.put(targetSlot, backup.character().reassignedToSlot(targetSlot));
         List<DeletedCharacterBackup> updatedBackups = this.backups.stream().filter((candidate) -> !candidate.character().id().equals(characterId)).toList();
         return this.copy(targetSlot, updatedCharacters, this.selectorMusicEnabled, this.autoOpenSelector, this.pendingDeletions, updatedBackups);
      }
   }

   private AccountProfile copy(int selectedSlot, Map<Integer, CharacterProfile> updatedCharacters, boolean musicEnabled, boolean autoOpen, Map<Integer, Instant> deletions, List<DeletedCharacterBackup> deletedBackups) {
      return new AccountProfile(this.ownerId, selectedSlot, updatedCharacters, musicEnabled, autoOpen, deletions, deletedBackups);
   }
}
