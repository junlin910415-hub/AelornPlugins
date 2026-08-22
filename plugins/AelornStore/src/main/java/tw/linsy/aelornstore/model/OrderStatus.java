package tw.linsy.aelornstore.model;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * The order lifecycle. Every transition is a conditional {@code UPDATE ... WHERE
 * status = ?}, so a replayed gateway callback or a double click can never move an
 * order forward twice — that is what makes delivery idempotent.
 *
 * <pre>
 *   CREATED ──▶ PENDING ──▶ PAID ──▶ DELIVERING ──▶ DELIVERED ──▶ REFUNDED
 *      │           │                     │
 *      └───────────┴──▶ CANCELLED        └──▶ NEEDS_ATTENTION ──▶ DELIVERED
 *                  └──▶ EXPIRED
 *                  └──▶ FAILED
 * </pre>
 */
public enum OrderStatus {

    /** Written to the database; the player has not picked a payment channel yet. */
    CREATED(false, true),
    /** Handed to a payment channel; waiting for the player to pay. */
    PENDING(false, true),
    /** Money confirmed. The delivery poller picks these up. */
    PAID(true, false),
    /** Claimed by the delivery poller. Guards against two nodes delivering at once. */
    DELIVERING(true, false),
    /** Contents granted. Terminal unless refunded. */
    DELIVERED(true, false),
    /** Delivery failed past the retry limit; waiting for a human. */
    NEEDS_ATTENTION(true, false),
    /** Money confirmed but contents revoked. Terminal. */
    REFUNDED(true, false),
    /** Cancelled by the player or an admin before payment. Terminal. */
    CANCELLED(false, false),
    /** Passed its expiry without payment. Terminal. */
    EXPIRED(false, false),
    /** The payment channel reported a failure. Terminal. */
    FAILED(false, false);

    private final boolean paid;
    private final boolean cancellable;

    OrderStatus(boolean paid, boolean cancellable) {
        this.paid = paid;
        this.cancellable = cancellable;
    }

    /** True once money has changed hands — these orders must never be silently dropped. */
    public boolean paid() {
        return paid;
    }

    /** True while the player or an admin may still cancel without a refund. */
    public boolean cancellable() {
        return cancellable;
    }

    public boolean terminal() {
        return this == DELIVERED || this == REFUNDED || this == CANCELLED
            || this == EXPIRED || this == FAILED;
    }

    public static @Nullable OrderStatus parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
