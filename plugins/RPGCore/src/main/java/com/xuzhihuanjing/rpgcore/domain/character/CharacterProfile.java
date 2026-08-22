package com.xuzhihuanjing.rpgcore.domain.character;

import com.xuzhihuanjing.rpgcore.domain.profession.ProfessionProgress;
import com.xuzhihuanjing.rpgcore.domain.profession.ProfessionType;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestProgress;
import com.xuzhihuanjing.rpgcore.domain.stats.PrimarySkill;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CharacterProfile(UUID id, int slot, String name, String classId, int level, long experience, Set<String> unlockedAbilityNodes, Map<PrimarySkill, Integer> skillPoints, Map<ProfessionType, ProfessionProgress> professions, Map<String, QuestProgress> questProgress, String trackedQuestId, Set<String> discoveredLocations, long playTimeSeconds, Instant createdAt, Instant lastPlayedAt) {
   public CharacterProfile(UUID id, int slot, String name, String classId, int level, long experience, Set<String> unlockedAbilityNodes, Map<PrimarySkill, Integer> skillPoints, Map<ProfessionType, ProfessionProgress> professions, Map<String, QuestProgress> questProgress, String trackedQuestId, Set<String> discoveredLocations, long playTimeSeconds, Instant createdAt, Instant lastPlayedAt) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(classId, "classId");
      unlockedAbilityNodes = Set.copyOf(unlockedAbilityNodes);
      skillPoints = normalizeSkillPoints(skillPoints);
      professions = normalizeProfessions(professions);
      questProgress = Map.copyOf(questProgress);
      Objects.requireNonNull(trackedQuestId, "trackedQuestId");
      discoveredLocations = Set.copyOf(discoveredLocations);
      Objects.requireNonNull(createdAt, "createdAt");
      Objects.requireNonNull(lastPlayedAt, "lastPlayedAt");
      if (slot >= 0 && level >= 1 && experience >= 0L && playTimeSeconds >= 0L) {
         if (!name.isBlank() && !classId.isBlank()) {
            this.id = id;
            this.slot = slot;
            this.name = name;
            this.classId = classId;
            this.level = level;
            this.experience = experience;
            this.unlockedAbilityNodes = unlockedAbilityNodes;
            this.skillPoints = skillPoints;
            this.professions = professions;
            this.questProgress = questProgress;
            this.trackedQuestId = trackedQuestId;
            this.discoveredLocations = discoveredLocations;
            this.playTimeSeconds = playTimeSeconds;
            this.createdAt = createdAt;
            this.lastPlayedAt = lastPlayedAt;
         } else {
            throw new IllegalArgumentException("Character name and class id are required");
         }
      } else {
         throw new IllegalArgumentException("Invalid character progression values");
      }
   }

   public CharacterProfile(UUID id, int slot, String name, String classId, int level, long experience, Set<String> unlockedAbilityNodes, Instant createdAt, Instant lastPlayedAt) {
      this(id, slot, name, classId, level, experience, unlockedAbilityNodes, Map.of(), Map.of(), Map.of(), "", Set.of(), 0L, createdAt, lastPlayedAt);
   }

   public CharacterProfile(UUID id, int slot, String name, String classId, int level, long experience, Set<String> unlockedAbilityNodes, Map<PrimarySkill, Integer> skillPoints, Map<ProfessionType, ProfessionProgress> professions, Map<String, QuestProgress> questProgress, String trackedQuestId, Set<String> discoveredLocations, Instant createdAt, Instant lastPlayedAt) {
      this(id, slot, name, classId, level, experience, unlockedAbilityNodes, skillPoints, professions, questProgress, trackedQuestId, discoveredLocations, 0L, createdAt, lastPlayedAt);
   }

   public CharacterProfile(UUID id, int slot, String name, String classId, int level, long experience, Set<String> unlockedAbilityNodes, Map<String, QuestProgress> questProgress, String trackedQuestId, Instant createdAt, Instant lastPlayedAt) {
      this(id, slot, name, classId, level, experience, unlockedAbilityNodes, Map.of(), Map.of(), questProgress, trackedQuestId, Set.of(), 0L, createdAt, lastPlayedAt);
   }

   public CharacterProfile(UUID id, int slot, String name, String classId, int level, long experience, Set<String> unlockedAbilityNodes, Map<String, QuestProgress> questProgress, String trackedQuestId, Set<String> discoveredLocations, Instant createdAt, Instant lastPlayedAt) {
      this(id, slot, name, classId, level, experience, unlockedAbilityNodes, Map.of(), Map.of(), questProgress, trackedQuestId, discoveredLocations, 0L, createdAt, lastPlayedAt);
   }

   public CharacterProfile playedAt(Instant time) {
      return this.copy(this.slot, this.level, this.experience, this.unlockedAbilityNodes, this.skillPoints, this.professions, this.questProgress, this.trackedQuestId, this.discoveredLocations, this.playTimeSeconds, time);
   }

   public CharacterProfile withAbilityNodes(Set<String> nodeIds) {
      return this.copy(this.slot, this.level, this.experience, nodeIds, this.skillPoints, this.professions, this.questProgress, this.trackedQuestId, this.discoveredLocations, this.playTimeSeconds, this.lastPlayedAt);
   }

   public CharacterProfile withSkillPoints(Map<PrimarySkill, Integer> newSkillPoints) {
      return this.copy(this.slot, this.level, this.experience, this.unlockedAbilityNodes, newSkillPoints, this.professions, this.questProgress, this.trackedQuestId, this.discoveredLocations, this.playTimeSeconds, this.lastPlayedAt);
   }

   public CharacterProfile withProfessions(Map<ProfessionType, ProfessionProgress> newProfessions) {
      return this.copy(this.slot, this.level, this.experience, this.unlockedAbilityNodes, this.skillPoints, newProfessions, this.questProgress, this.trackedQuestId, this.discoveredLocations, this.playTimeSeconds, this.lastPlayedAt);
   }

   public CharacterProfile withProgression(int newLevel, long newExperience) {
      return this.copy(this.slot, newLevel, newExperience, this.unlockedAbilityNodes, this.skillPoints, this.professions, this.questProgress, this.trackedQuestId, this.discoveredLocations, this.playTimeSeconds, this.lastPlayedAt);
   }

   public CharacterProfile withQuestProgress(Map<String, QuestProgress> newQuestProgress, String newTrackedQuestId) {
      return this.copy(this.slot, this.level, this.experience, this.unlockedAbilityNodes, this.skillPoints, this.professions, newQuestProgress, newTrackedQuestId, this.discoveredLocations, this.playTimeSeconds, this.lastPlayedAt);
   }

   public CharacterProfile withDiscoveredLocations(Set<String> discoveries) {
      return this.copy(this.slot, this.level, this.experience, this.unlockedAbilityNodes, this.skillPoints, this.professions, this.questProgress, this.trackedQuestId, discoveries, this.playTimeSeconds, this.lastPlayedAt);
   }

   public CharacterProfile withPlayTimeSeconds(long seconds) {
      return this.copy(this.slot, this.level, this.experience, this.unlockedAbilityNodes, this.skillPoints, this.professions, this.questProgress, this.trackedQuestId, this.discoveredLocations, Math.max(0L, seconds), this.lastPlayedAt);
   }

   public CharacterProfile reassignedToSlot(int newSlot) {
      return this.copy(newSlot, this.level, this.experience, this.unlockedAbilityNodes, this.skillPoints, this.professions, this.questProgress, this.trackedQuestId, this.discoveredLocations, this.playTimeSeconds, this.lastPlayedAt);
   }

   private CharacterProfile copy(int newSlot, int newLevel, long newExperience, Set<String> nodes, Map<PrimarySkill, Integer> skills, Map<ProfessionType, ProfessionProgress> professionProgress, Map<String, QuestProgress> quests, String trackedQuest, Set<String> discoveries, long playedSeconds, Instant playedAt) {
      return new CharacterProfile(this.id, newSlot, this.name, this.classId, newLevel, newExperience, nodes, skills, professionProgress, quests, trackedQuest, discoveries, playedSeconds, this.createdAt, playedAt);
   }

   private static Map<PrimarySkill, Integer> normalizeSkillPoints(Map<PrimarySkill, Integer> values) {
      EnumMap<PrimarySkill, Integer> normalized = new EnumMap(PrimarySkill.class);

      for(PrimarySkill skill : PrimarySkill.values()) {
         normalized.put(skill, Math.max(0, values == null ? 0 : (Integer)values.getOrDefault(skill, 0)));
      }

      return Map.copyOf(normalized);
   }

   private static Map<ProfessionType, ProfessionProgress> normalizeProfessions(Map<ProfessionType, ProfessionProgress> values) {
      EnumMap<ProfessionType, ProfessionProgress> normalized = new EnumMap(ProfessionType.class);

      for(ProfessionType profession : ProfessionType.values()) {
         ProfessionProgress progress = values == null ? null : (ProfessionProgress)values.get(profession);
         normalized.put(profession, progress == null ? ProfessionProgress.fresh() : progress);
      }

      return Map.copyOf(normalized);
   }
}
