package tw.linsy.aelorn.rpgcore.party;

import java.util.Objects;
import java.util.UUID;

public record PartyInvite(UUID partyId, UUID inviterId, UUID targetId, long expiresAtMillis) {
   public PartyInvite {
      Objects.requireNonNull(partyId, "partyId");
      Objects.requireNonNull(inviterId, "inviterId");
      Objects.requireNonNull(targetId, "targetId");
   }

   public boolean expired(long nowMillis) {
      return nowMillis >= this.expiresAtMillis;
   }
}
