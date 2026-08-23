package tw.linsy.aelorn.mmoitems.lore;

import org.bukkit.configuration.ConfigurationSection;
import tw.linsy.aelorn.mmoitems.config.TextBundle;

public final class LoreText {
   private final TextBundle text = new TextBundle();
   private final TextBundle pages = new TextBundle();

   public LoreText() {
   }

   public int load(ConfigurationSection var1) {
      return var1 == null ? this.text.load((ConfigurationSection)null) + this.pages.load((ConfigurationSection)null) : this.text.load(var1.getConfigurationSection("text")) + this.pages.load(var1.getConfigurationSection("pages"));
   }

   public String get(String var1, String var2) {
      return this.text.get(var1, var2);
   }

   public String page(String var1, String var2) {
      return this.pages.get(var1, var2);
   }

   public String format(String var1, String var2, Object... var3) {
      return this.text.format(var1, var2, var3);
   }

   public static String substitute(String var0, Object... var1) {
      return TextBundle.substitute(var0, var1);
   }
}
