package tw.linsy.aelornstore.payment;

import java.util.List;

/**
 * What a player is told after an order is handed to a payment channel.
 *
 * Deliberately just text and a link. The plugin never renders a payment form,
 * never holds a merchant key and never sees card details — everything sensitive
 * stays on the web backend, so a compromised game server cannot leak anything
 * that would let someone take money.
 */
public record CheckoutTicket(String providerId, String payUrl, List<String> instructions) {

    public CheckoutTicket {
        instructions = List.copyOf(instructions);
    }

    public boolean hasLink() {
        return !payUrl.isEmpty();
    }
}
