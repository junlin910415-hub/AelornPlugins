package tw.linsy.aelorn.rpgcore.hud;

import tw.linsy.aelorn.rpgcore.config.HudSettings;
import tw.linsy.aelorn.rpgcore.platform.RpgScheduler;
import dev.aeloria.hud.api.AeloriaHudService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Publishes RPGCore's bundled HUD assets through AeloriaHUD's single-pack API.
 * Nexo owns item registration and pack generation; AeloriaHUD is the only dispatcher.
 */
public final class InternalHudPackService implements AutoCloseable {
   private static final String PACK_RESOURCE = "rpgcore-hud-pack.zip";
   private static final String SOURCE_ID = "rpgcore";
   private static final int SOURCE_PRIORITY = 200;

   private final JavaPlugin plugin;
   private final HudSettings settings;
   private final Logger logger;
   private final Path sourcePack;
   private final Path marker;
   private final AtomicBoolean generationPending = new AtomicBoolean();
   private volatile String bundledHash;
   private volatile boolean initialized;
   private volatile boolean updateRequired;
   private AeloriaHudService hudService;
   private AeloriaHudService.Registration assetRegistration;

   public InternalHudPackService(JavaPlugin plugin, HudSettings settings, RpgScheduler scheduler) {
      this.plugin = Objects.requireNonNull(plugin, "plugin");
      this.settings = Objects.requireNonNull(settings, "settings");
      Objects.requireNonNull(scheduler, "scheduler");
      this.logger = plugin.getLogger();
      Path dataRoot = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
      this.sourcePack = dataRoot.resolve("pack-sources").resolve(PACK_RESOURCE).normalize();
      this.marker = dataRoot.resolve("cache").resolve("internal-hud-pack.sha256").normalize();
      if (!this.sourcePack.startsWith(dataRoot) || !this.marker.startsWith(dataRoot)) {
         throw new IllegalArgumentException("RPGCore pack paths escaped the plugin data folder");
      }
   }

   public void initialize() throws IOException {
      if (this.settings.renderer() == HudSettings.Renderer.NATIVE
         || !this.settings.aeloriaAssetsEnabled()
         || !this.settings.installPackOnStartup()) {
         return;
      }

      this.requireBundledPack();
      this.hudService = this.plugin.getServer().getServicesManager().load(AeloriaHudService.class);
      if (this.hudService == null) {
         throw new IOException("AeloriaHUD service is unavailable; cannot publish RPGCore HUD assets");
      }

      this.updateRequired = this.installSourcePack();
      this.assetRegistration = this.hudService.registerAssetSource(SOURCE_ID, this.sourcePack, SOURCE_PRIORITY);
      this.initialized = true;

      if (this.settings.regeneratePackOnChange() && this.updateRequired) {
         this.requestRegeneration(true);
      } else {
         this.logger.info("RPGCore assets are registered with AeloriaHUD's merged resource pack.");
      }
   }

   public boolean requestRegeneration(boolean publish) {
      if (!this.initialized || this.hudService == null || !this.generationPending.compareAndSet(false, true)) {
         return false;
      }

      this.hudService.rebuildResourcePack().whenComplete((descriptor, failure) -> {
         this.generationPending.set(false);
         if (failure != null) {
            this.logger.log(Level.SEVERE, "AeloriaHUD could not rebuild the merged RPGCore resource pack", failure);
            return;
         }
         this.updateRequired = false;
         this.logger.info("AeloriaHUD published the merged RPGCore resource pack (sha1 " + descriptor.sha1() + ").");
      });
      return true;
   }

   public Status status() {
      if (!this.initialized) {
         return new Status(false, false, false, "disabled");
      }
      boolean assetsReady = this.sourceIsCurrent();
      String state = this.generationPending.get() ? "publishing" : assetsReady && !this.updateRequired ? "ready" : "update-required";
      return new Status(true, assetsReady, this.generationPending.get(), state);
   }

   @Override
   public void close() {
      this.generationPending.set(false);
      AeloriaHudService.Registration registration = this.assetRegistration;
      this.assetRegistration = null;
      if (registration != null) {
         try {
            registration.close();
         } catch (RuntimeException exception) {
            this.logger.log(Level.WARNING, "Could not unregister RPGCore's AeloriaHUD asset source", exception);
         }
      }
      this.hudService = null;
      this.initialized = false;
   }

   private boolean installSourcePack() throws IOException {
      String expected = this.bundledHash();
      if (Files.isRegularFile(this.sourcePack, new LinkOption[0]) && expected.equals(this.hash(this.sourcePack))) {
         this.writeMarker(expected);
         return false;
      }

      Files.createDirectories(this.sourcePack.getParent());
      Path temporary = Files.createTempFile(this.sourcePack.getParent(), "rpgcore-hud-pack-", ".tmp");
      try {
         try (InputStream input = this.plugin.getResource(PACK_RESOURCE)) {
            if (input == null) {
               throw new IOException("Missing bundled resource " + PACK_RESOURCE);
            }
            Files.copy(input, temporary, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
         }
         if (!expected.equals(this.hash(temporary))) {
            throw new IOException("RPGCore HUD source failed its post-copy SHA-256 check");
         }
         try {
            Files.move(temporary, this.sourcePack, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, this.sourcePack, StandardCopyOption.REPLACE_EXISTING);
         }
         this.writeMarker(expected);
         return true;
      } finally {
         Files.deleteIfExists(temporary);
      }
   }

   private boolean sourceIsCurrent() {
      try {
         return Files.isRegularFile(this.sourcePack, new LinkOption[0])
            && this.bundledHash().equals(this.hash(this.sourcePack));
      } catch (IOException | UncheckedIOException exception) {
         this.logger.log(Level.WARNING, "Could not verify the RPGCore HUD asset source", exception);
         return false;
      }
   }

   private void requireBundledPack() throws IOException {
      try (InputStream input = this.plugin.getResource(PACK_RESOURCE)) {
         if (input == null) {
            throw new IOException("RPGCore JAR does not contain " + PACK_RESOURCE);
         }
      }
      this.bundledHash();
   }

   private String bundledHash() {
      String cached = this.bundledHash;
      if (cached != null) {
         return cached;
      }
      try (InputStream input = this.plugin.getResource(PACK_RESOURCE)) {
         if (input == null) {
            throw new IllegalStateException("Missing bundled HUD pack");
         }
         cached = digest(input);
         this.bundledHash = cached;
         return cached;
      } catch (IOException exception) {
         throw new UncheckedIOException(exception);
      }
   }

   private String hash(Path file) throws IOException {
      try (InputStream input = Files.newInputStream(file)) {
         return digest(input);
      }
   }

   private static String digest(InputStream input) throws IOException {
      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         byte[] buffer = new byte[16384];
         int read;
         while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
               digest.update(buffer, 0, read);
            }
         }
         return HexFormat.of().withUpperCase().formatHex(digest.digest());
      } catch (NoSuchAlgorithmException exception) {
         throw new IllegalStateException("SHA-256 is unavailable", exception);
      }
   }

   private void writeMarker(String hash) throws IOException {
      Files.createDirectories(this.marker.getParent());
      Files.writeString(this.marker, hash + System.lineSeparator(), StandardCharsets.US_ASCII);
   }

   public record Status(boolean initialized, boolean assetsReady, boolean generationPending, String state) {
   }
}
