package eu.decentsoftware.holograms.api;

import eu.decentsoftware.holograms.api.holograms.Hologram;
import eu.decentsoftware.holograms.api.holograms.HologramLine;
import eu.decentsoftware.holograms.api.holograms.HologramPage;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import tw.linsy.aelornholograms.AelornHologramsPlugin;

public final class DHAPI {
   private DHAPI() {
   }

   public static Hologram createHologram(String var0, Location var1) throws IllegalArgumentException {
      return createHologram(var0, var1, true, List.of(" "));
   }

   public static Hologram createHologram(String var0, Location var1, boolean var2) throws IllegalArgumentException {
      return createHologram(var0, var1, var2, List.of(" "));
   }

   public static Hologram createHologram(String var0, Location var1, List<String> var2) throws IllegalArgumentException {
      return createHologram(var0, var1, true, var2);
   }

   public static Hologram createHologram(String var0, Location var1, boolean var2, List<String> var3) throws IllegalArgumentException {
      plugin().hologramManager().create(var0, var1, var3 != null && !var3.isEmpty() ? var3 : List.of(" "));
      Hologram var4 = Hologram.wrap(var0);
      var4.setSaveToFile(var2);
      return var4;
   }

   public static void moveHologram(String var0, Location var1) throws IllegalArgumentException {
      if (!plugin().hologramManager().move(var0, var1)) {
         throw new IllegalArgumentException("Hologram does not exist: " + var0);
      }
   }

   public static void moveHologram(Hologram var0, Location var1) throws IllegalArgumentException {
      require(var0).setLocation(var1);
   }

   public static void updateHologram(String var0) {
      plugin().hologramManager().refresh(var0);
   }

   public static void removeHologram(String var0) {
      plugin().hologramManager().delete(var0);
   }

   public static Hologram getHologram(String var0) throws IllegalArgumentException {
      return plugin().hologramManager().hologram(var0).isPresent() ? Hologram.wrap(var0) : null;
   }

   public static HologramLine createHologramLine(HologramPage var0, String var1) throws IllegalArgumentException {
      return addHologramLine(var0, var1);
   }

   public static HologramLine createHologramLine(HologramPage var0, Location var1, String var2) throws IllegalArgumentException {
      return addHologramLine(var0, var2);
   }

   public static HologramPage addHologramPage(Hologram var0) throws IllegalArgumentException {
      return require(var0).addPage();
   }

   public static HologramPage addHologramPage(Hologram var0, List<String> var1) throws IllegalArgumentException {
      HologramPage var2 = require(var0).addPage();
      if (var1 != null) {
         require(var0).setLines(var1);
      }

      return var2;
   }

   public static HologramPage insertHologramPage(Hologram var0, int var1) throws IllegalArgumentException {
      return require(var0).insertPage(var1);
   }

   public static HologramPage insertHologramPage(Hologram var0, int var1, List<String> var2) throws IllegalArgumentException {
      HologramPage var3 = require(var0).insertPage(var1);
      if (var2 != null) {
         require(var0).setLines(var2);
      }

      return var3;
   }

   public static HologramPage removeHologramPage(Hologram var0, int var1) throws IllegalArgumentException {
      return require(var0).removePage(var1);
   }

   public static HologramPage getHologramPage(Hologram var0, int var1) throws IllegalArgumentException {
      return require(var0).getPage(var1);
   }

   public static HologramLine getHologramLine(HologramPage var0, int var1) throws IllegalArgumentException {
      return require(var0).getLine(var1);
   }

   public static HologramLine addHologramLine(Hologram var0, Material var1) throws IllegalArgumentException {
      return addHologramLine(var0, "#ICON:" + var1.name());
   }

   public static HologramLine addHologramLine(Hologram var0, ItemStack var1) throws IllegalArgumentException {
      Material var2 = var1 == null ? Material.STONE : var1.getType();
      return addHologramLine(var0, "#ICON:" + var2.name());
   }

   public static HologramLine addHologramLine(Hologram var0, String var1) throws IllegalArgumentException {
      Hologram var2 = require(var0);
      int var3 = var2.size();
      var2.addLine(var1);
      return new HologramLine(var2.getPage(0), var3);
   }

   public static HologramLine addHologramLine(Hologram var0, int var1, String var2) throws IllegalArgumentException {
      return addHologramLine(var0, var2);
   }

   public static HologramLine addHologramLine(HologramPage var0, Material var1) throws IllegalArgumentException {
      return addHologramLine(var0, "#ICON:" + var1.name());
   }

   public static HologramLine addHologramLine(HologramPage var0, ItemStack var1) throws IllegalArgumentException {
      Material var2 = var1 == null ? Material.STONE : var1.getType();
      return addHologramLine(var0, "#ICON:" + var2.name());
   }

   public static HologramLine addHologramLine(HologramPage var0, String var1) throws IllegalArgumentException {
      HologramPage var2 = require(var0);
      int var3 = var2.size();
      var2.getParent().addLine(var1);
      return new HologramLine(var2, var3);
   }

   public static HologramLine insertHologramLine(Hologram var0, int var1, Material var2) throws IllegalArgumentException {
      return insertHologramLine(var0, var1, "#ICON:" + var2.name());
   }

   public static HologramLine insertHologramLine(Hologram var0, int var1, ItemStack var2) throws IllegalArgumentException {
      Material var3 = var2 == null ? Material.STONE : var2.getType();
      return insertHologramLine(var0, var1, "#ICON:" + var3.name());
   }

   public static HologramLine insertHologramLine(Hologram var0, int var1, String var2) throws IllegalArgumentException {
      Hologram var3 = require(var0);
      var3.insertLine(var1, var2);
      return new HologramLine(var3.getPage(0), var1);
   }

