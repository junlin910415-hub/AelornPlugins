package tw.linsy.aelornstore.payment;

import tw.linsy.aelornstore.config.StoreSettings;
import tw.linsy.aelornstore.model.StoreOrder;

/**
 * A way for a player to pay real money.
 *
 * The contract is intentionally narrow: given an order that already exists in the
 * database, produce the instructions a player needs to pay it. Confirming that
 * payment is <em>not</em> part of this interface — that arrives asynchronously at
 * the web backend, which verifies the gateway's signature and flips the order to
 * PAID. The plugin then picks it up from the database.
 *
 * <p>Keeping settlement out of the game server is what makes this safe: the
 * Minecraft process never needs an inbound port, never holds a merchant key, and
 * cannot be tricked into delivering by anything a player sends it.
 *
 * <p>Implementations are stateless and are called from async threads.
 */
public interface PaymentProvider {

    /** Matches the key under {@code payment.providers} in config.yml. */
    String id();

    /**
     * Whether this channel can currently take an order.
     * A channel that is enabled but misconfigured reports {@code false} rather
     * than failing at the moment a player clicks pay.
     */
    boolean available(StoreSettings.ProviderSettings settings);

    /** Produces the pay link and instructions for an order already marked PENDING. */
    CheckoutTicket checkout(StoreOrder order, StoreSettings.ProviderSettings settings)
        throws PaymentException;
}
