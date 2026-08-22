package tw.linsy.aelornstore.ui;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tw.linsy.aelornstore.AelornStorePlugin;
import tw.linsy.aelornstore.config.Catalog;
import tw.linsy.aelornstore.config.MenuLayout;
import tw.linsy.aelornstore.config.StoreSettings;
import tw.linsy.aelornstore.model.Category;
import tw.linsy.aelornstore.model.PriceCurrency;
import tw.linsy.aelornstore.model.Product;
import tw.linsy.aelornstore.model.TopupPackage;
import tw.linsy.aelornstore.model.VipRecord;
import tw.linsy.aelorn.lib.text.Messages;

/**
 * Builds and opens every shop screen.
 *
 * Two rules shape this class. Layout comes entirely from shop.yml, so nothing
 * here assumes a slot number or a row count. And every screen is composed from a
 * snapshot taken before the inventory is created — balance, VIP, stock and
 * per-player counts are read once, off the main thread, then rendered on the
 * player's own region thread. That keeps Folia happy and stops the menu from
 * issuing a database query per icon.
 */
public final class StoreMenu {

    /** A menu is only worth drawing from data this fresh; older reads are re-fetched. */
    private record Snapshot(long credit, double vaultBalance, String vipName) { }

    private final AelornStorePlugin plugin;

    public StoreMenu(AelornStorePlugin plugin) {
        this.plugin = plugin;
    }

    // ── 進入點（皆為非同步取資料 → 玩家執行緒開啟） ──────────────────────────

    public void openRoot(Player player) {
        withSnapshot(player, snapshot -> buildRoot(player, snapshot));
    }

    public void openCategory(Player player, String categoryId, int page) {
        withSnapshot(player, snapshot -> buildCategory(player, snapshot, categoryId, page));
    }

    public void openConfirm(Player player, String productId) {
        withSnapshot(player, snapshot -> buildConfirm(player, snapshot, productId));
    }

    public void openTopup(Player player) {
        withSnapshot(player, snapshot -> buildTopup(player, snapshot));
    }

    public void openProvider(Player player, String context) {
        withSnapshot(player, snapshot -> buildProvider(player, snapshot, context));
    }

    /**
     * Reads the player's balances off the main thread, then opens on their region.
     * A database failure closes nothing and tells the player, rather than opening
     * a menu with a silently wrong balance on it.
     */
    private void withSnapshot(Player player, MenuBuilder builder) {
        plugin.schedulers().async(() -> {
            Snapshot snapshot;
            try {
                long credit = plugin.wallet().balance(player.getUniqueId());
                Optional<VipRecord> vip = plugin.vip().current(player.getUniqueId());
                double vault = plugin.vault() == null ? 0.0d : plugin.vault().balance(player.getUniqueId());
                snapshot = new Snapshot(credit, vault, plugin.vip().displayName(vip.orElse(null)));
            } catch (SQLException failure) {
                plugin.getLogger().log(Level.WARNING, "讀取商店資料失敗: " + player.getName(), failure);
                player.getScheduler().execute(plugin,
                    () -> plugin.messages().send(player, "general.storage-unavailable"), null, 1L);
                return;
            }
            player.getScheduler().execute(plugin, () -> {
                Inventory inventory = builder.build(snapshot);
                if (inventory != null) {
                    player.openInventory(inventory);
                }
            }, null, 1L);
        });
    }

    // ── 版面 ────────────────────────────────────────────────────────────────

    private Inventory buildRoot(Player player, Snapshot snapshot) {
        Catalog catalog = plugin.catalog();
        Messages messages = plugin.messages();
        MenuLayout layout = catalog.menu("root");
        MenuHolder holder = new MenuHolder(MenuHolder.Type.ROOT, "", 0);
        Inventory inventory = create(holder, layout, messages.raw("shop.title-root"));

        for (Category category : catalog.orderedCategories()) {
            if (!category.visibleTo(player)) {
                continue;
            }
            inventory.setItem(category.slot(), icon(category.icon(),
                messages.inlineItem(category.name()),
                messages.inlineLore(category.lore())));
            holder.bind(category.slot(), "cat:" + category.id());
        }
        placeBalance(inventory, holder, layout, snapshot);
        placeButton(inventory, holder, layout, "topup", Material.GOLD_INGOT,
            messages.raw("shop.title-topup"), List.of(), "topup");
        placeButton(inventory, holder, layout, "close", Material.BARRIER,
            messages.raw("gui.close-name"), messages.lore("gui.close-lore"), "close");
        return inventory;
    }