   public static HologramLine insertHologramLine(Hologram var0, int var1, int var2, Material var3) throws IllegalArgumentException {
      return insertHologramLine(var0, var2, "#ICON:" + var3.name());
   }

   public static HologramLine insertHologramLine(Hologram var0, int var1, int var2, ItemStack var3) throws IllegalArgumentException {
      Material var4 = var3 == null ? Material.STONE : var3.getType();
      return insertHologramLine(var0, var2, "#ICON:" + var4.name());
   }

   public static HologramLine insertHologramLine(Hologram var0, int var1, int var2, String var3) throws IllegalArgumentException {
      return insertHologramLine(var0, var2, var3);
   }

   public static HologramLine insertHologramLine(HologramPage var0, int var1, Material var2) throws IllegalArgumentException {
      return insertHologramLine(require(var0).getParent(), var1, "#ICON:" + var2.name());
   }

   public static HologramLine insertHologramLine(HologramPage var0, int var1, ItemStack var2) throws IllegalArgumentException {
      Material var3 = var2 == null ? Material.STONE : var2.getType();
      return insertHologramLine(require(var0).getParent(), var1, "#ICON:" + var3.name());
   }

   public static HologramLine insertHologramLine(HologramPage var0, int var1, String var2) throws IllegalArgumentException {
      return insertHologramLine(require(var0).getParent(), var1, var2);
   }

   public static void setHologramLine(HologramLine var0, Material var1) throws IllegalArgumentException {
      setHologramLine(var0, "#ICON:" + var1.name());
   }

   public static void setHologramLine(HologramLine var0, ItemStack var1) throws IllegalArgumentException {
      Material var2 = var1 == null ? Material.STONE : var1.getType();
      setHologramLine(var0, "#ICON:" + var2.name());
   }

   public static void setHologramLine(HologramLine var0, String var1) throws IllegalArgumentException {
      require(var0).setContent(var1);
   }

   public static void setHologramLine(HologramPage var0, int var1, Material var2) throws IllegalArgumentException {
      setHologramLine(var0, var1, "#ICON:" + var2.name());
   }

   public static void setHologramLine(HologramPage var0, int var1, ItemStack var2) throws IllegalArgumentException {
      Material var3 = var2 == null ? Material.STONE : var2.getType();
      setHologramLine(var0, var1, "#ICON:" + var3.name());
   }

   public static void setHologramLine(HologramPage var0, int var1, String var2) throws IllegalArgumentException {
      require(var0).setLine(var1, var2);
   }

   public static void setHologramLine(Hologram var0, int var1, Material var2) throws IllegalArgumentException {
      setHologramLine(var0, var1, "#ICON:" + var2.name());
   }

   public static void setHologramLine(Hologram var0, int var1, ItemStack var2) throws IllegalArgumentException {
      Material var3 = var2 == null ? Material.STONE : var2.getType();
      setHologramLine(var0, var1, "#ICON:" + var3.name());
   }

   public static void setHologramLine(Hologram var0, int var1, String var2) throws IllegalArgumentException {
      require(var0).setLine(var1, var2);
   }

   public static void setHologramLine(Hologram var0, int var1, int var2, Material var3) throws IllegalArgumentException {
      setHologramLine(var0, var2, "#ICON:" + var3.name());
   }

   public static void setHologramLine(Hologram var0, int var1, int var2, ItemStack var3) throws IllegalArgumentException {
      Material var4 = var3 == null ? Material.STONE : var3.getType();
      setHologramLine(var0, var2, "#ICON:" + var4.name());
   }

   public static void setHologramLine(Hologram var0, int var1, int var2, String var3) throws IllegalArgumentException {
      setHologramLine(var0, var2, var3);
   }

   public static HologramLine removeHologramLine(Hologram var0, int var1) throws IllegalArgumentException {
      Hologram var2 = require(var0);
      HologramLine var3 = new HologramLine(var2.getPage(0), var1);
      var2.removeLine(var1);
      return var3;
   }

   public static HologramLine removeHologramLine(Hologram var0, int var1, int var2) throws IllegalArgumentException {
      return removeHologramLine(var0, var2);
   }

   public static HologramLine removeHologramLine(HologramPage var0, int var1) throws IllegalArgumentException {
      return removeHologramLine(require(var0).getParent(), var1);
   }

   public static void setHologramLines(Hologram var0, List<String> var1) throws IllegalArgumentException {
      require(var0).setLines(var1);
   }

   public static void setHologramLines(Hologram var0, int var1, List<String> var2) throws IllegalArgumentException {
      setHologramLines(var0, var2);
   }

   private static AelornHologramsPlugin plugin() {
      AelornHologramsPlugin var0 = AelornHologramsPlugin.instance();
      if (var0 != null && var0.isEnabled()) {
         return var0;
      } else {
         throw new IllegalStateException("AelornHolograms is not enabled.");
      }
   }

   private static Hologram require(Hologram var0) {
      if (var0 != null && var0.exists()) {
         return var0;
      } else {
         throw new IllegalArgumentException("Hologram does not exist.");
      }
   }

   private static HologramPage require(HologramPage var0) {
      if (var0 != null && var0.getParent() != null && var0.getParent().exists()) {
         return var0;
      } else {
         throw new IllegalArgumentException("Hologram page does not exist.");
      }
   }

   private static HologramLine require(HologramLine var0) {
      if (var0 != null && var0.getParent() != null && var0.getParent().getParent().exists()) {
         return var0;
      } else {
         throw new IllegalArgumentException("Hologram line does not exist.");
      }
   }
}
