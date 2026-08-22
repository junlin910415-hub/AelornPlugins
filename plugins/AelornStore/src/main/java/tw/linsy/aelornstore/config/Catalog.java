package tw.linsy.aelornstore.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelornstore.model.Category;
import tw.linsy.aelornstore.model.PriceCurrency;
import tw.linsy.aelornstore.model.Product;
import tw.linsy.aelornstore.model.StoreAction;
import tw.linsy.aelornstore.model.TopupPackage;
import tw.linsy.aelornstore.model.VipTier;
import tw.linsy.aelorn.lib.config.ConfigParse;

/**
 * The parsed contents of shop.yml and vip.yml.
 *
 * Loading is all-or-nothing per entry, never per file: one malformed product is
 * logged and skipped so the rest of the shop stays open. The result is immutable
 * and swapped in as a single reference on reload.
 */
public record Catalog(
    Map<String, Category> categories,
    Map<String, Product> products,
    Map<String, List<Product>> productsByCategory,
    Map<String, TopupPackage> topupPackages,
    Map<String, VipTier> vipTiers,
    Map<String, MenuLayout> menus
) {

    public static final String SHOP_FILE = "shop.yml";
    public static final String VIP_FILE = "vip.yml";

    public Catalog {
        categories = Map.copyOf(categories);
        products = Map.copyOf(products);
        productsByCategory = Map.copyOf(productsByCategory);
        topupPackages = Map.copyOf(topupPackages);
        vipTiers = Map.copyOf(vipTiers);
        menus = Map.copyOf(menus);
    }

    public MenuLayout menu(String name) {
        MenuLayout layout = menus.get(name);
        return layout != null ? layout : new MenuLayout(6, null, List.of(), Map.of());
    }

    public List<Product> productsIn(String categoryId) {
        List<Product> found = productsByCategory.get(categoryId);
        return found != null ? found : List.of();
    }

    /** Categories in slot order, so the menu draws deterministically across reloads. */
    public List<Category> orderedCategories() {
        List<Category> ordered = new ArrayList<>(categories.values());
        ordered.sort(Comparator.comparingInt(Category::slot));
        return ordered;
    }

    public List<TopupPackage> orderedTopupPackages() {
        List<TopupPackage> ordered = new ArrayList<>(topupPackages.values());
        ordered.sort(Comparator.comparingInt(TopupPackage::slot));
        return ordered;
    }

    public @Nullable VipTier tier(@Nullable String id) {
        return id == null ? null : vipTiers.get(id.toLowerCase(Locale.ROOT));
    }

    public String tierIdsForDisplay() {
        List<String> ids = new ArrayList<>(vipTiers.keySet());
        return String.join(", ", ids);
    }

    /** Reads both files. Pure IO plus parsing — safe to call off the main thread. */
    public static Catalog load(JavaPlugin plugin, Logger logger) {
        YamlConfiguration shop = readWithDefaults(plugin, SHOP_FILE, logger);
        YamlConfiguration vip = readWithDefaults(plugin, VIP_FILE, logger);

        Map<String, MenuLayout> menus = readMenus(shop.getConfigurationSection("menu"), logger);
        Map<String, Category> categories = readCategories(shop.getConfigurationSection("categories"),
            menus.get("root"), logger);
        Map<String, TopupPackage> packages = readTopupPackages(shop.getConfigurationSection("topup-packages"),
            menus.get("topup"), logger);
        Map<String, VipTier> tiers = readTiers(vip.getConfigurationSection("tiers"), logger);
        Map<String, Product> products = readProducts(shop.getConfigurationSection("products"),
            categories, menus.get("category"), logger);

        Map<String, List<Product>> byCategory = new LinkedHashMap<>();
        for (Category category : categories.values()) {
            byCategory.put(category.id(), new ArrayList<>());
        }
        List<Product> ordered = new ArrayList<>(products.values());
        ordered.sort(Comparator.comparingInt(Product::slot).thenComparing(Product::id));
        for (Product product : ordered) {
            byCategory.computeIfAbsent(product.categoryId(), key -> new ArrayList<>()).add(product);
        }
        Map<String, List<Product>> frozen = new LinkedHashMap<>();
        byCategory.forEach((key, value) -> frozen.put(key, List.copyOf(value)));

        return new Catalog(categories, products, frozen, packages, tiers, menus);
    }

    private static YamlConfiguration readWithDefaults(JavaPlugin plugin, String name, Logger logger) {
        File target = new File(plugin.getDataFolder(), name);
        if (!target.exists() && plugin.getResource(name) != null) {
            try {
                plugin.saveResource(name, false);
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, "無法寫出預設 " + name + "。", failure);
            }
        }
        if (!target.exists()) {
            logger.warning("找不到 " + name + "，該部分將為空。");
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(target);
    }

    private static Map<String, MenuLayout> readMenus(@Nullable ConfigurationSection section, Logger logger) {
        Map<String, MenuLayout> menus = new LinkedHashMap<>();
        // Default rows differ per screen; a missing block still yields a usable size.
        Map<String, Integer> defaults = Map.of(
            "root", 5, "category", 6, "confirm", 3, "topup", 5, "provider", 3);
        for (Map.Entry<String, Integer> entry : defaults.entrySet()) {
            ConfigurationSection child = section == null ? null : section.getConfigurationSection(entry.getKey());
            menus.put(entry.getKey(), MenuLayout.load(child, entry.getValue(),
                "menu." + entry.getKey(), logger));
        }
        return menus;
    }

    private static Map<String, Category> readCategories(@Nullable ConfigurationSection section,
                                                        @Nullable MenuLayout rootMenu, Logger logger) {
        Map<String, Category> categories = new LinkedHashMap<>();
        if (section == null) {
            return categories;
        }
        int size = rootMenu == null ? 54 : rootMenu.size();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String id = key.trim().toLowerCase(Locale.ROOT);
            if (!ConfigParse.isSafeId(id)) {
                logger.warning("分類 id「" + key + "」含有不允許的字元，已略過。");
                continue;
            }
            int slot = ConfigParse.slot(entry, "slot", size, logger);
            if (slot < 0) {
                logger.warning("分類 " + id + " 未設定有效的 slot，已略過。");
                continue;
            }
            Material icon = ConfigParse.material(entry.getString("icon"), Material.CHEST,
                "categories." + id + ".icon", logger);
            categories.put(id, new Category(
                id, slot, icon,
                entry.getString("name", id),
                ConfigParse.stringList(entry, "lore"),
                ConfigParse.trimmedOrEmpty(entry.getString("permission", ""))
            ));
        }
        return categories;
    }

    private static Map<String, TopupPackage> readTopupPackages(@Nullable ConfigurationSection section,
                                                               @Nullable MenuLayout topupMenu, Logger logger) {
        Map<String, TopupPackage> packages = new LinkedHashMap<>();
        if (section == null) {
            return packages;
        }
        int size = topupMenu == null ? 54 : topupMenu.size();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String id = key.trim().toLowerCase(Locale.ROOT);
            if (!ConfigParse.isSafeId(id)) {
                logger.warning("儲值方案 id「" + key + "」含有不允許的字元，已略過。");
                continue;
            }
            long amount = entry.getLong("amount-minor", 0L);
            if (amount <= 0L) {
                logger.warning("儲值方案 " + id + " 的 amount-minor 必須大於 0，已略過。");
                continue;
            }
            int slot = ConfigParse.slot(entry, "slot", size, logger);
            if (slot < 0) {
                logger.warning("儲值方案 " + id + " 未設定有效的 slot，已略過。");
                continue;
            }
            packages.put(id, new TopupPackage(
                id, slot,
                ConfigParse.material(entry.getString("icon"), Material.GOLD_NUGGET,
                    "topup-packages." + id + ".icon", logger),
                entry.getString("name", id),
                ConfigParse.stringList(entry, "lore"),
                amount,
                Math.max(0L, entry.getLong("bonus-credit", 0L))
            ));
        }
        return packages;
    }

    private static Map<String, VipTier> readTiers(@Nullable ConfigurationSection section, Logger logger) {
        Map<String, VipTier> tiers = new LinkedHashMap<>();
        if (section == null) {
            return tiers;
        }
        List<VipTier> parsed = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String id = key.trim().toLowerCase(Locale.ROOT);
            if (!ConfigParse.isSafeId(id)) {
                logger.warning("VIP 等級 id「" + key + "」含有不允許的字元，已略過。");
                continue;
            }
            parsed.add(new VipTier(
                id,
                entry.getInt("weight", 0),
                entry.getString("display-name", id),
                Math.max(0L, entry.getLong("price-ratio", 0L)),
                readActions(entry, "on-grant", "vip.tiers." + id, logger),
                readActions(entry, "on-expire", "vip.tiers." + id, logger)
            ));
        }
        // Ascending weight, so upgrade comparisons read naturally elsewhere.
        parsed.sort(Comparator.comparingInt(VipTier::weight));
        for (VipTier tier : parsed) {
            tiers.put(tier.id(), tier);
        }
        return tiers;
    }

    private static Map<String, Product> readProducts(@Nullable ConfigurationSection section,
                                                     Map<String, Category> categories,
                                                     @Nullable MenuLayout categoryMenu, Logger logger) {
        Map<String, Product> products = new LinkedHashMap<>();
        if (section == null) {
            return products;
        }
        int size = categoryMenu == null ? 54 : categoryMenu.size();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String id = key.trim().toLowerCase(Locale.ROOT);
            if (!ConfigParse.isSafeId(id)) {
                logger.warning("商品 id「" + key + "」含有不允許的字元，已略過。");
                continue;
            }
            String categoryId = ConfigParse.trimmedOrEmpty(entry.getString("category", "")).toLowerCase(Locale.ROOT);
            if (!categories.containsKey(categoryId)) {
                logger.warning("商品 " + id + " 指向不存在的分類「" + categoryId + "」，已略過。");
                continue;
            }
            long price = entry.getLong("price", -1L);
            if (price < 0L) {
                logger.warning("商品 " + id + " 的 price 必須為 0 或正整數，已略過。");
                continue;
            }
            List<StoreAction> actions = readActions(entry, "actions", "products." + id, logger);
            if (actions.isEmpty()) {
                logger.warning("商品 " + id + " 沒有任何有效的 actions，購買後不會發放任何東西，已略過。");
                continue;
            }
            // A slot is optional: products without one are still purchasable by id,
            // they simply do not appear on the category page.
            int slot = entry.contains("slot") ? ConfigParse.slot(entry, "slot", size, logger) : -1;
            int discount = ConfigParse.bounded(entry.getInt("discount-percent", 0), 0, 100);
            products.put(id, new Product(
                id,
                categoryId,
                slot,
                ConfigParse.material(entry.getString("icon"), Material.PAPER,
                    "products." + id + ".icon", logger),
                entry.getString("name", id),
                ConfigParse.stringList(entry, "lore"),
                price,
                ConfigParse.enumValue(PriceCurrency.class, entry.getString("currency"),
                    PriceCurrency.CREDIT, "products." + id + ".currency", logger),
                entry.getInt("stock", -1),
                Math.max(0, entry.getInt("limit-per-player", 0)),
                Math.max(0, entry.getInt("limit-per-day", 0)),
                ConfigParse.trimmedOrEmpty(entry.getString("permission", "")),
                ConfigParse.trimmedOrEmpty(entry.getString("discount-permission", "")),
                discount,
                actions,
                readActions(entry, "revoke-actions", "products." + id, logger)
            ));
        }
        return products;
    }

    private static List<StoreAction> readActions(ConfigurationSection section, String key,
                                                 String path, Logger logger) {
        List<StoreAction> actions = new ArrayList<>();
        for (String line : section.getStringList(key)) {
            StoreAction action = StoreAction.parse(line, path + "." + key, logger);
            if (action != null) {
                actions.add(action);
            }
        }
        return actions;
    }
}
