package eu.decentsoftware.holograms.api;

import org.bukkit.plugin.java.JavaPlugin;
import tw.linsy.aelornholograms.AelornHologramsPlugin;

public final class DecentHologramsAPI {
   private static DecentHolograms api;
   private static boolean running;

   private DecentHologramsAPI() {
   }

   public static void onLoad(JavaPlugin var0) {
      api = new DecentHolograms(var0);
   }

   public static void onEnable() {
      running = true;
      if (api == null && AelornHologramsPlugin.instance() != null) {
         api = new DecentHolograms(AelornHologramsPlugin.instance());
      }

   }

   public static void onDisable() {
      running = false;
   }

   public static boolean isRunning() {
      AelornHologramsPlugin var0 = AelornHologramsPlugin.instance();
      return running || var0 != null && var0.isEnabled();
   }

   public static DecentHolograms get() {
      if (api == null && AelornHologramsPlugin.instance() != null) {
         api = new DecentHolograms(AelornHologramsPlugin.instance());
      }

      return api;
   }
}
