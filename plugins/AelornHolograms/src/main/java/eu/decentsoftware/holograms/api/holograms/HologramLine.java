package eu.decentsoftware.holograms.api.holograms;

import eu.decentsoftware.holograms.api.holograms.enums.HologramLineType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class HologramLine {
   private final HologramPage parent;
   private final int index;
   private final Location location;
   private String content;
   private boolean enabled = true;
   private double height = 0.3;

   public HologramLine(HologramPage var1, Location var2, String var3) {
      this.parent = var1;
      this.index = -1;
      this.location = var2 == null ? var1.getParent().getLocation() : var2.clone();
      this.content = var3 == null ? " " : var3;
   }

   public HologramLine(HologramPage var1, int var2) {
      this.parent = var1;
      this.index = var2;
      this.location = var1.getParent().getLocation().subtract((double)0.0F, (double)var2 * 0.3, (double)0.0F);
   }

   public void setContent(String var1) {
      this.content = var1 == null ? " " : var1;
      if (this.index >= 0) {
         this.parent.getParent().setLine(this.index, this.content);
      }

   }

   public void enable() {
      this.enabled = true;
   }

   public void disable() {
      this.enabled = false;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public boolean isDisabled() {
      return !this.enabled;
   }

   public void parseContent() {
   }

   public Map<String, Object> serializeToMap() {
      LinkedHashMap var1 = new LinkedHashMap();
      var1.put("content", this.getContent());
      var1.put("height", this.height);
      return var1;
   }

   public HologramLine clone(HologramPage var1, Location var2) {
      return new HologramLine(var1, var2, this.getContent());
   }

   public HologramLineType getType() {
      String var1 = this.getContent().toUpperCase();
      if (!var1.startsWith("#ICON:") && !var1.startsWith("#ITEM:") && !var1.startsWith("{ITEM:")) {
         return !var1.startsWith("#BLOCK:") && !var1.startsWith("{BLOCK:") ? HologramLineType.TEXT : HologramLineType.ENTITY;
      } else {
         return HologramLineType.ICON;
      }
   }

   public boolean hasPermission(Player var1) {
      return true;
   }

   public void updateVisibility(Player var1) {
   }

   public void show(Player... var1) {
   }

   public void update(Player... var1) {
      this.parent.getParent().updateAll();
   }

   public void update(boolean var1, Player... var2) {
      this.update(var2);
   }

   public void updateLocation(boolean var1, Player... var2) {
      this.update(var2);
   }

   public void updateAnimations(Player... var1) {
   }

   public void hide(Player... var1) {
   }

   public boolean isInDisplayRange(Player var1) {
      return this.parent.getParent().isInDisplayRange(var1);
   }

   public boolean isInUpdateRange(Player var1) {
      return this.parent.getParent().isInUpdateRange(var1);
   }

   public double getOffsetX() {
      return (double)0.0F;
   }

   public double getOffsetY() {
      return (double)0.0F;
   }

   public double getOffsetZ() {
      return (double)0.0F;
   }

   public void setOffsetX(double var1) {
   }

   public void setOffsetY(double var1) {
   }

   public void setOffsetZ(double var1) {
   }

   public boolean canShow(Player var1) {
      return true;
   }

   public int[] getEntityIds() {
      return new int[0];
   }

   public HologramPage getParent() {
      return this.parent;
   }

   public double getHeight() {
      return this.height;
   }

   public String getContent() {
      if (this.index >= 0 && this.index < this.parent.getParent().getLines().size()) {
         return (String)this.parent.getParent().getLines().get(this.index);
      } else {
         return this.content == null ? " " : this.content;
      }
   }

   public String getText() {
      return this.getContent();
   }

   public void setType(HologramLineType var1) {
   }

   public void setHeight(double var1) {
      this.height = var1;
   }

   public void setText(String var1) {
      this.setContent(var1);
   }

   public void setContainsAnimations(boolean var1) {
   }

   public void setContainsPlaceholders(boolean var1) {
   }

   public boolean isContainsAnimations() {
      return false;
   }

   public boolean isContainsPlaceholders() {
      return this.getContent().contains("%");
   }

   public Location getLocation() {
      return this.location.clone();
   }

   public void setLocation(Location var1) {
   }

   public void delete() {
      if (this.index >= 0) {
         this.parent.getParent().removeLine(this.index);
      }

   }

   public void destroy() {
      this.delete();
   }

   public String toString() {
      return this.getContent();
   }
}
