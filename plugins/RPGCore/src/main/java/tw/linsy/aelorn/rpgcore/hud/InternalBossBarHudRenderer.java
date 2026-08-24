package tw.linsy.aelorn.rpgcore.hud;

import tw.linsy.aelorn.rpgcore.combat.CombatHudSnapshot;
import tw.linsy.aelorn.rpgcore.config.HudSettings;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class InternalBossBarHudRenderer implements CombatHudRenderer, Listener {
   private static final byte DISABLED = 1;
   private final NamespacedKey disabledKey;
   private final HudSettings settings;
   private final InternalHudComposer composer;
   private final Map<UUID, Session> sessions = new ConcurrentHashMap();
   private final Map<UUID, ResourcePackState> resourcePackStates = new ConcurrentHashMap();

   public InternalBossBarHudRenderer(Plugin plugin, HudSettings settings) {
      this.disabledKey = new NamespacedKey(plugin, "hud-disabled");
      this.settings = settings;
      this.composer = new InternalHudComposer(settings.maximumNotificationCodePoints());
   }

   public void render(Player player, CombatHudSnapshot snapshot) {
      if (snapshot.active() && this.isEnabled(player) && this.resourcePackReady(player)) {
         Session session = (Session)this.sessions.computeIfAbsent(player.getUniqueId(), (ignored) -> new Session(this.createBossBar(), snapshot));
         InternalHudVisualState visual = session.advance(snapshot, this.settings);
         String signature = this.signature(snapshot, visual);
         if (!signature.equals(session.signature)) {
            Component component = this.composer.compose(snapshot, visual);
            session.bossBar.name(component);
            session.signature = signature;
         }

         if (!session.visible) {
            player.showBossBar(session.bossBar);
            session.visible = true;
         }

      } else {
         this.hide(player);
      }
   }

   public void hide(Player player) {
      Session removed = (Session)this.sessions.remove(player.getUniqueId());
      if (removed != null && removed.visible) {
         player.hideBossBar(removed.bossBar);
      }

   }

   public boolean isEnabled(Player player) {
      Byte value = (Byte)player.getPersistentDataContainer().get(this.disabledKey, PersistentDataType.BYTE);
      return value == null || value != 1;
   }

   public void setEnabled(Player player, boolean enabled) {
      if (enabled) {
         player.getPersistentDataContainer().remove(this.disabledKey);
      } else {
         player.getPersistentDataContainer().set(this.disabledKey, PersistentDataType.BYTE, (byte)1);
         this.hide(player);
      }

   }

   public CombatHudRenderer.Status status(Player player) {
      Session session = (Session)this.sessions.get(player.getUniqueId());
      ResourcePackState packState = (ResourcePackState)this.resourcePackStates.getOrDefault(player.getUniqueId(), InternalBossBarHudRenderer.ResourcePackState.UNKNOWN);
      return new CombatHudRenderer.Status("internal", this.isEnabled(player), session != null && session.visible, this.settings.waitForResourcePack() ? packState.id : "not-required");
   }

   @EventHandler
   public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
      UUID playerId = event.getPlayer().getUniqueId();
      ResourcePackState var10000;
      switch (event.getStatus().name()) {
         case "SUCCESSFULLY_LOADED":
            var10000 = InternalBossBarHudRenderer.ResourcePackState.READY;
            break;
         case "ACCEPTED":
         case "DOWNLOADED":
            var10000 = InternalBossBarHudRenderer.ResourcePackState.LOADING;
            break;
         case "DECLINED":
         case "FAILED_DOWNLOAD":
         case "INVALID_URL":
         case "FAILED_RELOAD":
         case "DISCARDED":
            var10000 = InternalBossBarHudRenderer.ResourcePackState.BLOCKED;
            break;
         default:
            var10000 = InternalBossBarHudRenderer.ResourcePackState.UNKNOWN;
      }

      ResourcePackState state = var10000;
      this.resourcePackStates.put(playerId, state);
      if (state == InternalBossBarHudRenderer.ResourcePackState.BLOCKED) {
         this.hide(event.getPlayer());
      }

   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.hide(event.getPlayer());
      this.resourcePackStates.remove(event.getPlayer().getUniqueId());
   }

   public void close() {
      for(Map.Entry<UUID, Session> entry : this.sessions.entrySet()) {
         Player player = Bukkit.getPlayer((UUID)entry.getKey());
         if (player != null && ((Session)entry.getValue()).visible) {
            player.hideBossBar(((Session)entry.getValue()).bossBar);
         }
      }

      this.sessions.clear();
      this.resourcePackStates.clear();
   }

   private boolean resourcePackReady(Player player) {
      if (!this.settings.waitForResourcePack()) {
         return true;
      } else {
         return this.resourcePackStates.getOrDefault(player.getUniqueId(), InternalBossBarHudRenderer.ResourcePackState.UNKNOWN) == InternalBossBarHudRenderer.ResourcePackState.READY;
      }
   }

   private BossBar createBossBar() {
      return BossBar.bossBar(Component.empty(), 0.0F, Color.YELLOW, Overlay.PROGRESS);
   }

   private String signature(CombatHudSnapshot snapshot, InternalHudVisualState visual) {
      int var10000 = visual.frame();
      return var10000 + "|" + visual.healthStep() + "|" + visual.manaStep() + "|" + visual.movementStep() + "|" + snapshot.underwater() + "|" + snapshot.currentHealth() + "|" + snapshot.maximumHealth() + "|" + snapshot.currentMana() + "|" + snapshot.maximumMana() + "|" + snapshot.level() + "|" + snapshot.classId() + "|" + snapshot.className() + "|" + String.valueOf(snapshot.combo()) + "|" + snapshot.notification();
   }

   private static enum ResourcePackState {
      UNKNOWN("waiting"),
      LOADING("loading"),
      READY("ready"),
      BLOCKED("blocked");

      private final String id;

      private ResourcePackState(String id) {
         this.id = id;
      }
   }

   private static final class Session {
      private final BossBar bossBar;
      private double healthRatio;
      private double manaRatio;
      private double movementRatio;
      private CombatHudSnapshot.MovementMode movementMode;
      private long animationTicks;
      private String signature = "";
      private boolean visible;

      private Session(BossBar bossBar, CombatHudSnapshot snapshot) {
         this.bossBar = bossBar;
         this.healthRatio = snapshot.healthRatio();
         this.manaRatio = snapshot.manaRatio();
         this.movementRatio = snapshot.movementRatio();
         this.movementMode = snapshot.movementMode();
      }

      private InternalHudVisualState advance(CombatHudSnapshot snapshot, HudSettings settings) {
         this.healthRatio = smooth(this.healthRatio, snapshot.healthRatio(), settings.healthSmoothing());
         this.manaRatio = smooth(this.manaRatio, snapshot.manaRatio(), settings.manaSmoothing());
         if (this.movementMode != snapshot.movementMode()) {
            this.movementMode = snapshot.movementMode();
            this.movementRatio = snapshot.movementRatio();
         } else {
            this.movementRatio = smooth(this.movementRatio, snapshot.movementRatio(), settings.movementSmoothing());
         }

         int frame = (int)(this.animationTicks / (long)settings.animationFrameTicks() % 4L);
         this.animationTicks += settings.updateIntervalTicks();
         return new InternalHudVisualState(frame, InternalHudComposer.listenerStep(this.healthRatio, 32), InternalHudComposer.listenerStep(this.manaRatio, 32), InternalHudComposer.listenerStep(this.movementRatio, 25));
      }

      private static double smooth(double current, double target, double factor) {
         return Math.abs(current - target) < 5.0E-4 ? target : current + (target - current) * factor;
      }
   }
}
