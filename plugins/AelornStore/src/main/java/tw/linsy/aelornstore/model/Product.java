package tw.linsy.aelornstore.model;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * One purchasable entry from shop.yml, fully resolved at reload time.
 *
 * Everything a purchase needs — price, limits, the exact actions to run and the
 * exact actions that undo them — is decided here rather than at click time, so a
 * mid-session reload can never make a half-processed purchase see two different
 * definitions of the same product.
 */
public record Product(
    String id,
    String categoryId,
    int slot,
    Material icon,
    String name,
    List<String> lore,
    long price,
    PriceCurrency currency,
    /** Total units the server will ever sell; {@code -1} means unlimited. */
    int stock,
    /** Lifetime cap per player; {@code 0} means uncapped. */
    int limitPerPlayer,
    /** Daily cap per player; {@code 0} means uncapped. */
    int limitPerDay,
    String permission,
    String discountPermission,
    int discountPercent,
    List<StoreAction> actions,
    List<StoreAction> revokeActions
) {

    public Product {
        lore = List.copyOf(lore);
        actions = List.copyOf(actions);
        revokeActions = List.copyOf(revokeActions);
    }

    public boolean unlimitedStock() {
        return stock < 0;
    }

    public boolean visibleTo(Player player) {
        return permission.isEmpty() || player.hasPermission(permission);
    }

    /**
     * The price this player actually pays. Rounded down so a displayed discount is
     * never worse than what the player is charged.
     */
    public long priceFor(Player player) {
        if (!hasDiscount(player)) {
            return price;
        }
        long discounted = price - (price * discountPercent / 100L);
        return Math.max(0L, discounted);
    }

    public boolean hasDiscount(Player player) {
        return discountPercent > 0
            && !discountPermission.isEmpty()
            && player.hasPermission(discountPermission);
    }
}
