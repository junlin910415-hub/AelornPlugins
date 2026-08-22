package tw.linsy.aelornstore.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formatting for the two units this plugin deals in.
 *
 * Real money is carried as an integer count of minor units (TWD cents) end to end
 * — from the top-up package in shop.yml, through the database, to the gateway —
 * so no rounding ever happens between the price a player saw and the amount that
 * is charged. Doubles are used only at the last step, to render a display string.
 */
public final class Money {

    /** Gateways in Taiwan (ECPay, NewebPay) accept whole NT dollars only. */
    public static final long MINOR_UNITS_PER_MAJOR = 100L;

    private final String pattern;
    private final DecimalFormat format;
    private final boolean requireWholeUnits;

    public Money(String pattern, int decimals, boolean requireWholeUnits) {
        this.pattern = pattern == null || pattern.isBlank() ? "{amount}" : pattern;
        this.requireWholeUnits = requireWholeUnits;
        StringBuilder digits = new StringBuilder("#,##0");
        if (decimals > 0) {
            digits.append('.');
            digits.append("0".repeat(decimals));
        }
        this.format = new DecimalFormat(digits.toString(), DecimalFormatSymbols.getInstance(Locale.ROOT));
        this.format.setRoundingMode(RoundingMode.HALF_UP);
    }

    /** Renders a minor-unit amount using the admin's {@code economy.money.display}. */
    public String formatMinor(long minor) {
        BigDecimal major = BigDecimal.valueOf(minor).divide(BigDecimal.valueOf(MINOR_UNITS_PER_MAJOR));
        return pattern.replace("{amount}", format.format(major));
    }

    /** True when the amount can be handed to a gateway that only takes whole units. */
    public boolean acceptable(long minor) {
        return !requireWholeUnits || minor % MINOR_UNITS_PER_MAJOR == 0;
    }

    /** The value a gateway's amount field expects. Only valid when {@link #acceptable} passes. */
    public static long toMajorUnits(long minor) {
        return minor / MINOR_UNITS_PER_MAJOR;
    }

    /** Plain integer rendering for credit balances, thousands-separated. */
    public String formatCredit(long credit) {
        return format.format(BigDecimal.valueOf(credit).setScale(0, RoundingMode.DOWN));
    }

    /**
     * Credit granted for a real-money amount. Integer division on purpose: a
     * package priced below one credit grants nothing rather than a fraction.
     */
    public static long creditFor(long amountMinor, long minorUnitsPerCredit) {
        return minorUnitsPerCredit <= 0 ? 0 : amountMinor / minorUnitsPerCredit;
    }
}
