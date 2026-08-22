package tw.linsy.aelornstore.model;

import java.util.List;
import org.bukkit.Material;

/** A fixed-price top-up tile. Custom amounts go through {@code /store topup <amount>}. */
public record TopupPackage(
    String id,
    int slot,
    Material icon,
    String name,
    List<String> lore,
    /** Real money, in minor units (TWD cents). */
    long amountMinor,
    /** Extra credit on top of the amount-derived credit; the "bonus" in marketing terms. */
    long bonusCredit
) {

    public TopupPackage {
        lore = List.copyOf(lore);
    }

    /** Total credit this package grants, given the configured conversion rate. */
    public long totalCredit(long minorUnitsPerCredit) {
        return Money.creditFor(amountMinor, minorUnitsPerCredit) + bonusCredit;
    }
}
