package tw.linsy.aelornstore;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelornstore.command.AdminCommand;
import tw.linsy.aelornstore.command.StoreCommand;
import tw.linsy.aelornstore.config.Catalog;
import tw.linsy.aelornstore.config.StoreSettings;
import tw.linsy.aelornstore.db.Database;
import tw.linsy.aelornstore.db.StoreDao;
import tw.linsy.aelornstore.payment.PaymentRegistry;
import tw.linsy.aelornstore.service.ActionRunner;
import tw.linsy.aelornstore.service.DeliveryService;
import tw.linsy.aelornstore.service.OrderService;
import tw.linsy.aelornstore.service.Storefront;
import tw.linsy.aelornstore.service.VaultBridge;
import tw.linsy.aelornstore.service.VipService;
import tw.linsy.aelornstore.service.WalletService;
import tw.linsy.aelornstore.ui.MenuListener;
import tw.linsy.aelornstore.ui.StoreMenu;
import tw.linsy.aelornstore.util.AuditLog;
import tw.linsy.aelornstore.util.Clock;
import tw.linsy.aelornstore.util.Cooldowns;
import tw.linsy.aelorn.lib.AelornLib;
import tw.linsy.aelorn.lib.sched.Schedulers;
import tw.linsy.aelorn.lib.text.Messages;
import tw.linsy.aelorn.lib.text.Text;

/**
 * Wiring and lifecycle for the store.
 *
 * Configuration is held as three immutable snapshots ({@link StoreSettings},
 * {@link Catalog}, {@link Messages}) published through volatile fields. A reload
 * builds new ones and swaps the references, so a purchase already in flight
 * finishes against the definitions it started with and never observes a
 * half-applied config.
 *
 * <p>Storage settings are the one exception: changing them takes a restart.
 * Swapping a live connection pool underneath in-flight transactions would risk
 * exactly the kind of partial write this design exists to prevent.
 */
public final class AelornStorePlugin extends JavaPlugin {

    /** How often abandoned deliveries are swept back into the queue. */
    private static final long RECOVERY_INTERVAL_SECONDS = 300L;
    /** How often expired audit rows are pruned. */
    private static final long AUDIT_PURGE_INTERVAL_HOURS = 6L;

    /** Folia scheduling from the core, so every Aelorn plugin hops threads the same way. */
    private Schedulers schedulers;

    private volatile StoreSettings settings;
    private volatile Catalog catalog;
    private volatile Messages messages;
    private volatile Clock clock;
    private volatile boolean storageReady;

    private Database database;
    private StoreDao dao;
    private AuditLog auditLog;
    private WalletService walletService;
    private VipService vipService;
    private OrderService orderService;
    private DeliveryService deliveryService;
    private ActionRunner actionRunner;
    private Storefront storefront;
    private StoreMenu menus;
    private VaultBridge vault;

    private final PaymentRegistry payments = new PaymentRegistry();
    private final Cooldowns commandCooldowns = new Cooldowns();
    private final Cooldowns clickCooldowns = new Cooldowns();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.schedulers = AelornLib.require().schedulersFor(this);
        loadConfiguration();

        database = new Database(settings.storage(), getDataFolder(), getLogger());
        dao = new StoreDao(database);
        try {
            database.open();
            storageReady = true;
            getLogger().info("已連線至 " + settings.storage().describe() + "。");
        } catch (SQLException failure) {
            // Everything here moves money. Running with no ledger is worse than
            // not running at all, so the plugin stays up but refuses to trade.
            getLogger().log(Level.SEVERE, "無法連線至商店資料庫；商店功能將停用，"
                + "修正 config.yml 的 storage 設定後重新啟動伺服器。", failure);
        }

        auditLog = new AuditLog(dao, () -> settings.audit(), clock, getDataFolder(), getLogger());
        actionRunner = new ActionRunner(this);
        walletService = new WalletService(this, dao, auditLog, clock);
        vipService = new VipService(this, dao, actionRunner, auditLog, clock);
        orderService = new OrderService(this, dao, walletService, vipService, actionRunner, auditLog, clock);
        deliveryService = new DeliveryService(this, dao, orderService, actionRunner, auditLog, clock);
        actionRunner.bind(walletService, vipService);
        storefront = new Storefront(this);
        menus = new StoreMenu(this);
        vault = settings.vault().enabled() ? VaultBridge.attach(getLogger()) : null;
        payments.rebuild(settings);

