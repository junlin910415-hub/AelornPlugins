package eu.decentsoftware.holograms.api.holograms;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.actions.ClickType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import tw.linsy.aelornholograms.AelornHologramsPlugin;

public class HologramManager {
   public synchronized void tick() {
   }

   public void updateVisibility(Hologram var1) {
      if (var1 != null && var1.exists()) {
         var1.updateAll();
      }

   }

   public void updateVisibility(Player var1) {
   }

   public void updateVisibility(Player var1, Hologram var2) {
      this.updateVisibility(var2);
   }

   public HologramLine spawnTemporaryHologramLine(Location var1, String var2, long var3) {
      Hologram var5 = DHAPI.createHologram("temporary_" + String.valueOf(UUID.randomUUID()), var1, false, List.of(var2));
      return var5.getPage(0).getLine(0);
   }

   public boolean onClick(Player var1, int var2, ClickType var3) {
      return false;
   }

   public void onQuit(Player var1) {
   }

   public synchronized void reload() {
      plugin().hologramManager().reload();
   }

   public synchronized void destroy() {
      plugin().hologramManager().shutdown();
   }

   public void showAll(Player var1) {
   }

   public void hideAll(Player var1) {
   }

   public boolean containsHologram(String var1) {
      return plugin().hologramManager().hologram(var1).isPresent();
   }

   public void registerHologram(Hologram var1) {
      if (var1 != null && var1.exists()) {
         var1.updateAll();
      }

   }

   public Hologram getHologram(String var1) {
      return plugin().hologramManager().hologram(var1).isPresent() ? Hologram.wrap(var1) : null;
   }

   public Hologram removeHologram(String var1) {
      Hologram var2 = this.getHologram(var1);
      plugin().hologramManager().delete(var1);
      return var2;
   }

   public Set<String> getHologramNames() {
      return Set.copyOf(plugin().hologramManager().hologramNames());
   }

   public Collection<Hologram> getHolograms() {
      return plugin().hologramManager().hologramNames().stream().map(Hologram::wrap).toList();
   }

   public Map<String, Set<String>> getToLoad() {
      return Map.of();
   }

   private static AelornHologramsPlugin plugin() {
      AelornHologramsPlugin var0 = AelornHologramsPlugin.instance();
      if (var0 != null && var0.isEnabled()) {
         return var0;
      } else {
         throw new IllegalStateException("AelornHolograms is not enabled.");
      }
   }
}
