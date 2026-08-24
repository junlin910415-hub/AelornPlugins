package tw.linsy.aelorn.rpgcore.integration.nexo;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public final class CustomItemProviders {
   private CustomItemProviders() {
   }

   public static CustomItemProvider create(PluginManager pluginManager, boolean enabled, Logger logger) {
      if (!enabled) {
         return CustomItemProvider.empty();
      }

      Plugin nexo = pluginManager.getPlugin("Nexo");
      if (nexo == null || !nexo.isEnabled()) {
         logger.warning("Nexo was not detected; RPGCore will use vanilla item fallbacks.");
         return CustomItemProvider.empty();
      }

      try {
         return new NexoCustomItemProvider();
      } catch (RuntimeException | LinkageError exception) {
         logger.log(Level.WARNING, "Could not initialize Nexo item integration; using vanilla items", exception);
         return CustomItemProvider.empty();
      }
   }
}