        register("store", new StoreCommand(this));
        register("aelornstore", new AdminCommand(this));
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        if (storageReady) {
            startTasks();
        }
        getLogger().info("AelornStore 已啟用：商品 " + catalog.products().size()
            + " 項、VIP " + catalog.vipTiers().size() + " 級、金流管道 "
            + payments.describeUsable(settings) + "。");
    }

    @Override
    public void onDisable() {
        
        schedulers().cancelAll();
        if (database != null) {
            database.close();
        }
        storageReady = false;
    }

    private void register(String name, Object handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("plugin.yml 未宣告指令 " + name + "，該指令不會註冊。");
            return;
        }
        command.setExecutor((org.bukkit.command.CommandExecutor) handler);
        command.setTabCompleter((org.bukkit.command.TabCompleter) handler);
    }

    /**
     * Starts the background sweeps.
     *
     * All four run on the async scheduler because every one of them is database
     * work; none of them may touch the main thread directly. Their initial delays
     * are staggered so a restart does not fire every sweep on the same tick.
     */
    private void startTasks() {
        long pollSeconds = settings.delivery().pollSeconds();
        schedulers().asyncRepeating(() -> {
            deliveryService.pollOnce();
            orderService.sweepExpired(settings.delivery().batchSize());
        }, pollSeconds, pollSeconds, TimeUnit.SECONDS);

        long vipSeconds = settings.vip().checkSeconds();
        schedulers().asyncRepeating(
            () -> vipService.sweepExpired(settings.delivery().batchSize()),
            vipSeconds, vipSeconds, TimeUnit.SECONDS);

        schedulers().asyncRepeating(
            () -> deliveryService.recoverStaleClaims(),
            30L, RECOVERY_INTERVAL_SECONDS, TimeUnit.SECONDS);

        schedulers().asyncRepeating(
            () -> auditLog.purge(),
            AUDIT_PURGE_INTERVAL_HOURS, AUDIT_PURGE_INTERVAL_HOURS, TimeUnit.HOURS);
    }

    /** Rebuilds every configuration snapshot. Safe to call from an async thread. */
    public void reloadEverything() {
        StoreSettings.Storage before = settings.storage();
        reloadConfig();
        loadConfiguration();
        payments.rebuild(settings);
        if (!before.equals(settings.storage())) {
            getLogger().warning("storage 設定已變更，但資料庫連線不會在執行中切換；"
                + "請重新啟動伺服器以套用。");
        }
    }

    private void loadConfiguration() {
        settings = StoreSettings.load(getConfig(), getLogger());
        clock = new Clock(settings.zone(), settings.timeFormat());
        messages = Messages.load(this, "messages.yml", settings.textFormat());
        catalog = Catalog.load(this, getLogger());
    }

    // ── 存取器 ──────────────────────────────────────────────────────────────

    public Schedulers schedulers() {
        return schedulers;
    }

    public StoreSettings settings() {
        return settings;
    }

    public Catalog catalog() {
        return catalog;
    }

    public Messages messages() {
        return messages;
    }

    public Clock clock() {
        return clock;
    }

    public Text.Format textFormat() {
        return settings.textFormat();
    }

    public StoreDao dao() {
        return dao;
    }

    public WalletService wallet() {
        return walletService;
    }

    public VipService vip() {
        return vipService;
    }

    public OrderService orders() {
        return orderService;
    }

    public DeliveryService delivery() {
        return deliveryService;
    }

    public Storefront storefront() {
        return storefront;
    }

    public StoreMenu menus() {
        return menus;
    }

    public PaymentRegistry payments() {
        return payments;
    }

    public @Nullable VaultBridge vault() {
        return vault;
    }

    public Cooldowns commandCooldowns() {
        return commandCooldowns;
    }

    public Cooldowns clickCooldowns() {
        return clickCooldowns;
    }

    /** False when the database never came up; every trading path checks this first. */
    public boolean storageReady() {
        return storageReady;
    }
}
