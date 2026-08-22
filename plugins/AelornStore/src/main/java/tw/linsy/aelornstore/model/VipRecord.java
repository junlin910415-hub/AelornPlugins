package tw.linsy.aelornstore.model;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * A player's current VIP standing.
 *
 * {@code expiresAt == 0} means permanent. Storing the granting order lets a
 * refund find and revoke exactly the membership that money paid for, instead of
 * clearing whatever the player happens to hold at refund time.
 */
public record VipRecord(
    UUID playerId,
    String tierId,
    long expiresAt,
    long updatedAt,
    @Nullable String sourceOrder
) {

    public boolean permanent() {
        return expiresAt <= 0;
    }

    public boolean active(long now) {
        return permanent() || now < expiresAt;
    }

    /** Whole days left, rounded up so "0 days" only ever means "expired". */
    public long daysRemaining(long now) {
        if (permanent()) {
            return Long.MAX_VALUE;
        }
        long millis = expiresAt - now;
        return millis <= 0 ? 0 : (millis + 86_399_999L) / 86_400_000L;
    }
}
