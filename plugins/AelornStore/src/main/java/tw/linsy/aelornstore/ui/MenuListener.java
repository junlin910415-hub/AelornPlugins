package tw.linsy.aelornstore.ui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryHolder;
import tw.linsy.aelornstore.AelornStorePlugin;

/**
 * Turns clicks into store actions, and greets joining players.
 *
 * Store inventories are read-only: every click is cancelled before anything else
 * happens, and drags are refused outright. Nothing in a shop menu is a real item,
 * so letting one be picked up would be an item duplication bug rather than a
 * cosmetic one.
 */
public final class MenuListener implements Listener {

    private final AelornStorePlugin plugin;

    public MenuListener(AelornStorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MenuHolder menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Clicks in the player's own inventory while a store menu is open are
        // already harmless once cancelled; only the top inventory carries tokens.
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        String token = menu.token(event.getSlot());
        if (token == null) {
            return;
        }
        if (!plugin.clickCooldowns().tryUse(player.getUniqueId(),
            plugin.settings().limits().guiClickCooldownMs())) {
            return;
        }
        handle(player, menu, token);
    }

    private void handle(Player player, MenuHolder menu, String token) {
        StoreMenu menus = plugin.menus();
        if (token.equals("close")) {
            player.closeInventory();
            return;
        }
        if (token.equals("root")) {
            menus.openRoot(player);
            return;
        }
        if (token.equals("topup")) {
            menus.openTopup(player);
            return;
        }
        if (token.equals("orders")) {
            player.closeInventory();
            plugin.storefront().sendOrders(player, 10);
            return;
        }
        int separator = token.indexOf(':');
        if (separator <= 0) {
            return;
        }
        String action = token.substring(0, separator);
        String value = token.substring(separator + 1);
        switch (action) {
            case "cat" -> menus.openCategory(player, value, 0);
            case "page" -> menus.openCategory(player, menu.context(), parsePage(value));
            case "buy" -> {
                if (plugin.settings().shop().confirmPurchase()) {
                    menus.openConfirm(player, value);
                } else {
                    plugin.storefront().buy(player, value, 1);
                }
            }
            case "confirm" -> plugin.storefront().buy(player, value, 1);
            case "pkg" -> menus.openProvider(player, value);
            case "prov" -> {
                player.closeInventory();
                plugin.storefront().topupPackage(player, menu.context(), value);
            }
            default -> {
                // An unknown token means the menu was built by an older jar; ignore it.
            }
        }
    }

    /** Announces queued deliveries and any VIP about to lapse, once the player is settled in. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.storageReady()) {
            return;
        }
        plugin.schedulers().asyncDelayed(() -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.delivery().nudgeFor(player);
            plugin.vip().warnIfExpiringSoon(player);
        }, 2, java.util.concurrent.TimeUnit.SECONDS);
    }

    private int parsePage(String raw) {
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }
}
