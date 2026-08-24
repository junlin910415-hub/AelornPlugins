package tw.linsy.aelorn.rpgcore.config;

import org.bukkit.configuration.file.FileConfiguration;

public record PartySettings(int maximumMembers, long inviteLifetimeSeconds, boolean finderEnabled) {
   public static PartySettings from(FileConfiguration config) {
      int maximumMembers = config.getInt("party.maximum-members", 6);
      long inviteLifetimeSeconds = config.getLong("party.invite-lifetime-seconds", 60L);
      if (maximumMembers >= 2 && maximumMembers <= 12) {
         if (inviteLifetimeSeconds >= 15L && inviteLifetimeSeconds <= 300L) {
            return new PartySettings(maximumMembers, inviteLifetimeSeconds, config.getBoolean("party.finder-enabled", true));
         } else {
            throw new IllegalArgumentException("party.invite-lifetime-seconds must be between 15 and 300");
         }
      } else {
         throw new IllegalArgumentException("party.maximum-members must be between 2 and 12");
      }
   }
}
