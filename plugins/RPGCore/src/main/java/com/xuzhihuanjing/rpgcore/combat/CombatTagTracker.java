package com.xuzhihuanjing.rpgcore.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 戰鬥標記 —— 判斷玩家「是不是正在打架」。
 *
 * <p>資源回復、怒氣衰退、脫戰回血這類機制全都需要這個判斷，
 * 但插件先前並沒有任何地方在追蹤它。與其讓每個系統各自猜，
 * 不如集中記錄一次：只要造成傷害或受到傷害就重新計時，
 * 超過設定秒數沒有交火即視為脫離戰鬥。</p>
 *
 * <p>只存時間戳，不存實體參照，因此不會妨礙玩家物件被回收，
 * 判定本身也只是純數字比較，可在任意執行緒安全呼叫。</p>
 */
public final class CombatTagTracker implements Listener {

    /** 預設脫戰秒數。 */
    public static final double DEFAULT_TIMEOUT_SECONDS = 8.0;

    private final Map<UUID, Long> lastCombatMillis = new ConcurrentHashMap<>();
    private final long timeoutMillis;

    public CombatTagTracker() {
        this(DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * @param timeoutSeconds 最後一次交火後多久視為脫戰
     */
    public CombatTagTracker(double timeoutSeconds) {
        double safe = Double.isFinite(timeoutSeconds) && timeoutSeconds > 0
                ? timeoutSeconds
                : DEFAULT_TIMEOUT_SECONDS;
        this.timeoutMillis = (long) (safe * 1000);
    }

    /** 手動標記進入戰鬥，供技能與劇情腳本使用。 */
    public void mark(UUID playerId) {
        if (playerId != null) {
            lastCombatMillis.put(playerId, System.currentTimeMillis());
        }
    }

    /** 玩家目前是否處於戰鬥狀態。 */
    public boolean inCombat(Player player) {
        return player != null && inCombat(player.getUniqueId());
    }

    /** 依識別碼判斷是否處於戰鬥狀態。 */
    public boolean inCombat(UUID playerId) {
        Long last = lastCombatMillis.get(playerId);
        return last != null && System.currentTimeMillis() - last < timeoutMillis;
    }

    /** 距離脫戰還有幾秒；已脫戰時回傳 0。 */
    public double remainingSeconds(UUID playerId) {
        Long last = lastCombatMillis.get(playerId);
        if (last == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, (timeoutMillis - elapsed) / 1000.0);
    }

    /** 立即清除戰鬥標記（重生、傳送、切換角色時使用）。 */
    public void clear(UUID playerId) {
        lastCombatMillis.remove(playerId);
    }

    /** 清空全部標記。 */
    public void clear() {
        lastCombatMillis.clear();
    }

    /**
     * 交火時同時標記攻擊者與被攻擊者。
     *
     * <p>用 {@code MONITOR} 且忽略已取消事件：這裡只是觀察，
     * 不該影響傷害流程，也不該把被其他插件擋下的攻擊算成戰鬥。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            mark(victim.getUniqueId());
        }
        if (event.getDamager() instanceof Player attacker) {
            mark(attacker.getUniqueId());
        }
    }

    /** 離線即清除，避免長期累積死資料。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }
}
