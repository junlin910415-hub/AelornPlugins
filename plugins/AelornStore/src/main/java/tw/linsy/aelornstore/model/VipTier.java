package tw.linsy.aelornstore.model;

import java.util.List;

/**
 * A VIP rank from vip.yml.
 *
 * {@code priceRatio} is what makes an upgrade fair: when a player on a cheaper
 * tier buys a dearer one, their unused days are converted at the ratio of the two
 * tiers' prices rather than being discarded or carried over at face value.
 */
public record VipTier(
    String id,
    int weight,
    String displayName,
    long priceRatio,
    List<StoreAction> onGrant,
    List<StoreAction> onExpire
) {

    public VipTier {
        onGrant = List.copyOf(onGrant);
        onExpire = List.copyOf(onExpire);
    }

    /**
     * Converts {@code days} remaining on this tier into days on {@code target}.
     * A zero or negative ratio on either side means "no conversion" — the days are
     * dropped rather than turned into an accidental jackpot.
     */
    public long convertDays(long days, VipTier target) {
        if (days <= 0 || priceRatio <= 0 || target.priceRatio() <= 0) {
            return 0L;
        }
        return days * priceRatio / target.priceRatio();
    }
}
