package com.xuzhihuanjing.rpgcore.cast;

import com.xuzhihuanjing.rpgcore.platform.RpgScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 施法讀條 —— 讓長吟唱技能有前搖、有回饋、也能被打斷。
 *
 * <p>先前所有技能都是按下即生效，於是「大招」和「小技能」在操作上毫無區別，
 * 對手也完全沒有反應窗口。加上讀條之後，強力技能需要站定吟唱，
 * 敵人看得見、打得斷，攻防才有來回。</p>
 *
 * <h2>打斷條件</h2>
 * <ul>
 *   <li><b>移動</b>：超過容許距離即中斷（可設定，預設 0.4 格）</li>
 *   <li><b>受擊</b>：由外部呼叫 {@link #interrupt}</li>
 *   <li><b>離線／死亡</b>：自動清除</li>
 * </ul>
 *
 * <p>容許距離刻意不設為 0：玩家站著不動時座標仍會因碰撞箱微調而有極小變化，
 * 設成 0 會讓讀條莫名其妙自己斷掉，而且極難重現與回報。</p>
 *
 * <h2>Folia</h2>
 * <p>每次讀條都跑在該玩家自己的 {@code EntityScheduler} 上，
 * 只存取自己的位置與動作列，不跨區域碰其他實體。</p>
 */
public final class CastBarService {

    /** 讀條刷新間隔的預設值（tick）。2 tick 約 10 FPS，肉眼已是平滑的。 */
    public static final long DEFAULT_REFRESH_TICKS = 2L;
    /** 進度條格數的預設值。 */
    public static final int DEFAULT_BAR_SEGMENTS = 20;
    /** 移動打斷容忍距離的預設值（格）。 */
    public static final double DEFAULT_MOVE_TOLERANCE = 0.4;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final RpgScheduler scheduler;
    private final Map<UUID, ActiveCast> active = new ConcurrentHashMap<>();
    private final double moveTolerance;
    /**
     * 刷新間隔與進度條格數。
     *
     * <p>這兩個值直接決定讀條的手感與伺服器負擔：格數影響玩家能不能看清進度，
     * 刷新間隔則是每位吟唱中的玩家每秒要送幾個封包。不同伺服器的取捨不同，
     * 因此交給設定檔決定，而不是埋在程式裡。</p>
     */
    private final long refreshTicks;
    private final int barSegments;

    public CastBarService(RpgScheduler scheduler, double moveTolerance) {
        this(scheduler, moveTolerance, DEFAULT_REFRESH_TICKS, DEFAULT_BAR_SEGMENTS);
    }

    /**
     * @param moveTolerance 移動多遠算打斷（格）
     * @param refreshTicks 讀條刷新間隔（tick），至少 1
     * @param barSegments 進度條格數，範圍 5 ~ 60
     */
    public CastBarService(RpgScheduler scheduler, double moveTolerance, long refreshTicks, int barSegments) {
        this.scheduler = scheduler;
        this.moveTolerance = Double.isFinite(moveTolerance) && moveTolerance > 0
                ? moveTolerance : DEFAULT_MOVE_TOLERANCE;
        this.refreshTicks = Math.max(1L, refreshTicks);
        // 上限 60 是為了避免有人填出一條在聊天列換行的進度條
        this.barSegments = Math.max(5, Math.min(60, barSegments));
    }

    /** 玩家是否正在吟唱。 */
    public boolean isCasting(Player player) {
        return player != null && active.containsKey(player.getUniqueId());
    }

    /**
     * 玩家目前正在吟唱哪個技能。
     *
     * <p>{@link #isCasting} 只答得出「有沒有在唱」，答不出「唱的是哪一個」。
     * 想擋下重複施放、或在介面上顯示吟唱中的技能名稱時都需要這個。</p>
     *
     * @return 技能代號；沒有在吟唱時回傳 {@code null}
     */
    public String castingAbility(Player player) {
        if (player == null) {
            return null;
        }
        ActiveCast cast = active.get(player.getUniqueId());
        return cast == null ? null : cast.abilityId;
    }

    /**
     * 開始吟唱。
     *
     * @param player 施法者
     * @param abilityId 技能代號
     * @param displayName 顯示名稱（已去除格式標記）
     * @param seconds 吟唱秒數
     * @param onComplete 吟唱完成時執行；跑在玩家自己的區域執行緒上
     * @param onInterrupt 被打斷時執行；可為 {@code null}
     * @return 成功開始為 {@code true}；已在吟唱中則為 {@code false}
     */
    public boolean beginCast(Player player, String abilityId, String displayName,
                             double seconds, Runnable onComplete, Runnable onInterrupt) {
        if (player == null || seconds <= 0) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (active.containsKey(playerId)) {
            // 已經在唱了,不接受插隊
            return false;
        }

        long totalTicks = Math.max(1L, Math.round(seconds * 20.0));
        ActiveCast cast = new ActiveCast(abilityId, displayName, player.getLocation().clone(),
                totalTicks, onComplete, onInterrupt);
        active.put(playerId, cast);

        ScheduledTask task = scheduler.runEntityAtFixedRate(
                player,
                scheduled -> tick(player, cast, scheduled),
                () -> active.remove(playerId),
                refreshTicks,
                refreshTicks);
        cast.task = task;
        if (task == null) {
            // 排程建立失敗（玩家正在被移除）,別讓狀態卡住
            active.remove(playerId);
            return false;
        }
        render(player, cast);
        return true;
    }

    /**
     * 打斷吟唱。
     *
     * @param reason 顯示給玩家的原因，例如「受到攻擊」
     * @return 確實中斷了一次吟唱為 {@code true}
     */
    public boolean interrupt(Player player, String reason) {
        if (player == null) {
            return false;
        }
        ActiveCast cast = active.remove(player.getUniqueId());
        if (cast == null) {
            return false;
        }
        scheduler.cancel(cast.task);
        actionBar(player, "&c✖ 吟唱中斷 &7｜ &f" + cast.displayName
                + (reason == null || reason.isBlank() ? "" : " &7（" + reason + "）"));
        if (cast.onInterrupt != null) {
            cast.onInterrupt.run();
        }
        return true;
    }

    /** 靜默清除（登出、切換角色時使用）。 */
    public void clear(UUID playerId) {
        ActiveCast cast = active.remove(playerId);
        if (cast != null) {
            scheduler.cancel(cast.task);
        }
    }

    /** 停止所有吟唱，插件停用時呼叫。 */
    public void shutdown() {
        active.values().forEach(cast -> scheduler.cancel(cast.task));
        active.clear();
    }

    /** 單次刷新：檢查打斷條件、更新畫面、判斷是否完成。 */
    private void tick(Player player, ActiveCast cast, ScheduledTask task) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || player.isDead()) {
            active.remove(playerId);
            scheduler.cancel(task);
            return;
        }
        // 這次刷新對應的仍是同一次吟唱嗎（避免舊排程殘留時誤傷新吟唱）
        if (active.get(playerId) != cast) {
            scheduler.cancel(task);
            return;
        }

        // 用可重複使用的 Location 承接座標:讀條每 2 tick 刷新一次,
        // 每次都 new 一個 Location 只是白白製造垃圾。
        // 這裡跑在玩家自己的區域執行緒上,緩衝區不會被其他執行緒碰到。
        player.getLocation(cast.probe);
        if (cast.probe.distanceSquared(cast.origin) > moveTolerance * moveTolerance) {
            scheduler.cancel(task);
            interrupt(player, "移動中斷");
            return;
        }

        cast.elapsedTicks += refreshTicks;
        if (cast.elapsedTicks < cast.totalTicks) {
            render(player, cast);
            return;
        }

        // 完成:先移除狀態再執行,免得回呼裡再次施法時被自己擋下
        active.remove(playerId);
        scheduler.cancel(task);
        actionBar(player, "&a✔ 吟唱完成 &7｜ &f" + cast.displayName);
        if (cast.onComplete != null) {
            cast.onComplete.run();
        }
    }

    /** 畫出進度條。 */
    private void render(Player player, ActiveCast cast) {
        double progress = Math.max(0, Math.min(1, (double) cast.elapsedTicks / cast.totalTicks));
        int filled = (int) Math.round(progress * barSegments);
        double remaining = Math.max(0, (cast.totalTicks - cast.elapsedTicks) / 20.0);
        String bar = "&b" + "▉".repeat(filled) + "&8" + "▉".repeat(barSegments - filled);
        actionBar(player, "&f" + cast.displayName + " &7｜ " + bar
                + String.format(Locale.ROOT, " &7%.1f 秒", remaining));
    }

    private static void actionBar(Player player, String legacyText) {
        player.sendActionBar(LEGACY.deserialize(legacyText));
    }

    /** 一次進行中的吟唱。 */
    private static final class ActiveCast {
        private final String abilityId;
        private final String displayName;
        private final Location origin;
        /** 位置檢查用的可重複使用緩衝區，避免每次刷新都配置新的 Location。 */
        private final Location probe;
        private final long totalTicks;
        private final Runnable onComplete;
        private final Runnable onInterrupt;
        private long elapsedTicks;
        private ScheduledTask task;

        private ActiveCast(String abilityId, String displayName, Location origin, long totalTicks,
                           Runnable onComplete, Runnable onInterrupt) {
            this.abilityId = abilityId;
            this.displayName = displayName;
            this.origin = origin;
            this.probe = origin.clone();
            this.totalTicks = totalTicks;
            this.onComplete = onComplete;
            this.onInterrupt = onInterrupt;
        }
    }
}
