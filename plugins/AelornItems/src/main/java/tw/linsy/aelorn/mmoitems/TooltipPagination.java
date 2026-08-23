package tw.linsy.aelorn.mmoitems;

public final class TooltipPagination {
   private TooltipPagination() {
   }

   public static int direction(boolean var0) {
      return 1;
   }

   public static int nextPage(int var0, int var1) {
      int var2 = Math.max(1, var1);
      return Math.floorMod(Math.max(0, var0) + 1, var2);
   }

   public static int nextPage(int var0, int var1, int var2) {
      return nextPage(var0, var1);
   }
}
