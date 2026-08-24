package tw.linsy.aelorn.rpgcore.combat;

import tw.linsy.aelorn.rpgcore.ability.AbilityInputService;
import tw.linsy.aelorn.rpgcore.config.HudSettings;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.combat.CombatStats;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentService;
import tw.linsy.aelorn.rpgcore.hud.CombatHudRenderer;
import tw.linsy.aelorn.rpgcore.platform.RpgScheduler;
import tw.linsy.aelorn.rpgcore.progression.ProgressionResult;
import tw.linsy.aelorn.rpgcore.progression.ProgressionService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class CombatHudService {
   private static final int STAMINA_MAX = 100;
   private static final double STAMINA_DRAIN_PER_SECOND_WHILE_WALKING = (double)6.0F;
   private static final double STAMINA_DRAIN_PER_SECOND_WHILE_SPRINTING = (double)14.0F;
   private static final double STAMINA_REGEN_PER_SECOND = (double)12.0F;
   public static final String KEY_F_GLYPH = "rpgcore_key_f";
   private static final Map<String, String> CLASS_LABELS = Map.of("vanguard", "戰士", "ranger", "弓手", "shadowblade", "刺客", "arcanist", "法師", "warden", "薩滿");
   private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
   private final CharacterService characterService;
   private final StatService statService;
   private final EquipmentService equipmentService;
   private final CombatStateService combatStateService;
   private final AbilityInputService abilityInputService;
   private final HudNotificationService notifications;
   private final ProgressionService progressionService;
   private final NavigationHudService navigationHud;
   private final CombatHudRenderer renderer;
   private final RpgScheduler scheduler;
   private final HudSettings settings;
   private final double baseMagicPerUpdate;
   private final double baseHealthRegenerationPerSecond;
   private final double healthToMinecraftScale;
   private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap();
   private final Map<UUID, StaminaState> movementStates = new ConcurrentHashMap();
   private final Map<UUID, CombatHudSnapshot> snapshots = new ConcurrentHashMap();

   public CombatHudService(CharacterService characterService, StatService statService, EquipmentService equipmentService, CombatStateService combatStateService, AbilityInputService abilityInputService, HudNotificationService notifications, ProgressionService progressionService, NavigationHudService navigationHud, CombatHudRenderer renderer, RpgScheduler scheduler, HudSettings settings, double manaRegenerationPerSecond, double baseHealthRegenerationPerSecond, double healthToMinecraftScale) {
      this.characterService = characterService;
      this.statService = statService;
      this.equipmentService = equipmentService;
      this.combatStateService = combatStateService;
      this.abilityInputService = abilityInputService;
      this.notifications = notifications;
      this.progressionService = progressionService;
      this.navigationHud = navigationHud;
      this.renderer = renderer;
      this.scheduler = scheduler;
      this.settings = settings;
      this.baseMagicPerUpdate = manaRegenerationPerSecond * (double)settings.updateIntervalTicks() / (double)20.0F;
      this.baseHealthRegenerationPerSecond = Math.max((double)0.0F, baseHealthRegenerationPerSecond);
      this.healthToMinecraftScale = Math.max((double)0.0F, healthToMinecraftScale);
   }

   public void start(Player player) {
      UUID playerId = player.getUniqueId();
      this.stop(player);
      this.characterService.activeCharacter(playerId).ifPresent((character) -> this.navigationHud.start(player, character));
      this.movementStates.put(playerId, new StaminaState(System.nanoTime()));
      ScheduledTask task = this.scheduler.runEntityAtFixedRate(player, (ignored) -> this.tick(player), () -> {
         this.tasks.remove(playerId);
         this.movementStates.remove(playerId);
         this.snapshots.remove(playerId);
         this.combatStateService.remove(playerId);
         this.abilityInputService.clear(playerId);
      }, this.settings.updateIntervalTicks(), this.settings.updateIntervalTicks());
      if (task != null) {
         this.tasks.put(playerId, task);
      }

   }

   public void stop(Player player) {
      this.stop(player.getUniqueId());
      this.renderer.hide(player);
      this.navigationHud.stop(player);
   }

   public void stop(UUID playerId) {
      this.scheduler.cancel((ScheduledTask)this.tasks.remove(playerId));
      this.movementStates.remove(playerId);
      this.snapshots.remove(playerId);
   }

   public CombatHudSnapshot snapshot(UUID playerId) {
      return (CombatHudSnapshot)this.snapshots.getOrDefault(playerId, CombatHudSnapshot.inactive());
   }

   public CombatHudRenderer.Status hudStatus(Player player) {
      return this.renderer.status(player);
   }

   public boolean hudEnabled(Player player) {
      return this.renderer.isEnabled(player);
   }

   public void setHudEnabled(Player player, boolean enabled) {
      this.renderer.setEnabled(player, enabled);
      if (enabled) {
         this.refreshHud(player);
      }

   }

   public void refreshHud(Player player) {
      this.renderer.render(player, this.snapshot(player.getUniqueId()));
   }

   public void shutdown() {
      for(UUID playerId : this.tasks.keySet()) {
         Player player = Bukkit.getPlayer(playerId);
         if (player != null) {
            this.navigationHud.stop(player);
         }
      }

      var var10000 = this.tasks.values();
      RpgScheduler var10001 = this.scheduler;
      Objects.requireNonNull(var10001);
      var10000.forEach(var10001::cancel);
      this.tasks.clear();
      this.movementStates.clear();
      this.snapshots.clear();
      this.navigationHud.shutdown();
      this.renderer.close();
   }

   public static List<String> requiredGlyphIds() {
      return List.of("rpgcore_key_f");
   }

   private void tick(Player player) {
      UUID playerId = player.getUniqueId();
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(playerId).orElse(null);
      PlayerCombatState state = (PlayerCombatState)this.combatStateService.state(playerId).orElse(null);
      if (character != null && state != null && state.characterId().equals(character.id())) {
         CombatStats stats = this.statService.calculate(character, this.equipmentService.bonuses(player, character));
         state.regenerate(this.baseMagicPerUpdate + Math.max((double)0.0F, stats.manaRegeneration()) * (double)this.settings.updateIntervalTicks() / (double)20.0F);
         state.resizeMaximumMana(stats.maximumMana());
         this.regenerateHealth(player, stats);
         AttributeInstance maximumHealth = player.getAttribute(Attribute.MAX_HEALTH);
         double healthRatio = maximumHealth != null && !(maximumHealth.getValue() <= (double)0.0F) ? player.getHealth() / maximumHealth.getValue() : (double)0.0F;
         int currentHealth = (int)Math.ceil(stats.maximumHealth() * healthRatio);
         int maximumHealthValue = (int)Math.ceil(stats.maximumHealth());
         int mana = (int)Math.floor(state.mana());
         int maximumMana = (int)Math.ceil(state.maximumMana());
         MovementResource movement = this.resolveMovementResource(player);
         ProgressionResult progression = this.progressionService.describe(character);
         player.setLevel(progression.level());
         if (this.settings.showProgressionOnExperienceBar()) {
            player.setExp(this.experienceRatio(progression));
         } else {
            player.setExp((float)movement.ratio());
         }

         String classId = character.classId().toLowerCase(Locale.ROOT);
         String className = (String)CLASS_LABELS.getOrDefault(classId, character.classId());
         var var10000 = this.notifications.current(playerId, System.currentTimeMillis());
         PlainTextComponentSerializer var10001 = PLAIN_TEXT;
         Objects.requireNonNull(var10001);
         String notification = (String)var10000.map(var10001::serialize).orElse("");
         Location location = player.getLocation();
         CombatHudSnapshot snapshot = new CombatHudSnapshot(true, currentHealth, maximumHealthValue, mana, maximumMana, movement.current(), movement.maximum(), movement.mode(), progression.level(), progression.currentLevelExperience(), progression.requiredLevelExperience(), classId, className, this.abilityInputService.pendingInput(player), notification, location.getWorld() == null ? "" : location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
         this.snapshots.put(playerId, snapshot);
         this.renderer.render(player, snapshot);
         this.navigationHud.update(player, character);
      } else {
         this.snapshots.remove(playerId);
         this.renderer.hide(player);
      }
   }

   private void regenerateHealth(Player player, CombatStats stats) {
      if (!player.isDead() && !(this.healthToMinecraftScale <= (double)0.0F)) {
         AttributeInstance maximumHealth = player.getAttribute(Attribute.MAX_HEALTH);
         if (maximumHealth != null && !(maximumHealth.getValue() <= (double)0.0F) && !(player.getHealth() >= maximumHealth.getValue())) {
            double rpgHealingPerSecond = this.baseHealthRegenerationPerSecond + Math.max((double)0.0F, stats.healthRegeneration());
            if (!(rpgHealingPerSecond <= (double)0.0F)) {
               double healing = rpgHealingPerSecond * this.healthToMinecraftScale * (double)this.settings.updateIntervalTicks() / (double)20.0F;
               if (healing > (double)0.0F) {
                  player.setHealth(Math.min(maximumHealth.getValue(), player.getHealth() + healing));
               }

            }
         }
      }
   }

   private MovementResource resolveMovementResource(Player player) {
      if (!player.isInWater() && player.getRemainingAir() >= player.getMaximumAir()) {
         StaminaState state = (StaminaState)this.movementStates.computeIfAbsent(player.getUniqueId(), (id) -> new StaminaState(System.nanoTime()));
         long nowNanos = System.nanoTime();
         double deltaSeconds = (double)(nowNanos - state.lastUpdateNanos) / (double)1.0E9F;
         if (deltaSeconds < (double)0.0F) {
            deltaSeconds = (double)0.0F;
         } else if (deltaSeconds > (double)5.0F) {
            deltaSeconds = (double)5.0F;
         }

         state.lastUpdateNanos = nowNanos;
         if (this.isPlayerMoving(player)) {
            double drain = player.isSprinting() ? (double)14.0F : (double)6.0F;
            state.stamina = Math.max((double)0.0F, state.stamina - drain * deltaSeconds);
         } else {
            state.stamina = Math.min((double)100.0F, state.stamina + (double)12.0F * deltaSeconds);
         }

         return new MovementResource((int)Math.round(state.stamina), 100, CombatHudSnapshot.MovementMode.STAMINA);
      } else {
         int maximumAir = Math.max(1, player.getMaximumAir());
         return new MovementResource(player.getRemainingAir(), maximumAir, CombatHudSnapshot.MovementMode.AIR);
      }
   }

   private boolean isPlayerMoving(Player player) {
      Vector velocity = player.getVelocity();
      return velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ() > 3.0E-4;
   }

   private float experienceRatio(ProgressionResult progression) {
      return progression.requiredLevelExperience() <= 0L ? 1.0F : (float)Math.max((double)0.0F, Math.min((double)1.0F, (double)progression.currentLevelExperience() / (double)progression.requiredLevelExperience()));
   }

   private static record MovementResource(int current, int maximum, CombatHudSnapshot.MovementMode mode) {
      double ratio() {
         return Math.max((double)0.0F, Math.min((double)1.0F, (double)this.current / (double)Math.max(1, this.maximum)));
      }
   }

   private static final class StaminaState {
      private long lastUpdateNanos;
      private double stamina;

      private StaminaState(long nowNanos) {
         this.lastUpdateNanos = nowNanos;
         this.stamina = (double)100.0F;
      }
   }
}
