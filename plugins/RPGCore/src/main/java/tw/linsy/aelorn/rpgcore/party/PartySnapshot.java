package tw.linsy.aelorn.rpgcore.party;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PartySnapshot(UUID id, UUID leaderId, List<UUID> members, boolean listed, long createdAtMillis) {
   public PartySnapshot(UUID id, UUID leaderId, List<UUID> members, boolean listed, long createdAtMillis) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(leaderId, "leaderId");
      members = List.copyOf(members);
      if (!members.isEmpty() && members.contains(leaderId)) {
         this.id = id;
         this.leaderId = leaderId;
         this.members = members;
         this.listed = listed;
         this.createdAtMillis = createdAtMillis;
      } else {
         throw new IllegalArgumentException("A party must contain its leader");
      }
   }

   public boolean isLeader(UUID playerId) {
      return this.leaderId.equals(playerId);
   }

   public boolean contains(UUID playerId) {
      return this.members.contains(playerId);
   }
}
