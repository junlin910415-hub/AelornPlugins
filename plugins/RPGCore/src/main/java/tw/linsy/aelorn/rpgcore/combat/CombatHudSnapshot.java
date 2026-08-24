package tw.linsy.aelorn.rpgcore.combat;

import tw.linsy.aelorn.rpgcore.domain.ability.InputToken;
import java.util.List;

public record CombatHudSnapshot(boolean active, int currentHealth, int maximumHealth, int currentMana, int maximumMana, int movement, int maximumMovement, MovementMode movementMode, int level, long currentLevelExperience, long requiredLevelExperience, String classId, String className, List<InputToken> combo, String notification, String worldName, int blockX, int blockY, int blockZ) {
   private static final CombatHudSnapshot INACTIVE;

   public CombatHudSnapshot(boolean active, int currentHealth, int maximumHealth, int currentMana, int maximumMana, int movement, int maximumMovement, MovementMode movementMode, int level, String classId, String className, List<InputToken> combo, String notification) {
      this(active, currentHealth, maximumHealth, currentMana, maximumMana, movement, maximumMovement, movementMode, level, 0L, 1L, classId, className, combo, notification, "", 0, 0, 0);
   }

   public CombatHudSnapshot(boolean active, int currentHealth, int maximumHealth, int currentMana, int maximumMana, int movement, int maximumMovement, MovementMode movementMode, int level, String classId, String className, List<InputToken> combo, String notification, String worldName, int blockX, int blockY, int blockZ) {
      this(active, currentHealth, maximumHealth, currentMana, maximumMana, movement, maximumMovement, movementMode, level, 0L, 1L, classId, className, combo, notification, worldName, blockX, blockY, blockZ);
   }

   public CombatHudSnapshot(boolean active, int currentHealth, int maximumHealth, int currentMana, int maximumMana, int movement, int maximumMovement, MovementMode movementMode, int level, long currentLevelExperience, long requiredLevelExperience, String classId, String className, List<InputToken> combo, String notification, String worldName, int blockX, int blockY, int blockZ) {
      maximumHealth = Math.max(1, maximumHealth);
      maximumMana = Math.max(1, maximumMana);
      maximumMovement = Math.max(1, maximumMovement);
      currentHealth = clamp(currentHealth, 0, maximumHealth);
      currentMana = clamp(currentMana, 0, maximumMana);
      movement = clamp(movement, 0, maximumMovement);
      currentLevelExperience = Math.max(0L, currentLevelExperience);
      requiredLevelExperience = Math.max(0L, requiredLevelExperience);
      if (requiredLevelExperience > 0L) {
         currentLevelExperience = Math.min(currentLevelExperience, requiredLevelExperience);
      }

      movementMode = movementMode == null ? CombatHudSnapshot.MovementMode.STAMINA : movementMode;
      classId = classId != null && !classId.isBlank() ? classId : "none";
      className = className == null ? "" : className;
      combo = combo == null ? List.of() : List.copyOf(combo);
      notification = notification == null ? "" : notification;
      worldName = worldName == null ? "" : worldName;
      this.active = active;
      this.currentHealth = currentHealth;
      this.maximumHealth = maximumHealth;
      this.currentMana = currentMana;
      this.maximumMana = maximumMana;
      this.movement = movement;
      this.maximumMovement = maximumMovement;
      this.movementMode = movementMode;
      this.level = level;
      this.currentLevelExperience = currentLevelExperience;
      this.requiredLevelExperience = requiredLevelExperience;
      this.classId = classId;
      this.className = className;
      this.combo = combo;
      this.notification = notification;
      this.worldName = worldName;
      this.blockX = blockX;
      this.blockY = blockY;
      this.blockZ = blockZ;
   }

   public static CombatHudSnapshot inactive() {
      return INACTIVE;
   }

   public double healthRatio() {
      return ratio(this.currentHealth, this.maximumHealth);
   }

   public double manaRatio() {
      return ratio(this.currentMana, this.maximumMana);
   }

   public double movementRatio() {
      return ratio(this.movement, this.maximumMovement);
   }

   public double experienceRatio() {
      return this.requiredLevelExperience <= 0L ? (double)1.0F : ratio(this.currentLevelExperience, this.requiredLevelExperience);
   }

   public boolean underwater() {
      return this.movementMode == CombatHudSnapshot.MovementMode.AIR;
   }

   public boolean comboActive() {
      return !this.combo.isEmpty();
   }

   public String comboToken(int index) {
      if (index >= 0 && index < this.combo.size()) {
         return this.combo.get(index) == InputToken.LEFT ? "left" : "right";
      } else {
         return "empty";
      }
   }

   public boolean notificationActive() {
      return !this.notification.isBlank();
   }

   public String coordinates() {
      return this.blockX + ", " + this.blockY + ", " + this.blockZ;
   }

   private static double ratio(int current, int maximum) {
      return Math.max((double)0.0F, Math.min((double)1.0F, (double)current / (double)Math.max(1, maximum)));
   }

   private static double ratio(long current, long maximum) {
      return Math.max((double)0.0F, Math.min((double)1.0F, (double)current / (double)Math.max(1L, maximum)));
   }

   private static int clamp(int value, int minimum, int maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   static {
      INACTIVE = new CombatHudSnapshot(false, 0, 1, 0, 1, 0, 1, CombatHudSnapshot.MovementMode.STAMINA, 0, 0L, 1L, "none", "", List.of(), "", "", 0, 0, 0);
   }

   public static enum MovementMode {
      STAMINA("stamina"),
      AIR("air");

      private final String id;

      private MovementMode(String id) {
         this.id = id;
      }

      public String id() {
         return this.id;
      }
   }
}
