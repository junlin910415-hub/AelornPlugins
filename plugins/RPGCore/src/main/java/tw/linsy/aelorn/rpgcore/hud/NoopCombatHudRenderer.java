package tw.linsy.aelorn.rpgcore.hud;

import tw.linsy.aelorn.rpgcore.combat.CombatHudSnapshot;
import org.bukkit.entity.Player;

public final class NoopCombatHudRenderer implements CombatHudRenderer {
   public void render(Player player, CombatHudSnapshot snapshot) {
   }

   public void hide(Player player) {
   }

   public boolean isEnabled(Player player) {
      return false;
   }

   public void setEnabled(Player player, boolean enabled) {
   }

   public CombatHudRenderer.Status status(Player player) {
      return new CombatHudRenderer.Status("native", false, false, "not-required");
   }

   public void close() {
   }
}
