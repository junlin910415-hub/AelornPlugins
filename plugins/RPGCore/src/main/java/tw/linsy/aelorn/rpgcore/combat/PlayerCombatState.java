package tw.linsy.aelorn.rpgcore.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerCombatState {
   private final UUID characterId;
   private final Map<String, Long> cooldowns = new HashMap();
   private double maximumMana;
   private double mana;

   public PlayerCombatState(UUID characterId, double maximumMana) {
      this.characterId = characterId;
      this.maximumMana = maximumMana;
      this.mana = maximumMana;
   }

   public UUID characterId() {
      return this.characterId;
   }

   public double maximumMana() {
      return this.maximumMana;
   }

   public synchronized void resizeMaximumMana(double newMaximumMana) {
      double safeMaximum = Math.max((double)1.0F, newMaximumMana);
      double ratio = this.maximumMana <= (double)0.0F ? (double)1.0F : this.mana / this.maximumMana;
      this.maximumMana = safeMaximum;
      this.mana = Math.max((double)0.0F, Math.min(this.maximumMana, ratio * this.maximumMana));
   }

   public synchronized double mana() {
      return this.mana;
   }

   public synchronized boolean spendMana(double amount) {
      if (!(amount < (double)0.0F) && !(this.mana + 1.0E-9 < amount)) {
         this.mana -= amount;
         return true;
      } else {
         return false;
      }
   }

   public synchronized void refundMana(double amount) {
      this.mana = Math.min(this.maximumMana, this.mana + Math.max((double)0.0F, amount));
   }

   public synchronized void regenerate(double amount) {
      this.mana = Math.min(this.maximumMana, this.mana + Math.max((double)0.0F, amount));
   }

   public synchronized long cooldownRemainingMillis(String abilityId, long nowMillis) {
      return Math.max(0L, (Long)this.cooldowns.getOrDefault(abilityId, 0L) - nowMillis);
   }

   public synchronized void startCooldown(String abilityId, double seconds, long nowMillis) {
      long duration = Math.max(0L, Math.round(seconds * (double)1000.0F));
      this.cooldowns.put(abilityId, nowMillis + duration);
   }
}
