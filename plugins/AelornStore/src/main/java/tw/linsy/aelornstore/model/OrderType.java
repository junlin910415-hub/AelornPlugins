package tw.linsy.aelornstore.model;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** What an order is for. Both kinds share one table so audit and refund are uniform. */
public enum OrderType {

    /** Real money in, credit out. Goes through a payment channel. */
    TOPUP,
    /** Credit (or Vault money) in, product contents out. Settles instantly in-game. */
    PURCHASE;

    public static @Nullable OrderType parse(@Nullable String raw) {
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
