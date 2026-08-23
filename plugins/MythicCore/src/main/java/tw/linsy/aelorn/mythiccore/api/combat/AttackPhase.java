package tw.linsy.aelorn.mythiccore.api.combat;

public enum AttackPhase {
   READY,
   WINDUP,
   ACTIVE,
   RECOVERY,
   INTERRUPTED;

   private AttackPhase() {
   }
}
