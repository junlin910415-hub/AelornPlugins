package tw.linsy.aelornstore.payment;

import tw.linsy.aelornstore.config.StoreSettings;
import tw.linsy.aelornstore.model.StoreOrder;

/**
 * A channel whose gateway integration lives in the web backend.
 *
 * This covers ECPay and NewebPay alike, and would cover any future aggregator,
 * because from the game server's point of view they are identical: write the
 * order, hand the player a URL, wait for the database to say it was paid. All
 * three of the parts that differ per gateway — building the checkout form,
 * signing it, and verifying the callback — sit behind that URL.
 *
 * <p><b>Adapter status.</b> The backend ships the two adapters as documented
 * stubs ({@code web/src/providers/ecpay.js}, {@code web/src/providers/newebpay.js}).
 * Until one is filled in and its channel switched on in config.yml, this provider
 * reports itself unavailable rather than handing players a link to a page that
 * cannot take their money.
 */
public final class WebGatewayProvider implements PaymentProvider {

    private final String id;

    public WebGatewayProvider(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean available(StoreSettings.ProviderSettings settings) {
        // A checkout URL is the whole contract with the backend; without one there
        // is nothing to send the player to.
        return settings.enabled() && !settings.checkoutUrl().isEmpty();
    }

    @Override
    public CheckoutTicket checkout(StoreOrder order, StoreSettings.ProviderSettings settings)
            throws PaymentException {
        if (!settings.enabled()) {
            throw new PaymentException("管道 " + id + " 未啟用。");
        }
        if (settings.checkoutUrl().isEmpty()) {
            throw new PaymentException("管道 " + id + " 尚未設定 checkout-url。", true);
        }
        return new CheckoutTicket(id, settings.checkoutFor(order.orderNo()), settings.instructions());
    }
}