    private Inventory buildCategory(Player player, Snapshot snapshot, String categoryId, int page) {
        Catalog catalog = plugin.catalog();
        Messages messages = plugin.messages();
        Category category = catalog.categories().get(categoryId);
        if (category == null || !category.visibleTo(player)) {
            messages.send(player, "shop.category-locked");
            return null;
        }
        MenuLayout layout = catalog.menu("category");
        List<Integer> slots = layout.contentSlots();
        List<Product> visible = new ArrayList<>();
        for (Product product : catalog.productsIn(categoryId)) {
            if (product.visibleTo(player)) {
                visible.add(product);
            }
        }
        int perPage = Math.max(1, slots.size());
        int pages = Math.max(1, (visible.size() + perPage - 1) / perPage);
        int current = Math.max(0, Math.min(page, pages - 1));

        MenuHolder holder = new MenuHolder(MenuHolder.Type.CATEGORY, categoryId, current);
        Inventory inventory = create(holder, layout,
            messages.raw("shop.title-category", "category", category.name()));

        for (int index = 0; index < perPage; index++) {
            int productIndex = current * perPage + index;
            if (productIndex >= visible.size()) {
                break;
            }
            Product product = visible.get(productIndex);
            int slot = slots.get(index);
            inventory.setItem(slot, productIcon(player, product));
            holder.bind(slot, "buy:" + product.id());
        }

        placeBalance(inventory, holder, layout, snapshot);
        placeButton(inventory, holder, layout, "back", Material.ARROW,
            messages.raw("gui.back-name"), messages.lore("gui.back-lore"), "root");
        placeButton(inventory, holder, layout, "close", Material.BARRIER,
            messages.raw("gui.close-name"), messages.lore("gui.close-lore"), "close");
        if (current > 0) {
            placeButton(inventory, holder, layout, "prev", Material.SPECTRAL_ARROW,
                messages.raw("gui.prev-page-name"),
                List.of(messages.item("gui.page-indicator", "page", current + 1, "pages", pages)),
                "page:" + (current - 1));
        }
        if (current < pages - 1) {
            placeButton(inventory, holder, layout, "next", Material.SPECTRAL_ARROW,
                messages.raw("gui.next-page-name"),
                List.of(messages.item("gui.page-indicator", "page", current + 1, "pages", pages)),
                "page:" + (current + 1));
        }
        return inventory;
    }

    private Inventory buildConfirm(Player player, Snapshot snapshot, String productId) {
        Catalog catalog = plugin.catalog();
        Messages messages = plugin.messages();
        Product product = catalog.products().get(productId);
        if (product == null) {
            messages.send(player, "shop.purchase-failed", "reason", productId);
            return null;
        }
        MenuLayout layout = catalog.menu("confirm");
        MenuHolder holder = new MenuHolder(MenuHolder.Type.CONFIRM, productId, 0);
        Inventory inventory = create(holder, layout, messages.raw("shop.title-confirm"));

        long price = product.priceFor(player);
        String priceText = formatPrice(product.currency(), price);
        long after = product.currency() == PriceCurrency.CREDIT
            ? snapshot.credit() - price
            : (long) snapshot.vaultBalance() - price;

        int itemSlot = layout.button("item-slot");
        if (itemSlot >= 0) {
            inventory.setItem(itemSlot, productIcon(player, product));
        }
        int yesSlot = layout.button("yes-slot");
        if (yesSlot >= 0) {
            inventory.setItem(yesSlot, icon(Material.LIME_CONCRETE,
                messages.item("gui.confirm-yes-name"),
                messages.lore("gui.confirm-yes-lore", "price", priceText,
                    "after", formatPrice(product.currency(), Math.max(0, after)))));
            holder.bind(yesSlot, "confirm:" + productId);
        }
        int noSlot = layout.button("no-slot");
        if (noSlot >= 0) {
            inventory.setItem(noSlot, icon(Material.RED_CONCRETE,
                messages.item("gui.confirm-no-name"), messages.lore("gui.confirm-no-lore")));
            holder.bind(noSlot, "cat:" + product.categoryId());
        }
        return inventory;
    }

    private Inventory buildTopup(Player player, Snapshot snapshot) {
        Catalog catalog = plugin.catalog();
        Messages messages = plugin.messages();
        StoreSettings settings = plugin.settings();
        MenuLayout layout = catalog.menu("topup");
        MenuHolder holder = new MenuHolder(MenuHolder.Type.TOPUP, "", 0);
        Inventory inventory = create(holder, layout, messages.raw("shop.title-topup"));

        for (TopupPackage entry : catalog.orderedTopupPackages()) {
            List<Component> lore = new ArrayList<>(messages.inlineLore(entry.lore()));
            lore.add(messages.item("gui.price-line", "price",
                settings.money().formatMinor(entry.amountMinor())));
            inventory.setItem(entry.slot(), icon(entry.icon(),
                messages.inlineItem(entry.name()), lore));
            holder.bind(entry.slot(), "pkg:" + entry.id());
        }
        placeBalance(inventory, holder, layout, snapshot);
        placeButton(inventory, holder, layout, "back", Material.ARROW,
            messages.raw("gui.back-name"), messages.lore("gui.back-lore"), "root");
        placeButton(inventory, holder, layout, "close", Material.BARRIER,
            messages.raw("gui.close-name"), messages.lore("gui.close-lore"), "close");
        return inventory;
    }

