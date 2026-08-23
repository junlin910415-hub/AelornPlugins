package tw.linsy.aelorn.discordbridge;

import org.bukkit.configuration.file.FileConfiguration;

/** Immutable snapshot of config.yml, rebuilt on /aelornbridge reload. */
record BridgeSettings(
    String expectedGuildId,
    String expectedChannelId,
    String expectedConsoleChannelId,
    String gameChannel,
    boolean rejectUnexpectedChannel,
    boolean requireLinkedDiscordAccount,
    int maxMessageCodePoints,
    boolean blockDirectionalControlCharacters,
    boolean blockMassMentions,
    int maxAttachments,
    long maxAttachmentBytes,
    int minecraftWindowSeconds,
    int minecraftMaxMessages,
    int discordWindowSeconds,
    int discordMaxMessages,
    int maxSharePlaceholders,
    int shareWindowSeconds,
    int shareMaxMessages,
    String messagePrefix,
    String messageTooLong,
    String unsafeCharacters,
    String rateLimited,
    String tooManyShares,
    String shareRateLimited) {

    static BridgeSettings from(FileConfiguration config) {
        return new BridgeSettings(
            config.getString("bridge.expected-guild-id", "").trim(),
            config.getString("bridge.expected-channel-id", "").trim(),
            config.getString("bridge.expected-console-channel-id", "").trim(),
            config.getString("bridge.game-channel", "global").trim(),
            config.getBoolean("bridge.reject-unexpected-channel", true),
            config.getBoolean("bridge.require-linked-discord-account", true),
            bounded(config.getInt("security.max-message-code-points", 256), 32, 2000),
            config.getBoolean("security.block-directional-control-characters", true),
            config.getBoolean("security.block-mass-mentions", true),
            bounded(config.getInt("security.attachments.max-count", 3), 0, 10),
            bounded(config.getLong("security.attachments.max-file-size-bytes", 4_194_304L), 65_536L, 25_000_000L),
            bounded(config.getInt("rate-limits.minecraft.window-seconds", 10), 1, 300),
            bounded(config.getInt("rate-limits.minecraft.max-messages", 6), 1, 100),
            bounded(config.getInt("rate-limits.discord.window-seconds", 10), 1, 300),
            bounded(config.getInt("rate-limits.discord.max-messages", 8), 1, 100),
            bounded(config.getInt("rate-limits.interactive-shares.max-placeholders-per-message", 2), 1, 10),
            bounded(config.getInt("rate-limits.interactive-shares.window-seconds", 30), 1, 600),
            bounded(config.getInt("rate-limits.interactive-shares.max-messages", 3), 1, 100),
            config.getString("messages.prefix", "&6[AELORN] &r"),
            config.getString("messages.message-too-long", "&cYour message is too long."),
            config.getString("messages.unsafe-characters", "&cYour message contains unsupported control characters."),
            config.getString("messages.rate-limited", "&eYou are sending messages too quickly. Wait {seconds} seconds."),
            config.getString("messages.too-many-shares", "&eUse at most {limit} interactive shares in one message."),
            config.getString("messages.share-rate-limited", "&eInteractive shares are on cooldown for {seconds} seconds."));
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long bounded(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
