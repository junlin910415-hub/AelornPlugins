package eu.decentsoftware.holograms.api.holograms;

import eu.decentsoftware.holograms.api.DHAPI;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import tw.linsy.aelornholograms.AelornHologramsPlugin;

public class Hologram {
   private final String name;
   private boolean saveToFile;

   public Hologram(String var1, Location var2) {
      this(var1, var2, true);
   }

   public Hologram(String var1, Location var2, boolean var3) {
      this.saveToFile = true;
      this.name = var1;
      this.saveToFile = var3;
      AelornHologramsPlugin var4 = plugin();
      if (var4.hologramManager().hologram(var1).isEmpty()) {
         var4.hologramManager().create(var1, var2, List.of(" "));
      }

   }

   private Hologram(String var1) {
      this.saveToFile = true;
      this.name = var1;
   }

   public static Hologram wrap(String var0) {
      return new Hologram(var0);
   }

   public static Hologram getCachedHologram(String var0) {
      return plugin().hologramManager().hologram(var0).isPresent() ? wrap(var0) : null;
   }

   public static Set<String> getCachedHologramNames() {
      return Set.copyOf(plugin().hologramManager().hologramNames());
   }

   public static Collection<Hologram> getCachedHolograms() {
      return plugin().hologramManager().hologramNames().stream().map(Hologram::wrap).toList();
   }

   public boolean exists() {
      return plugin().hologramManager().hologram(this.name).isPresent();
   }

   public String getId() {
      return this.name;
   }

   public String getName() {
      return this.name;
   }

   public Location getLocation() {
      return this.raw().location().clone();
   }

   public void setLocation(Location var1) {
      plugin().hologramManager().move(this.name, var1);
   }

   public int size() {
      return this.raw().lines().size();
   }

   public void save() {
      plugin().store().save(this.raw());
   }

   public void delete() {
      plugin().hologramManager().delete(this.name);
   }

   public void destroy() {
      this.delete();
   }

   public void enable() {
      this.raw().enabled(true);
      this.save();
      plugin().hologramManager().refresh(this.name);
   }

   public void disable(DisableCause var1) {
      this.raw().enabled(false);
      this.save();
      plugin().hologramManager().refresh(this.name);
   }

   public void disable() {
      this.disable(DisableCause.API);
   }

   public boolean isEnabled() {
      return this.raw().enabled();
   }

   public boolean isDisabled() {
      return !this.isEnabled();
   }

   public void setFacing(float var1) {
      Location var2 = this.getLocation();
      var2.setYaw(var1);
      this.setLocation(var2);
   }

   public boolean isVisibleState() {
      return this.isEnabled();
   }

   public void setHidePlayer(Player var1) {
   }

   public void removeHidePlayer(Player var1) {
   }

   public boolean isHideState(Player var1) {
      return false;
   }

   public void setShowPlayer(Player var1) {
   }

   public void removeShowPlayer(Player var1) {
   }

   public boolean isShowState(Player var1) {
      return true;
   }

   public boolean show(Player var1, int var2) {
      return true;
   }

   public void showAll() {
   }

   public void update(Player var1) {
      this.updateAll();
   }

   public void update(boolean var1, Player var2) {
      this.updateAll();
   }

   public void updateAll() {
      plugin().hologramManager().refresh(this.name);
   }

   public void updateAll(boolean var1) {
      this.updateAll();
   }

   public void updateAnimations(Player var1) {
   }

   public void updateAnimationsAll() {
   }

   public void hide(Player var1) {
   }

   public void hideAll() {
   }

   public boolean isInDisplayRange(Player var1) {
      return this.isInRange(var1, this.raw().displayRange());
   }

   public boolean isInUpdateRange(Player var1) {
      return this.isInRange(var1, this.raw().updateRange());
   }

   public void setDownOrigin(boolean var1) {
   }

   public int getPlayerPage(Player var1) {
      return 0;
   }

