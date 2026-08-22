package com.xuzhihuanjing.rpgcore.progression;

import com.xuzhihuanjing.rpgcore.config.ProgressionSettings;

public final class ExperienceCurve {
   private final ProgressionSettings settings;
   private final long[] experienceAtLevel;

   public ExperienceCurve(ProgressionSettings settings) {
      this.settings = settings;
      this.experienceAtLevel = new long[settings.maximumLevel() + 1];

      for(int level = 2; level <= settings.maximumLevel(); ++level) {
         this.experienceAtLevel[level] = Math.addExact(this.experienceAtLevel[level - 1], this.experienceToNextLevel(level - 1));
      }

   }

   public int maximumLevel() {
      return this.settings.maximumLevel();
   }

   public long experienceAtLevel(int level) {
      this.requireLevel(level);
      return this.experienceAtLevel[level];
   }

   public long experienceToNextLevel(int level) {
      this.requireLevel(level);
      if (level >= this.settings.maximumLevel()) {
         return 0L;
      } else {
         double index = (double)level - (double)1.0F;
         return Math.max(1L, Math.round(this.settings.experienceBase() + this.settings.experienceLinear() * Math.pow(index, this.settings.experiencePower()) + this.settings.experienceQuadratic() * index * index));
      }
   }

   public int levelForExperience(long experience) {
      long safeExperience = Math.max(0L, experience);
      int low = 1;
      int high = this.settings.maximumLevel();

      while(low < high) {
         int middle = low + high + 1 >>> 1;
         if (this.experienceAtLevel[middle] <= safeExperience) {
            low = middle;
         } else {
            high = middle - 1;
         }
      }

      return low;
   }

   public double rewardMultiplier(int playerLevel, int monsterLevel) {
      int difference = monsterLevel - playerLevel;
      return difference < -10 ? Math.max(0.1, 0.7 + (double)(difference + 10) * 0.06) : Math.max(0.7, Math.min((double)1.25F, (double)1.0F + (double)difference * 0.03));
   }

   private void requireLevel(int level) {
      if (level < 1 || level > this.settings.maximumLevel()) {
         throw new IllegalArgumentException("Level is outside the progression range");
      }
   }
}
