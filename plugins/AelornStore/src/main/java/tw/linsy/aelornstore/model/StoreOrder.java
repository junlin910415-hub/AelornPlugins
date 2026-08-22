package tw.linsy.aelornstore.model;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One row of the order table — the system's unit of accountability.
 *
 * Both top-ups and in-game purchases live here so that "what did this player
 * receive, and why" is one query. {@code playerName} is stored alongside the UUID
 * on purpose: a name lookup must still work years later when the player has
 * renamed or the profile cache is gone, which is exactly when a payment dispute
 * gets raised.
 */
public record StoreOrder(
    String orderNo,
    UUID playerId,
    String playerName,
    OrderType type,
    @Nullable String productId,
    int quantity,
    /** Real money charged, in minor units. Always 0 for {@link OrderType#PURCHASE}. */
    long amountMinor,
    /** Credit granted (top-up) or spent (purchase). */
    long creditAmount,
    /** Which balance a purchase was paid from; null for top-ups. */
    @Nullable PriceCurrency currency,
    String provider,
    @Nullable String providerTradeNo,
    @Nullable String payMethod,
    OrderStatus status,
    long createdAt,
    long paidAt,
    long deliveredAt,
    long expiresAt,
    int attempts,
    @Nullable String failReason
) {

    /** True when the unpaid order has passed its window and should be reaped. */
    public boolean expired(long now) {
        return expiresAt > 0 && now >= expiresAt && !status.paid() && !status.terminal();
    }

    /** Short human summary used in chat lines and audit records. */
    public String summary() {
        if (type == OrderType.TOPUP) {
            return "儲值 " + creditAmount + " 點";
        }
        String product = productId == null ? "?" : productId;
        return quantity > 1 ? product + " ×" + quantity : product;
    }
}
