package tw.linsy.aelorn.rpgcore.domain.stats;

public record BaseStats(double health, double mana, double attack, double defense, double resistance, double speed) {
   public boolean isValid() {
      return this.health > (double)0.0F && this.mana >= (double)0.0F && this.attack >= (double)0.0F && this.defense >= (double)0.0F && this.resistance >= (double)0.0F && this.speed > (double)0.0F;
   }
}
