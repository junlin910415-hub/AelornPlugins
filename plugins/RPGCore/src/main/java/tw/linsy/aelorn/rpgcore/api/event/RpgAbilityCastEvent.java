package tw.linsy.aelorn.rpgcore.api.event;

import tw.linsy.aelorn.rpgcore.domain.ability.AbilityDefinition;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class RpgAbilityCastEvent extends Event implements Cancellable {
   private static final HandlerList HANDLERS = new HandlerList();
   private final Player player;
   private final CharacterProfile character;
   private final AbilityDefinition ability;
   private boolean cancelled;

   public RpgAbilityCastEvent(Player player, CharacterProfile character, AbilityDefinition ability) {
      this.player = player;
      this.character = character;
      this.ability = ability;
   }

   public Player player() {
      return this.player;
   }

   public CharacterProfile character() {
      return this.character;
   }

   public AbilityDefinition ability() {
      return this.ability;
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancelled) {
      this.cancelled = cancelled;
   }

   public @NotNull HandlerList getHandlers() {
      return HANDLERS;
   }

   public static @NotNull HandlerList getHandlerList() {
      return HANDLERS;
   }
}
