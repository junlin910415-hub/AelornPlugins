package eu.decentsoftware.holograms.api;

import eu.decentsoftware.holograms.api.animations.AnimationManager;
import eu.decentsoftware.holograms.api.commands.CommandManager;
import eu.decentsoftware.holograms.api.features.FeatureManager;
import eu.decentsoftware.holograms.api.holograms.HologramManager;
import eu.decentsoftware.holograms.api.utils.tick.Ticker;
import eu.decentsoftware.holograms.display.DisplayModule;
import eu.decentsoftware.holograms.integration.IntegrationAvailabilityService;
import eu.decentsoftware.holograms.nms.NmsPacketListenerService;
import eu.decentsoftware.holograms.nms.api.NmsAdapter;
import java.io.File;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import tw.linsy.aelornholograms.AelornHologramsPlugin;

public final class DecentHolograms {
   private final JavaPlugin plugin;
   private final HologramManager hologramManager = new HologramManager();

   DecentHolograms(JavaPlugin var1) {
      this.plugin = var1;
   }

   void enable() {
   }

   void disable() {
   }

   public void reload() {
      AelornHologramsPlugin var1 = AelornHologramsPlugin.instance();
      if (var1 != null && var1.isEnabled()) {
         var1.reloadConfig();
         var1.hologramManager().reload();
      }

   }

   public File getDataFolder() {
      return this.plugin == null ? new File("plugins/AelornHolograms") : this.plugin.getDataFolder();
   }

   public Logger getLogger() {
      return this.plugin == null ? Logger.getLogger("AelornHolograms") : this.plugin.getLogger();
   }

   public JavaPlugin getPlugin() {
      return this.plugin;
   }

   public NmsAdapter getNmsAdapter() {
      return null;
   }

   public IntegrationAvailabilityService getIntegrationAvailabilityService() {
      return null;
   }

   public NmsPacketListenerService getNmsPacketListenerService() {
      return null;
   }

   public HologramManager getHologramManager() {
      return this.hologramManager;
   }

   public CommandManager getCommandManager() {
      return null;
   }

   public FeatureManager getFeatureManager() {
      return null;
   }

   public AnimationManager getAnimationManager() {
      return null;
   }

   public Ticker getTicker() {
      return null;
   }

   public DisplayModule getDisplayModule() {
      return null;
   }

   public boolean isUpdateAvailable() {
      return false;
   }
}
