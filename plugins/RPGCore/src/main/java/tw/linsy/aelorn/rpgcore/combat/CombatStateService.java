package tw.linsy.aelorn.rpgcore.combat;

import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.combat.CombatStats;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatStateService {
   private final Map<UUID, PlayerCombatState> states = new ConcurrentHashMap();

   public PlayerCombatState activate(UUID playerId, CharacterProfile character, CombatStats stats) {
      PlayerCombatState current = (PlayerCombatState)this.states.get(playerId);
      if (current != null && current.characterId().equals(character.id())) {
         current.resizeMaximumMana(stats.maximumMana());
         return current;
      } else {
         PlayerCombatState created = new PlayerCombatState(character.id(), stats.maximumMana());
         this.states.put(playerId, created);
         return created;
      }
   }

   public void refresh(UUID playerId, CharacterProfile character, CombatStats stats) {
      PlayerCombatState current = (PlayerCombatState)this.states.get(playerId);
      if (current != null && current.characterId().equals(character.id())) {
         current.resizeMaximumMana(stats.maximumMana());
      }

   }

   public Optional<PlayerCombatState> state(UUID playerId) {
      return Optional.ofNullable((PlayerCombatState)this.states.get(playerId));
   }

   public void remove(UUID playerId) {
      this.states.remove(playerId);
   }

   public void clear() {
      this.states.clear();
   }
}
