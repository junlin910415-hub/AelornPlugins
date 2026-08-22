package tw.linsy.aelornstore.payment;

import tw.linsy.aelornstore.config.StoreSettings;
import tw.linsy.aelornstore.model.StoreOrder;

/**
 * Payment by bank transfer or convenience-store deposit, settled by an admin.
 *
 * Fully working with no gateway, no merchant account and no web backend, which
 * makes it the channel to run on while a merchant application is still being
 * reviewed — and the fallback if a gateway ever suspends the account.
 *
 * <p>An admin settles with {@code /astore order approve <no>}; the order then
 * enters the same delivery queue as a card payment, so the audit trail and the
 * refund path are identical.
 */
public final class ManualProvider implements PaymentProvider {

    public static final String ID = "manual";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available(StoreSettings.ProviderSettings settings) {
        return settings.enabled();
    }

    @Override
    public CheckoutTicket checkout(StoreOrder order, StoreSettings.ProviderSettings settings) {
        // No link: the instructions in config.yml carry the account details, and
        // the player quotes the order number as their transfer reference.
        return new CheckoutTicket(ID, "", settings.instructions());
    }
}
