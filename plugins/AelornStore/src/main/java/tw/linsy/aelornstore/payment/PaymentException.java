package tw.linsy.aelornstore.payment;

/** A payment channel could not take this order. Carries a player-safe reason. */
public class PaymentException extends Exception {

    private static final long serialVersionUID = 1L;

    private final boolean notImplemented;

    public PaymentException(String message) {
        this(message, false);
    }

    public PaymentException(String message, boolean notImplemented) {
        super(message);
        this.notImplemented = notImplemented;
    }

    /** True when the channel exists in config but its adapter has not been written yet. */
    public boolean notImplemented() {
        return notImplemented;
    }
}
