package eu.decentsoftware.holograms.api.holograms.enums;

public enum HologramLineType {
   UNKNOWN((double)0.0F, (double)0.0F),
   TEXT(0.3, (double)0.0F),
   HEAD(0.6, (double)0.0F),
   SMALLHEAD(0.3, (double)0.0F),
   ICON(0.6, (double)0.0F),
   ENTITY(0.8, (double)0.0F);

   private final double offsetY;
   private final double clickableOffsetY;

   private HologramLineType(double var3, double var5) {
      this.offsetY = var3;
      this.clickableOffsetY = var5;
   }

   public double getOffsetY() {
      return this.offsetY;
   }

   public double getClickableOffsetY() {
      return this.clickableOffsetY;
   }
}
