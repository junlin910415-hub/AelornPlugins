package tw.linsy.aelorn.rpgcore.hud;

import tw.linsy.aelorn.rpgcore.combat.CombatHudSnapshot;
import org.bukkit.entity.Player;

public interface CombatHudRenderer extends AutoCloseable {
   void render(Player var1, CombatHudSnapshot var2);

   void hide(Player var1);

   boolean isEnabled(Player var1);

   void setEnabled(Player var1, boolean var2);

   Status status(Player var1);

   void close();

   public static record Status(String renderer, boolean enabled, boolean visible, String resourcePackState) {
   }
}
