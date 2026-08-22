package com.xuzhihuanjing.rpgcore.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PartyService {
   private final int maximumMembers;
   private final long inviteLifetimeMillis;
   private final boolean finderEnabled;
   private final Map<UUID, PartyState> parties = new LinkedHashMap();
   private final Map<UUID, UUID> partyByMember = new HashMap();
   private final Map<UUID, Map<UUID, PartyInvite>> invitesByTarget = new HashMap();

   public PartyService(int maximumMembers, long inviteLifetimeSeconds, boolean finderEnabled) {
      if (maximumMembers < 2) {
         throw new IllegalArgumentException("maximumMembers must be at least 2");
      } else if (inviteLifetimeSeconds < 1L) {
         throw new IllegalArgumentException("inviteLifetimeSeconds must be positive");
      } else {
         this.maximumMembers = maximumMembers;
         this.inviteLifetimeMillis = Math.multiplyExact(inviteLifetimeSeconds, 1000L);
         this.finderEnabled = finderEnabled;
      }
   }

   public int maximumMembers() {
      return this.maximumMembers;
   }

   public boolean finderEnabled() {
      return this.finderEnabled;
   }

   public synchronized Optional<PartySnapshot> partyOf(UUID memberId) {
      Objects.requireNonNull(memberId, "memberId");
      UUID partyId = (UUID)this.partyByMember.get(memberId);
      PartyState party = partyId == null ? null : (PartyState)this.parties.get(partyId);
      return party == null ? Optional.empty() : Optional.of(party.snapshot());
   }

   public synchronized Operation create(UUID leaderId, long nowMillis) {
      Objects.requireNonNull(leaderId, "leaderId");
      PartySnapshot current = (PartySnapshot)this.partyOf(leaderId).orElse(null);
      if (current != null) {
         return new Operation(PartyService.Result.ALREADY_IN_PARTY, current);
      } else {
         UUID partyId = UUID.randomUUID();
         PartyState party = new PartyState(partyId, leaderId, nowMillis);
         this.parties.put(partyId, party);
         this.partyByMember.put(leaderId, partyId);
         this.clearInvitesFor(leaderId);
         return new Operation(PartyService.Result.SUCCESS, party.snapshot());
      }
   }

   public synchronized Operation invite(UUID inviterId, UUID targetId, long nowMillis) {
      Objects.requireNonNull(inviterId, "inviterId");
      Objects.requireNonNull(targetId, "targetId");
      this.cleanupExpired(nowMillis);
      PartyState party = this.stateOfMember(inviterId);
      if (party == null) {
         return new Operation(PartyService.Result.NOT_IN_PARTY, (PartySnapshot)null);
      } else if (!party.leaderId.equals(inviterId)) {
         return new Operation(PartyService.Result.NOT_LEADER, party.snapshot());
      } else if (!inviterId.equals(targetId) && !party.members.contains(targetId)) {
         if (this.partyByMember.containsKey(targetId)) {
            return new Operation(PartyService.Result.TARGET_IN_PARTY, party.snapshot());
         } else if (party.members.size() >= this.maximumMembers) {
            return new Operation(PartyService.Result.PARTY_FULL, party.snapshot());
         } else {
            PartyInvite invite = new PartyInvite(party.id, inviterId, targetId, this.saturatingAdd(nowMillis, this.inviteLifetimeMillis));
            ((Map)this.invitesByTarget.computeIfAbsent(targetId, (ignored) -> new LinkedHashMap())).put(party.id, invite);
            return new Operation(PartyService.Result.SUCCESS, party.snapshot());
         }
      } else {
         return new Operation(PartyService.Result.ALREADY_IN_PARTY, party.snapshot());
      }
   }

   public synchronized Operation accept(UUID targetId, UUID partyId, long nowMillis) {
      Objects.requireNonNull(targetId, "targetId");
      Objects.requireNonNull(partyId, "partyId");
      this.cleanupExpired(nowMillis);
      if (this.partyByMember.containsKey(targetId)) {
         return new Operation(PartyService.Result.ALREADY_IN_PARTY, (PartySnapshot)this.partyOf(targetId).orElse(null));
      } else {
         PartyInvite invite = (PartyInvite)Optional.ofNullable((Map)this.invitesByTarget.get(targetId)).map((invites) -> (PartyInvite)invites.get(partyId)).orElse(null);
         return invite != null && !invite.expired(nowMillis) ? this.join(targetId, partyId, false) : new Operation(PartyService.Result.INVITE_EXPIRED, (PartySnapshot)null);
      }
   }

   public synchronized Operation joinListed(UUID targetId, UUID partyId) {
      Objects.requireNonNull(targetId, "targetId");
      Objects.requireNonNull(partyId, "partyId");
      return !this.finderEnabled ? new Operation(PartyService.Result.FINDER_DISABLED, (PartySnapshot)null) : this.join(targetId, partyId, true);
   }

   public synchronized Result decline(UUID targetId, UUID partyId) {
      Map<UUID, PartyInvite> invites = (Map)this.invitesByTarget.get(targetId);
      if (invites != null && invites.remove(partyId) != null) {
         if (invites.isEmpty()) {
            this.invitesByTarget.remove(targetId);
         }

         return PartyService.Result.SUCCESS;
      } else {
         return PartyService.Result.INVITE_EXPIRED;
      }
   }

   public synchronized Operation leave(UUID memberId) {
      Objects.requireNonNull(memberId, "memberId");
      PartyState party = this.stateOfMember(memberId);
      if (party == null) {
         return new Operation(PartyService.Result.NOT_IN_PARTY, (PartySnapshot)null);
      } else {
         party.members.remove(memberId);
         this.partyByMember.remove(memberId);
         this.clearInvitesFor(memberId);
         if (party.members.isEmpty()) {
            this.parties.remove(party.id);
            this.removePartyInvites(party.id);
            return new Operation(PartyService.Result.PARTY_DISBANDED, (PartySnapshot)null);
         } else {
            if (party.leaderId.equals(memberId)) {
               party.leaderId = (UUID)party.members.iterator().next();
            }

            return new Operation(PartyService.Result.SUCCESS, party.snapshot());
         }
      }
   }

   public synchronized Operation kick(UUID actorId, UUID targetId) {
      PartyState party = this.stateOfMember(actorId);
      if (party == null) {
         return new Operation(PartyService.Result.NOT_IN_PARTY, (PartySnapshot)null);
      } else if (!party.leaderId.equals(actorId)) {
         return new Operation(PartyService.Result.NOT_LEADER, party.snapshot());
      } else if (!actorId.equals(targetId) && party.members.contains(targetId)) {
         party.members.remove(targetId);
         this.partyByMember.remove(targetId);
         this.clearInvitesFor(targetId);
         return new Operation(PartyService.Result.SUCCESS, party.snapshot());
      } else {
         return new Operation(PartyService.Result.TARGET_NOT_IN_PARTY, party.snapshot());
      }
   }

   public synchronized Operation promote(UUID actorId, UUID targetId) {
      PartyState party = this.stateOfMember(actorId);
      if (party == null) {
         return new Operation(PartyService.Result.NOT_IN_PARTY, (PartySnapshot)null);
      } else if (!party.leaderId.equals(actorId)) {
         return new Operation(PartyService.Result.NOT_LEADER, party.snapshot());
      } else if (!party.members.contains(targetId)) {
         return new Operation(PartyService.Result.TARGET_NOT_IN_PARTY, party.snapshot());
      } else {
         party.leaderId = targetId;
         return new Operation(PartyService.Result.SUCCESS, party.snapshot());
      }
   }

   public synchronized Operation disband(UUID actorId) {
      PartyState party = this.stateOfMember(actorId);
      if (party == null) {
         return new Operation(PartyService.Result.NOT_IN_PARTY, (PartySnapshot)null);
      } else if (!party.leaderId.equals(actorId)) {
         return new Operation(PartyService.Result.NOT_LEADER, party.snapshot());
      } else {
         this.parties.remove(party.id);
         LinkedHashSet var10000 = party.members;
         var var10001 = this.partyByMember;
         Objects.requireNonNull(var10001);
         var10000.forEach(var10001::remove);
         this.removePartyInvites(party.id);
         return new Operation(PartyService.Result.PARTY_DISBANDED, (PartySnapshot)null);
      }
   }

   public synchronized Operation toggleListing(UUID actorId) {
      PartyState party = this.stateOfMember(actorId);
      if (party == null) {
         return new Operation(PartyService.Result.NOT_IN_PARTY, (PartySnapshot)null);
      } else if (!this.finderEnabled) {
         return new Operation(PartyService.Result.FINDER_DISABLED, party.snapshot());
      } else if (!party.leaderId.equals(actorId)) {
         return new Operation(PartyService.Result.NOT_LEADER, party.snapshot());
      } else {
         party.listed = !party.listed;
         return new Operation(PartyService.Result.SUCCESS, party.snapshot());
      }
   }

   public synchronized List<PartySnapshot> listedParties() {
      return !this.finderEnabled ? List.of() : this.parties.values().stream().filter((party) -> party.listed && party.members.size() < this.maximumMembers).map(PartyState::snapshot).sorted(Comparator.comparingLong(PartySnapshot::createdAtMillis)).toList();
   }

   public synchronized List<PartyInvite> pendingInvites(UUID targetId, long nowMillis) {
      this.cleanupExpired(nowMillis);
      return (List)Optional.ofNullable((Map)this.invitesByTarget.get(targetId)).map(Map::values).map(ArrayList::new).map(List::copyOf).orElse(List.of());
   }

   public synchronized boolean sameParty(UUID first, UUID second) {
      UUID partyId = (UUID)this.partyByMember.get(first);
      return partyId != null && partyId.equals(this.partyByMember.get(second));
   }

   public synchronized void clear() {
      this.parties.clear();
      this.partyByMember.clear();
      this.invitesByTarget.clear();
   }

   private Operation join(UUID targetId, UUID partyId, boolean requireListed) {
      if (this.partyByMember.containsKey(targetId)) {
         return new Operation(PartyService.Result.ALREADY_IN_PARTY, (PartySnapshot)this.partyOf(targetId).orElse(null));
      } else {
         PartyState party = (PartyState)this.parties.get(partyId);
         if (party == null) {
            return new Operation(PartyService.Result.PARTY_NOT_FOUND, (PartySnapshot)null);
         } else if (requireListed && !party.listed) {
            return new Operation(PartyService.Result.PARTY_NOT_LISTED, party.snapshot());
         } else if (party.members.size() >= this.maximumMembers) {
            return new Operation(PartyService.Result.PARTY_FULL, party.snapshot());
         } else {
            party.members.add(targetId);
            this.partyByMember.put(targetId, party.id);
            this.clearInvitesFor(targetId);
            return new Operation(PartyService.Result.SUCCESS, party.snapshot());
         }
      }
   }

   private PartyState stateOfMember(UUID memberId) {
      UUID partyId = (UUID)this.partyByMember.get(memberId);
      return partyId == null ? null : (PartyState)this.parties.get(partyId);
   }

   private void cleanupExpired(long nowMillis) {
      this.invitesByTarget.values().forEach((invites) -> invites.values().removeIf((invite) -> invite.expired(nowMillis) || !this.parties.containsKey(invite.partyId())));
      this.invitesByTarget.values().removeIf(Map::isEmpty);
   }

   private void clearInvitesFor(UUID targetId) {
      this.invitesByTarget.remove(targetId);
   }

   private void removePartyInvites(UUID partyId) {
      this.invitesByTarget.values().forEach((invites) -> invites.remove(partyId));
      this.invitesByTarget.values().removeIf(Map::isEmpty);
   }

   private long saturatingAdd(long left, long right) {
      try {
         return Math.addExact(left, right);
      } catch (ArithmeticException var6) {
         return Long.MAX_VALUE;
      }
   }

   public static record Operation(Result result, PartySnapshot party) {
      public boolean successful() {
         return this.result == PartyService.Result.SUCCESS || this.result == PartyService.Result.PARTY_DISBANDED;
      }
   }

   public static enum Result {
      SUCCESS,
      ALREADY_IN_PARTY,
      NOT_IN_PARTY,
      NOT_LEADER,
      TARGET_IN_PARTY,
      TARGET_NOT_IN_PARTY,
      PARTY_FULL,
      PARTY_NOT_FOUND,
      PARTY_NOT_LISTED,
      PARTY_DISBANDED,
      INVITE_EXPIRED,
      FINDER_DISABLED;
   }

   private static final class PartyState {
      private final UUID id;
      private UUID leaderId;
      private final LinkedHashSet<UUID> members = new LinkedHashSet();
      private final long createdAtMillis;
      private boolean listed;

      private PartyState(UUID id, UUID leaderId, long createdAtMillis) {
         this.id = id;
         this.leaderId = leaderId;
         this.createdAtMillis = createdAtMillis;
         this.members.add(leaderId);
      }

      private PartySnapshot snapshot() {
         return new PartySnapshot(this.id, this.leaderId, List.copyOf(this.members), this.listed, this.createdAtMillis);
      }
   }
}
