package tw.linsy.aelornstore.config;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import tw.linsy.aelornstore.model.Money;
import tw.linsy.aelorn.lib.config.ConfigParse;
import tw.linsy.aelorn.lib.text.Text;

/**
 * An immutable snapshot of config.yml.
 *
 * Reading is done once per reload and the result is published as a single
 * reference, so no request path ever touches YAML and a reload can never be seen
 * half-applied: a purchase in flight keeps the settings it started with.
 */
public record StoreSettings(
    Text.Format textFormat,
    ZoneId zone,
    DateTimeFormatter timeFormat,
    Storage storage,
    Credit credit,
    VaultSettings vault,
    Money money,
    Topup topup,
    String defaultProvider,
    Map<String, ProviderSettings> providers,
    Delivery delivery,
    Shop shop,
    Vip vip,
    Audit audit,
    Limits limits
) {

    public enum StorageType { SQLITE, MYSQL }

    public enum UpgradePolicy { UPGRADE, STACK }

    public record Storage(
        StorageType type,
        String tablePrefix,
        String sqliteFile,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUser,
        String mysqlPassword,
        String mysqlProperties,
        int maxConnections,
        int connectionTimeoutSeconds,
        int validateAfterIdleSeconds
    ) {
        /** Human label for status output; never includes the password. */
        public String describe() {
            return type == StorageType.SQLITE
                ? "SQLite (" + sqliteFile + ")"
                : "MySQL (" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase + ")";
        }
    }

    public record Credit(String displayName, String symbol, long minorUnitsPerCredit, long maxBalance) { }

    public record VaultSettings(boolean enabled, String displayName) { }

    public record Topup(
        boolean enabled,
        String currency,
        int expireMinutes,
        int maxPendingOrders,
        long minAmountMinor,
        long maxAmountMinor,
        long dailyLimitMinor,
        int accountAgeHours,
        String orderPrefix
    ) { }

    public record ProviderSettings(
        String id,
        boolean enabled,
        String displayName,
        Material icon,
        String checkoutUrl,
        List<String> instructions
    ) {
        public ProviderSettings {
            instructions = List.copyOf(instructions);
        }

        /** The player-facing pay link for one order, or {@code ""} when this channel has none. */
        public String checkoutFor(String orderNo) {
            return checkoutUrl.isEmpty() ? "" : checkoutUrl.replace("{order}", orderNo);
        }
    }

    public record Delivery(
        int pollSeconds,
        int batchSize,
        boolean queueOnOffline,
        int maxAttempts,
        int retryBackoffSeconds,
        boolean notifyPlayer,
        long broadcastAboveMinor
    ) { }

    public record Shop(boolean confirmPurchase, boolean closeOnPurchase, int selfRefundMinutes) { }

    public record Vip(int checkSeconds, List<Integer> expireWarnDays, UpgradePolicy upgradePolicy) {
        public Vip {
            expireWarnDays = List.copyOf(expireWarnDays);
        }
    }

    public record Audit(boolean enabled, int keepDays, String file) { }

    public record Limits(long commandCooldownMs, long guiClickCooldownMs) { }

    /** Reads the whole file. Pure parsing — safe to call off the main thread. */
    public static StoreSettings load(FileConfiguration config, Logger logger) {
        Text.Format format = Text.Format.parse(config.getString("text-format"));

        ConfigurationSection timeSection = section(config, "time");
        ZoneId zone = parseZone(timeSection.getString("zone", "Asia/Taipei"), logger);
        DateTimeFormatter timeFormat = parsePattern(timeSection.getString("pattern", "yyyy-MM-dd HH:mm"), logger);

        return new StoreSettings(
            format,
            zone,
            timeFormat,
            readStorage(section(config, "storage"), logger),
            readCredit(section(config, "economy.credit"), logger),
            readVault(section(config, "economy.vault")),
            readMoney(section(config, "economy.money"), logger),
            readTopup(section(config, "topup"), logger),
            config.getString("payment.default-provider", "manual").trim().toLowerCase(Locale.ROOT),
            readProviders(config.getConfigurationSection("payment.providers"), logger),
            readDelivery(section(config, "delivery"), logger),
            readShop(section(config, "shop"), logger),
            readVip(section(config, "vip"), logger),
            readAudit(section(config, "audit"), logger),
            readLimits(section(config, "limits"), logger)
        );
    }

    private static Storage readStorage(ConfigurationSection section, Logger logger) {
        StorageType type = ConfigParse.enumValue(StorageType.class, section.getString("type"),
            StorageType.SQLITE, "storage.type", logger);
        String prefix = ConfigParse.trimmedOrEmpty(section.getString("table-prefix", "store_"));
        if (!ConfigParse.isSafeId(prefix.isEmpty() ? "x" : prefix)) {
            // The prefix is concatenated into DDL, so anything but [A-Za-z0-9_-] is refused.
            logger.warning("設定 storage.table-prefix 含有不安全的字元，已改用預設值 store_。");
            prefix = "store_";
        }
        ConfigurationSection pool = section(section, "pool");
        return new Storage(
            type,
            prefix,
            ConfigParse.trimmedOrEmpty(section.getString("sqlite.file", "store.db")),
            ConfigParse.trimmedOrEmpty(section.getString("mysql.host", "127.0.0.1")),
            ConfigParse.boundedInt(section, "mysql.port", 3306, 1, 65535, logger),
            ConfigParse.trimmedOrEmpty(section.getString("mysql.database", "aelorn_store")),
            ConfigParse.trimmedOrEmpty(section.getString("mysql.user", "")),
            section.getString("mysql.password", ""),
            ConfigParse.trimmedOrEmpty(section.getString("mysql.properties", "")),
            ConfigParse.boundedInt(pool, "max-connections", 4, 1, 32, logger),
            ConfigParse.boundedInt(pool, "connection-timeout-seconds", 10, 1, 120, logger),
            ConfigParse.boundedInt(pool, "validate-after-idle-seconds", 30, 0, 3600, logger)
        );
    }

    private static Credit readCredit(ConfigurationSection section, Logger logger) {
        return new Credit(
            section.getString("display-name", "儲值金"),
            section.getString("symbol", ""),
            ConfigParse.boundedLong(section, "minor-units-per-credit", 100L, 1L, 1_000_000L, logger),
            ConfigParse.boundedLong(section, "max-balance", 100_000_000L, 1L, Long.MAX_VALUE / 4, logger)
        );
    }

    private static VaultSettings readVault(ConfigurationSection section) {
        return new VaultSettings(section.getBoolean("enabled", true), section.getString("display-name", "金幣"));
    }

    private static Money readMoney(ConfigurationSection section, Logger logger) {
        return new Money(
            section.getString("display", "NT${amount}"),
            ConfigParse.boundedInt(section, "decimals", 0, 0, 4, logger),
            section.getBoolean("require-whole-units", true)
        );
    }

    private static Topup readTopup(ConfigurationSection section, Logger logger) {
        String prefix = ConfigParse.trimmedOrEmpty(section.getString("order-prefix", "AE"));
        if (!ConfigParse.isSafeId(prefix.isEmpty() ? "AE" : prefix) || prefix.length() > 6) {
            // Order numbers must stay within ECPay's 20-character MerchantTradeNo limit.
            logger.warning("設定 topup.order-prefix 無效（需為 1-6 碼英數），已改用 AE。");
            prefix = "AE";
        }
        long min = ConfigParse.boundedLong(section, "min-amount-minor", 3000L, 1L, Long.MAX_VALUE / 4, logger);
        long max = ConfigParse.boundedLong(section, "max-amount-minor", 2_000_000L, 1L, Long.MAX_VALUE / 4, logger);
        if (min > max) {
            logger.warning("設定 topup.min-amount-minor 大於 max-amount-minor，已對調。");
            long swap = min;
            min = max;
            max = swap;
        }
        return new Topup(
            section.getBoolean("enabled", true),
            ConfigParse.trimmedOrEmpty(section.getString("currency", "TWD")),
            ConfigParse.boundedInt(section, "order-expire-minutes", 60, 1, 43200, logger),
            ConfigParse.boundedInt(section, "max-pending-orders", 3, 1, 100, logger),
            min,
            max,
            ConfigParse.boundedLong(section, "daily-limit-minor", 1_000_000L, 0L, Long.MAX_VALUE / 4, logger),
            ConfigParse.boundedInt(section, "account-age-hours", 0, 0, 8760, logger),
            prefix
        );
    }

    private static Map<String, ProviderSettings> readProviders(ConfigurationSection section, Logger logger) {
        Map<String, ProviderSettings> providers = new LinkedHashMap<>();
        if (section == null) {
            return Map.of();
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String id = key.trim().toLowerCase(Locale.ROOT);
            if (!ConfigParse.isSafeId(id)) {
                logger.warning("金流管道 id「" + key + "」含有不允許的字元，已略過。");
                continue;
            }
            providers.put(id, new ProviderSettings(
                id,
                entry.getBoolean("enabled", false),
                entry.getString("display-name", id),
                ConfigParse.material(entry.getString("icon"), Material.PAPER,
                    "payment.providers." + id + ".icon", logger),
                ConfigParse.trimmedOrEmpty(entry.getString("checkout-url", "")),
                ConfigParse.stringList(entry, "instructions")
            ));
        }
        return Map.copyOf(providers);
    }

    private static Delivery readDelivery(ConfigurationSection section, Logger logger) {
        return new Delivery(
            ConfigParse.boundedInt(section, "poll-seconds", 10, 1, 3600, logger),
            ConfigParse.boundedInt(section, "batch-size", 20, 1, 500, logger),
            section.getBoolean("queue-on-offline", true),
            ConfigParse.boundedInt(section, "max-attempts", 5, 1, 100, logger),
            ConfigParse.boundedInt(section, "retry-backoff-seconds", 30, 0, 86400, logger),
            section.getBoolean("notify-player", true),
            ConfigParse.boundedLong(section, "broadcast-above-minor", 50_000L, 0L, Long.MAX_VALUE / 4, logger)
        );
    }

    private static Shop readShop(ConfigurationSection section, Logger logger) {
        return new Shop(
            section.getBoolean("confirm-purchase", true),
            section.getBoolean("close-on-purchase", false),
            ConfigParse.boundedInt(section, "self-refund-minutes", 0, 0, 43200, logger)
        );
    }

    private static Vip readVip(ConfigurationSection section, Logger logger) {
        List<Integer> warnDays = new ArrayList<>();
        for (int day : section.getIntegerList("expire-warn-days")) {
            if (day > 0 && !warnDays.contains(day)) {
                warnDays.add(day);
            }
        }
        return new Vip(
            ConfigParse.boundedInt(section, "check-seconds", 60, 5, 3600, logger),
            warnDays,
            ConfigParse.enumValue(UpgradePolicy.class, section.getString("upgrade-policy"),
                UpgradePolicy.UPGRADE, "vip.upgrade-policy", logger)
        );
    }

    private static Audit readAudit(ConfigurationSection section, Logger logger) {
        return new Audit(
            section.getBoolean("enabled", true),
            ConfigParse.boundedInt(section, "keep-days", 365, 0, 36500, logger),
            ConfigParse.trimmedOrEmpty(section.getString("file", "audit.log"))
        );
    }

    private static Limits readLimits(ConfigurationSection section, Logger logger) {
        return new Limits(
            ConfigParse.boundedLong(section, "command-cooldown-ms", 500L, 0L, 60_000L, logger),
            ConfigParse.boundedLong(section, "gui-click-cooldown-ms", 250L, 0L, 60_000L, logger)
        );
    }

    /** The channel a player gets when they do not choose, falling back to any enabled one. */
    public ProviderSettings resolveProvider(String id) {
        ProviderSettings requested = providers.get(id.toLowerCase(Locale.ROOT));
        if (requested != null && requested.enabled()) {
            return requested;
        }
        ProviderSettings fallback = providers.get(defaultProvider);
        if (fallback != null && fallback.enabled()) {
            return fallback;
        }
        for (ProviderSettings candidate : providers.values()) {
            if (candidate.enabled()) {
                return candidate;
            }
        }
        return null;
    }

    public List<ProviderSettings> enabledProviders() {
        List<ProviderSettings> enabled = new ArrayList<>();
        for (ProviderSettings provider : providers.values()) {
            if (provider.enabled()) {
                enabled.add(provider);
            }
        }
        return List.copyOf(enabled);
    }

    private static ZoneId parseZone(String raw, Logger logger) {
        try {
            return ZoneId.of(raw.trim());
        } catch (RuntimeException invalid) {
            logger.warning("設定 time.zone 無效「" + raw + "」，已改用系統時區。");
            return ZoneId.systemDefault();
        }
    }

    private static DateTimeFormatter parsePattern(String raw, Logger logger) {
        try {
            return DateTimeFormatter.ofPattern(raw.trim(), Locale.ROOT);
        } catch (RuntimeException invalid) {
            logger.warning("設定 time.pattern 無效「" + raw + "」，已改用 yyyy-MM-dd HH:mm。");
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
        }
    }

    /** Never returns null, so every reader can assume a section exists and use its defaults. */
    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection found = parent.getConfigurationSection(path);
        return found != null ? found : parent.createSection(path);
    }
}
