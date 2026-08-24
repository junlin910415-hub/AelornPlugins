package tw.linsy.aelorn.rpgcore.domain.classes;

public record ClassRatings(int difficulty, int damage, int defense, int range, int mobility, int support) {
   public boolean isValid() {
      return this.inRange(this.difficulty) && this.inRange(this.damage) && this.inRange(this.defense) && this.inRange(this.range) && this.inRange(this.mobility) && this.inRange(this.support);
   }

   private boolean inRange(int value) {
      return value >= 1 && value <= 5;
   }
}