   public List<Player> getViewerPlayers(int var1) {
      return List.of();
   }

   public void realignLines() {
      this.updateAll();
   }

   public HologramPage addPage() {
      return this.getPage(0);
   }

   public HologramPage insertPage(int var1) {
      return this.getPage(0);
   }

   public HologramPage getPage(int var1) {
      if (var1 != 0) {
         throw new IllegalArgumentException("AelornHolograms compatibility bridge supports page 0.");
      } else {
         return new HologramPage(this, 0);
      }
   }

   public HologramPage getPage(Player var1) {
      return this.getPage(0);
   }

   public HologramPage removePage(int var1) {
      HologramPage var2 = this.getPage(0);
      this.setLines(List.of(" "));
      return var2;
   }

   public boolean swapPages(int var1, int var2) {
      return var1 == var2;
   }

   public List<HologramPage> getPages() {
      return List.of(this.getPage(0));
   }

   public List<String> getLines() {
      return List.copyOf(this.raw().lines());
   }

   public void setLines(List<String> var1) {
      plugin().hologramManager().setLines(this.name, var1 != null && !var1.isEmpty() ? var1 : List.of(" "));
   }

   public void addLine(String var1) {
      plugin().hologramManager().addLine(this.name, var1 == null ? " " : var1);
   }

   public void insertLine(int var1, String var2) {
      plugin().hologramManager().insertLine(this.name, var1, var2 == null ? " " : var2);
   }

   public void setLine(int var1, String var2) {
      if (!plugin().hologramManager().setLine(this.name, var1, var2 == null ? " " : var2)) {
         throw new IllegalArgumentException("Line does not exist: " + var1);
      }
   }

   public void removeLine(int var1) {
      if (!plugin().hologramManager().removeLine(this.name, var1)) {
         throw new IllegalArgumentException("Line does not exist: " + var1);
      }
   }

   public boolean isSaveToFile() {
      return this.saveToFile;
   }

   public void setSaveToFile(boolean var1) {
      this.saveToFile = var1;
   }

   public boolean isDefaultVisibleState() {
      return true;
   }

   public void setDefaultVisibleState(boolean var1) {
   }

   public boolean isDownOrigin() {
      return false;
   }

   public boolean isAlwaysFacePlayer() {
      return true;
   }

   public void setAlwaysFacePlayer(boolean var1) {
   }

   public int getDisplayRange() {
      return this.raw().displayRange();
   }

   public int getUpdateRange() {
      return this.raw().updateRange();
   }

   public int getUpdateInterval() {
      return this.raw().updateInterval();
   }

   public void setDisplayRange(int var1) {
      this.raw().displayRange(var1);
      this.save();
      this.updateAll();
   }

   public void setUpdateRange(int var1) {
      this.raw().updateRange(var1);
      this.save();
   }

   public void setUpdateInterval(int var1) {
      this.raw().updateInterval(var1);
      this.save();
      this.updateAll();
   }

   public AtomicInteger getTickCounter() {
      return new AtomicInteger(0);
   }

   public Hologram clone(String var1, Location var2, boolean var3) {
      return DHAPI.createHologram(var1, var2, var3, this.getLines());
   }

   public String toString() {
      return "Hologram{name='" + this.name + "'}";
   }

   private boolean isInRange(Player var1, int var2) {
      if (var1 != null && var1.getWorld() != null && this.raw().location().getWorld() != null) {
         return var1.getWorld().equals(this.raw().location().getWorld()) && var1.getLocation().distanceSquared(this.raw().location()) <= (double)var2 * (double)var2;
      } else {
         return false;
      }
   }

   private tw.linsy.aelornholograms.Hologram raw() {
      return (tw.linsy.aelornholograms.Hologram)plugin().hologramManager().hologram(this.name).orElseThrow(() -> new IllegalArgumentException("Hologram does not exist: " + this.name));
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
