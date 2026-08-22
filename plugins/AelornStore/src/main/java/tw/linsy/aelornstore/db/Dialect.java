package tw.linsy.aelornstore.db;

import java.util.ArrayList;
import java.util.List;

/**
 * The two SQL flavours this plugin speaks.
 *
 * The differences are small but real — auto-increment syntax, whether
 * {@code CREATE INDEX} accepts {@code IF NOT EXISTS}, and table options — so each
 * dialect owns its full DDL rather than the caller string-patching one template.
 * Every other query in {@link StoreDao} is portable ANSI SQL on purpose: no
 * {@code FOR UPDATE}, no upsert syntax, no vendor functions.
 */
public enum Dialect {

    SQLITE("org.sqlite.JDBC"),
    MYSQL("com.mysql.cj.jdbc.Driver");

    private final String driverClass;

    Dialect(String driverClass) {
        this.driverClass = driverClass;
    }

    public String driverClass() {
        return driverClass;
    }

    /** Statements are executed in order and must all be idempotent. */
    public List<String> schema(String p) {
        List<String> statements = new ArrayList<>();
        boolean mysql = this == MYSQL;
        String id = mysql ? "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        String tail = mysql ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" : "";

        statements.add("CREATE TABLE IF NOT EXISTS " + p + "meta ("
            + "meta_key VARCHAR(64) NOT NULL PRIMARY KEY,"
            + "meta_value VARCHAR(255)"
            + ")" + tail);

        statements.add("CREATE TABLE IF NOT EXISTS " + p + "wallet ("
            + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
            + "balance BIGINT NOT NULL DEFAULT 0,"
            + "updated_at BIGINT NOT NULL DEFAULT 0"
            + ")" + tail);

        // The ledger is append-only. balance_after is stored so a dispute can be
        // reconstructed from the rows alone, without replaying every prior entry.
        statements.add("CREATE TABLE IF NOT EXISTS " + p + "wallet_tx ("
            + "id " + id + ","
            + "uuid VARCHAR(36) NOT NULL,"
            + "delta BIGINT NOT NULL,"
            + "balance_after BIGINT NOT NULL,"
            + "kind VARCHAR(32) NOT NULL,"
            + "ref VARCHAR(64),"
            + "note VARCHAR(255),"
            + "created_at BIGINT NOT NULL"
            + (mysql ? ",KEY idx_wtx_player (uuid, created_at),KEY idx_wtx_ref (ref)" : "")
            + ")" + tail);

        statements.add("CREATE TABLE IF NOT EXISTS " + p + "orders ("
            + "order_no VARCHAR(32) NOT NULL PRIMARY KEY,"
            + "uuid VARCHAR(36) NOT NULL,"
            + "player_name VARCHAR(32) NOT NULL,"
            + "type VARCHAR(16) NOT NULL,"
            + "product_id VARCHAR(64),"
            + "quantity INT NOT NULL DEFAULT 1,"
            + "amount_minor BIGINT NOT NULL DEFAULT 0,"
            + "credit_amount BIGINT NOT NULL DEFAULT 0,"
            + "currency VARCHAR(16),"
            + "provider VARCHAR(32) NOT NULL,"
            + "provider_trade_no VARCHAR(64),"
            + "pay_method VARCHAR(32),"
            + "status VARCHAR(24) NOT NULL,"
            + "created_at BIGINT NOT NULL,"
            + "paid_at BIGINT NOT NULL DEFAULT 0,"
            + "delivered_at BIGINT NOT NULL DEFAULT 0,"
            + "expires_at BIGINT NOT NULL DEFAULT 0,"
            + "attempts INT NOT NULL DEFAULT 0,"
            // Retry backoff lives on the row so a failing delivery does not spin
            // every poll cycle, and so the wait survives a restart.
            + "next_attempt_at BIGINT NOT NULL DEFAULT 0,"
            + "fail_reason VARCHAR(255)"
            + (mysql ? ",KEY idx_order_player (uuid, created_at),"
                     + "KEY idx_order_status (status, next_attempt_at),"
                     + "KEY idx_order_trade (provider, provider_trade_no)" : "")
            + ")" + tail);

        statements.add("CREATE TABLE IF NOT EXISTS " + p + "vip ("
            + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
            + "tier VARCHAR(64) NOT NULL,"
            + "expires_at BIGINT NOT NULL DEFAULT 0,"
            + "updated_at BIGINT NOT NULL DEFAULT 0,"
            + "source_order VARCHAR(32)"
            + (mysql ? ",KEY idx_vip_expiry (expires_at)" : "")
            + ")" + tail);

        // One row per delivered unit. Stock and per-player caps are counted from
        // here rather than kept as a mutable counter, so a refund that deletes the
        // row automatically returns the stock and the allowance together.
        statements.add("CREATE TABLE IF NOT EXISTS " + p + "purchases ("
            + "id " + id + ","
            + "uuid VARCHAR(36) NOT NULL,"
            + "product_id VARCHAR(64) NOT NULL,"
            + "quantity INT NOT NULL DEFAULT 1,"
            + "order_no VARCHAR(32) NOT NULL,"
            + "day_key VARCHAR(10) NOT NULL,"
            + "created_at BIGINT NOT NULL"
            + (mysql ? ",KEY idx_buy_player (uuid, product_id),"
                     + "KEY idx_buy_product (product_id),"
                     + "KEY idx_buy_day (uuid, product_id, day_key),"
                     + "KEY idx_buy_order (order_no)" : "")
            + ")" + tail);

        statements.add("CREATE TABLE IF NOT EXISTS " + p + "audit ("
            + "id " + id + ","
            + "actor VARCHAR(64) NOT NULL,"
            + "action VARCHAR(64) NOT NULL,"
            + "target VARCHAR(64),"
            + "detail VARCHAR(512),"
            + "created_at BIGINT NOT NULL"
            + (mysql ? ",KEY idx_audit_target (target, created_at)" : "")
            + ")" + tail);

        if (!mysql) {
            statements.add("CREATE INDEX IF NOT EXISTS idx_wtx_player ON " + p + "wallet_tx (uuid, created_at)");
            statements.add("CREATE INDEX IF NOT EXISTS idx_wtx_ref ON " + p + "wallet_tx (ref)");
            statements.add("CREATE INDEX IF NOT EXISTS idx_order_player ON " + p + "orders (uuid, created_at)");
            statements.add("CREATE INDEX IF NOT EXISTS idx_order_status ON " + p + "orders (status, next_attempt_at)");
            statements.add("CREATE INDEX IF NOT EXISTS idx_order_trade ON " + p + "orders (provider, provider_trade_no)");
            statements.add("CREATE INDEX IF NOT EXISTS idx_vip_expiry ON " + p + "vip (expires_at)");
            statements.add("CREATE INDEX IF NOT EXISTS idx_buy_player ON " + p + "purchases (uuid, product_id)");
            statements.add("CREATE INDEX IF NOT EXISTS idx_buy_product ON " + p + "purchases (product_id)");
            statements.add("CREATE INDEX IF NOT EXISTS idx_buy_day ON " + p + "purchases (uuid, product_id, day_key)");
            statements.add("CREATE INDEX IF NOT EXISTS idx_buy_order ON " + p + "purchases (order_no)");
            statements.add("CREATE INDEX IF NOT EXISTS idx_audit_target ON " + p + "audit (target, created_at)");
        }
        return statements;
    }

    /** Session pragmas/settings applied to every new connection. */
    public List<String> sessionSetup(int busyTimeoutMillis) {
        if (this == SQLITE) {
            return List.of(
                // WAL lets the web backend read while the plugin writes, on the same file.
                "PRAGMA journal_mode=WAL",
                "PRAGMA synchronous=NORMAL",
                "PRAGMA foreign_keys=ON",
                "PRAGMA busy_timeout=" + busyTimeoutMillis);
        }
        return List.of();
    }
}
