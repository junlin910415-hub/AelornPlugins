package com.xuzhihuanjing.rpgcore.encounter;

public final class EncounterProgress {
   private final int waveCount;
   private int currentWave = -1;
   private int remainingMonsters;
   private boolean terminal;

   public EncounterProgress(int waveCount) {
      if (waveCount < 1) {
         throw new IllegalArgumentException("Encounter requires at least one wave");
      } else {
         this.waveCount = waveCount;
      }
   }

   public synchronized int beginNextWave(int monsterCount) {
      if (!this.terminal && this.remainingMonsters == 0 && monsterCount >= 1 && this.currentWave + 1 < this.waveCount) {
         ++this.currentWave;
         this.remainingMonsters = monsterCount;
         return this.currentWave;
      } else {
         throw new IllegalStateException("Encounter cannot begin another wave");
      }
   }

   public synchronized Transition monsterGone() {
      if (!this.terminal && this.remainingMonsters > 0) {
         --this.remainingMonsters;
         if (this.remainingMonsters > 0) {
            return EncounterProgress.Transition.NONE;
         } else if (this.currentWave + 1 >= this.waveCount) {
            this.terminal = true;
            return EncounterProgress.Transition.COMPLETED;
         } else {
            return EncounterProgress.Transition.WAVE_CLEARED;
         }
      } else {
         return EncounterProgress.Transition.NONE;
      }
   }

   public synchronized void terminate() {
      this.terminal = true;
      this.remainingMonsters = 0;
   }

   public synchronized int currentWave() {
      return this.currentWave;
   }

   public synchronized int remainingMonsters() {
      return this.remainingMonsters;
   }

   public synchronized boolean terminal() {
      return this.terminal;
   }

   public static enum Transition {
      NONE,
      WAVE_CLEARED,
      COMPLETED;
   }
}