    private Inventory buildProvider(Player player, Snapshot snapshot, String context) {
        Catalog catalog = plugin.catalog();
        Messages messages = plugin.messages();
        StoreSettings settings = plugin.settings();
        MenuLayout layout = catalog.menu("provider");
        MenuHolder holder = new MenuHolder(MenuHolder.Type.PROVIDER, context, 0);
        Inventory inventory = create(holder, layout, messages.raw("shop.title-topup"));

        List<Integer> slots = layout.contentSlots();
        int index = 0;
        for (StoreSettings.ProviderSettings channel : settings.enabledProviders()) {
            if (index >= slots.size() || !plugin.payments().usable(settings, channel.id())) {
                continue;
            }
            inventory.setItem(slots.get(index), icon(channel.icon(),
                messages.inlineItem(channel.displayName()),
                messages.inlineLore(channel.instructions())));
            holder.bind(slots.get(index), "prov:" + channel.id());
            index++;
        }
        placeButton(inventory, holder, layout, "back", Material.ARROW,
            messages.raw("gui.back-name"), messages.lore("gui.back-lore"), "topup");
        placeButton(inventory, holder, layout, "close", Material.BARRIER,
            messages.raw("gui.close-name"), messages.lore("gui.close-lore"), "close");
        return inventory;
    }

    // ── 元件 ────────────────────────────────────────────────────────────────

    private ItemStack productIcon(Player player, Product product) {
        Messages messages = plugin.messages();
        List<Component> lore = new ArrayList<>(messages.inlineLore(product.lore()));
        long price = product.priceFor(player);
        if (product.hasDiscount(player)) {
            lore.add(messages.item("gui.price-line-discounted",
                "original", formatPrice(product.currency(), product.price()),
                "price", formatPrice(product.currency(), price),
                "percent", product.discountPercent()));
        } else {
            lore.add(messages.item("gui.price-line", "price", formatPrice(product.currency(), price)));
        }
        if (!product.unlimitedStock()) {
            lore.add(messages.item("gui.stock-line", "stock", product.stock()));
        }
        lore.add(messages.item("gui.click-to-buy"));
        return icon(product.icon(), messages.inlineItem(product.name()), lore);
    }

    private String formatPrice(PriceCurrency currency, long amount) {
        StoreSettings settings = plugin.settings();
        if (currency == PriceCurrency.VAULT) {
            return amount + " " + settings.vault().displayName();
        }
        return settings.credit().symbol() + settings.money().formatCredit(amount);
    }

    private void placeBalance(Inventory inventory, MenuHolder holder, MenuLayout layout, Snapshot snapshot) {
        int slot = layout.button("balance");
        if (slot < 0) {
            return;
        }
        StoreSettings settings = plugin.settings();
        Messages messages = plugin.messages();
        inventory.setItem(slot, icon(Material.SUNFLOWER,
            messages.item("gui.balance-name", "credit", settings.credit().displayName()),
            messages.lore("gui.balance-lore",
                "symbol", settings.credit().symbol(),
                "balance", settings.money().formatCredit(snapshot.credit()),
                "vip", snapshot.vipName().isEmpty() ? "—" : snapshot.vipName())));
        holder.bind(slot, "orders");
    }

    private void placeButton(Inventory inventory, MenuHolder holder, MenuLayout layout, String name,
                             Material material, String title, List<Component> lore, String token) {
        int slot = layout.button(name);
        if (slot < 0 || title.isBlank()) {
            return;
        }
        inventory.setItem(slot, icon(material, plugin.messages().inlineItem(title), lore));
        holder.bind(slot, token);
    }

    private Inventory create(MenuHolder holder, MenuLayout layout, String title) {
        Inventory inventory = Bukkit.createInventory(holder, layout.size(),
            plugin.messages().inline(title));
        holder.attach(inventory);
        if (layout.filler() != null) {
            ItemStack filler = icon(layout.filler(), Component.empty(), List.of());
            for (int slot = 0; slot < layout.size(); slot++) {
                inventory.setItem(slot, filler);
            }
        }
        return inventory;
    }

    private ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @FunctionalInterface
    private interface MenuBuilder {
        Inventory build(Snapshot snapshot);
    }
}
