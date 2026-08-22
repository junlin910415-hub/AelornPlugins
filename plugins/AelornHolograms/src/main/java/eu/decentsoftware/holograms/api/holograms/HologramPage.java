package eu.decentsoftware.holograms.api.holograms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;

public class HologramPage {
   private final Hologram parent;
   private int index;

   public HologramPage(Hologram var1, int var2) {
      this.parent = var1;
      this.index = var2;
   }

   public Hologram getParent() {
      return this.parent;
   }

   public double getHeight() {
      return (double)this.parent.size() * 0.3;
   }

   public Location getCenter() {
      Location var1 = this.parent.getLocation();
      return var1.subtract((double)0.0F, this.getHeight() / (double)2.0F, (double)0.0F);
   }

   public int size() {
      return this.parent.size();
   }

   public Map<String, Object> serializeToMap() {
      LinkedHashMap var1 = new LinkedHashMap();
      var1.put("index", this.index);
      var1.put("lines", this.parent.getLines());
      return var1;
   }

   public HologramPage clone(Hologram var1, int var2) {
      var1.setLines(this.parent.getLines());
      return new HologramPage(var1, var2);
   }

   public void realignLines() {
      this.parent.realignLines();
   }

   public boolean addLine(HologramLine var1) {
      this.parent.addLine(var1 == null ? " " : var1.getContent());
      return true;
   }

   public boolean insertLine(int var1, HologramLine var2) {
      this.parent.insertLine(var1, var2 == null ? " " : var2.getContent());
      return true;
   }

   public boolean setLine(int var1, String var2) {
      this.parent.setLine(var1, var2);
      return true;
   }

   public HologramLine getLine(int var1) {
      if (var1 >= 0 && var1 < this.parent.size()) {
         return new HologramLine(this, var1);
      } else {
         throw new IllegalArgumentException("Line does not exist: " + var1);
      }
   }

   public HologramLine removeLine(int var1) {
      HologramLine var2 = this.getLine(var1);
      this.parent.removeLine(var1);
      return var2;
   }

   public boolean swapLines(int var1, int var2) {
      ArrayList var3 = new ArrayList(this.parent.getLines());
      if (var1 >= 0 && var2 >= 0 && var1 < var3.size() && var2 < var3.size()) {
         String var4 = (String)var3.get(var1);
         var3.set(var1, (String)var3.get(var2));
         var3.set(var2, var4);
         this.parent.setLines(var3);
         return true;
      } else {
         return false;
      }
   }

   public Location getNextLineLocation() {
      return this.parent.getLocation().subtract((double)0.0F, (double)this.parent.size() * 0.3, (double)0.0F);
   }

   public List<HologramLine> getLines() {
      ArrayList var1 = new ArrayList();

      for(int var2 = 0; var2 < this.parent.size(); ++var2) {
         var1.add(new HologramLine(this, var2));
      }

      return var1;
   }

   public boolean isClickable() {
      return false;
   }

   public int getClickableEntityId(int var1) {
      return -1;
   }

   public boolean hasEntity(int var1) {
      return var1 >= 0 && var1 < this.parent.size();
   }

   public boolean hasActions() {
      return false;
   }

   public int getIndex() {
      return this.index;
   }

   public void setIndex(int var1) {
      this.index = var1;
   }
}
