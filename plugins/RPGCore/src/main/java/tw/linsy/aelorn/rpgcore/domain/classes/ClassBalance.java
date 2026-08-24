package tw.linsy.aelorn.rpgcore.domain.classes;

import java.util.Objects;

public record ClassBalance(double damageTakenMultiplier, double basicAttackMultiplier, ClassRatings ratings) {
   public ClassBalance {
      Objects.requireNonNull(ratings, "ratings");
   }

   public boolean isValid() {
      return this.damageTakenMultiplier >= (double)0.75F && this.damageTakenMultiplier <= (double)1.5F && this.basicAttackMultiplier >= (double)0.5F && this.basicAttackMultiplier <= (double)1.5F && this.ratings.isValid();
   }
}
