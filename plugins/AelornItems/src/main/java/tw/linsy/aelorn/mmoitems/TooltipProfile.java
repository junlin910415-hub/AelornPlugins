package tw.linsy.aelorn.mmoitems;

import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

record TooltipProfile(String id, String top, String middle, String bottom, List<String> header) {
   static TooltipProfile from(String var0, ConfigurationSection var1) {
      return new TooltipProfile(var0.toUpperCase(Locale.ROOT), var1.getString("top", ""), var1.getString("middle", ""), var1.getString("bottom", ""), List.copyOf(var1.getStringList("lore_header")));
   }

   boolean enabled() {
      return !this.top.isBlank() || !this.middle.isBlank() || !this.bottom.isBlank() || !this.header.isEmpty();
   }
}
