package tw.linsy.aelorn.rpgcore.domain.profession;

public record ProfessionProgress(int level, long experience) {
   public ProfessionProgress(int level, long experience) {
      if (level >= 1 && experience >= 0L) {
         this.level = level;
         this.experience = experience;
      } else {
         throw new IllegalArgumentException("Profession progress values must be positive");
      }
   }

   public static ProfessionProgress fresh() {
      return new ProfessionProgress(1, 0L);
   }
}
