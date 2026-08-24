package tw.linsy.aelorn.rpgcore.reagent;

import tw.linsy.aelorn.rpgcore.platform.RpgScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * 資源系統 —— 統一管理法力、怒氣、精力、連擊點等各式職業資源。
 *
 * <p>在此之前，插件只有一條寫死的法力值，任何新資源都得從排程、上限、
 * HUD 一路重寫一遍。本服務把「資源」抽象化，新增一種資源只要在
 * {@code reagents.yml} 補一段設定即可，程式不必動。</p>
 *
 * <h2>設計要點</h2>
 * <ul>
 *   <li><b>不搶既有法力的管轄權</b>：法力仍由 {@code PlayerCombatState} 保管，
 *       本服務只負責法力以外的資源，避免兩套系統各記一份而對不上。
 *       需要統一查詢時透過 {@link #registerExternal} 掛上唯讀的橋接。</li>
 *   <li><b>依賴以函式注入</b>：戰鬥狀態、角色等級、角色識別碼都以介面傳入，
 *       本套件因此不依賴戰鬥與角色模組，可獨立編譯與測試。</li>
 *   <li><b>Folia 安全</b>：每位玩家的回復排程都跑在自己的
 *       {@code EntityScheduler} 上，不跨區域存取實體。</li>
 * </ul>
 */
public final class ReagentService {

    /** 預設每秒結算一次。 */
    private static final long DEFAULT_PERIOD_TICKS = 20L;

    private final RpgScheduler scheduler;
    private final Predicate<Player> inCombat;
    private final ToIntFunction<Player> levelResolver;
    private final CharacterIdResolver characterIdResolver;

    private final Map<String, ReagentType> types = new ConcurrentHashMap<>();
    private final Map<UUID, ReagentPool> pools = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, ExternalReagent> external = new ConcurrentHashMap<>();

    private long periodTicks = DEFAULT_PERIOD_TICKS;

    /**
     * @param scheduler Folia 相容排程器
     * @param inCombat 判斷玩家是否處於戰鬥狀態
     * @param levelResolver 取得玩家目前角色等級
     * @param characterIdResolver 取得玩家目前角色的識別碼
     */
    public ReagentService(RpgScheduler scheduler,
                          Predicate<Player> inCombat,
                          ToIntFunction<Player> levelResolver,
                          CharacterIdResolver characterIdResolver) {
        this.scheduler = scheduler;
        this.inCombat = inCombat == null ? player -> false : inCombat;
        this.levelResolver = levelResolver == null ? player -> 1 : levelResolver;
        this.characterIdResolver = characterIdResolver == null
                ? player -> player.getUniqueId()
                : characterIdResolver;
    }

    // ------------------------------------------------------------------
    // 設定載入
    // ------------------------------------------------------------------

    /**
     * 由設定區段載入所有資源定義，重載時會整批換新。
     *
     * @param section {@code reagents} 區段
     * @return 載入的資源數量
     */
    public int load(ConfigurationSection section) {
        types.clear();
        if (section == null) {
            return 0;
        }
        this.periodTicks = Math.max(1L, section.getLong("tick-period-ticks", DEFAULT_PERIOD_TICKS));

        ConfigurationSection list = section.getConfigurationSection("types");
        ConfigurationSection source = list == null ? section : list;
        for (String key : source.getKeys(false)) {
            ConfigurationSection entry = source.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            ReagentType type = ReagentType.from(key, entry);
            if (!type.id().isBlank()) {
                types.put(type.id(), type);
            }
        }
        return types.size();
    }

    /** 以程式方式登錄一種資源，供測試與內建預設值使用。 */
    public void register(ReagentType type) {
        if (type != null && !type.id().isBlank()) {
            types.put(type.id(), type);
        }
    }

    /**
     * 掛上外部資源橋接 —— 讓由其他模組保管的資源（例如法力）
     * 也能透過本服務統一查詢，但寫入權仍留在原模組。
     */
    public void registerExternal(String typeId, ExternalReagent bridge) {
        if (typeId != null && bridge != null) {
            external.put(typeId.toLowerCase(Locale.ROOT), bridge);
        }
    }

    /** 全部資源定義，依 {@code order} 排序。 */
    public List<ReagentType> types() {
        return types.values().stream()
                .sorted(Comparator.comparingInt(ReagentType::order).thenComparing(ReagentType::id))
                .toList();
    }

    /** 指定職業可用的資源定義，依 {@code order} 排序。 */
    public List<ReagentType> typesFor(String classId) {
        return types.values().stream()
                .filter(type -> type.appliesTo(classId))
                .sorted(Comparator.comparingInt(ReagentType::order).thenComparing(ReagentType::id))
                .toList();
    }

    /** 取得單一資源定義；不存在時回傳 {@code null}。 */
    public ReagentType type(String typeId) {
        return typeId == null ? null : types.get(typeId.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // 生命週期
    // ------------------------------------------------------------------

    /**
     * 玩家進入遊戲或切換角色時呼叫：建立資源池並啟動回復排程。
     *
     * @param player 玩家
     * @param classId 目前職業代號
     */
    public void attach(Player player, String classId) {
        if (player == null) {
            return;
        }
        UUID characterId = characterIdResolver.resolve(player);
        if (characterId == null) {
            return;
        }
        int level = levelResolver.applyAsInt(player);
        ReagentPool pool = pools.computeIfAbsent(characterId, ReagentPool::new);

        // 只保留這個職業用得到的資源，轉職後舊資源會自動退場
        List<ReagentType> applicable = typesFor(classId);
        for (ReagentType type : applicable) {
            pool.register(type.id(), type.maxAt(level), type.startValueAt(level));
        }
        // 轉職後不再適用的資源要退場，否則戰士身上會殘留一條永遠不動的法力條
        Set<String> keep = applicable.stream()
                .map(ReagentType::id)
                .collect(Collectors.toUnmodifiableSet());
        pool.snapshot().keySet().stream()
                .filter(id -> !keep.contains(id))
                .toList()
                .forEach(pool::remove);

        startTicking(player, characterId);
    }

    /** 玩家離線時呼叫：停止排程。資源值保留在記憶體中，重新上線可續用。 */
    public void detach(Player player) {
        if (player == null) {
            return;
        }
        ScheduledTask task = tasks.remove(player.getUniqueId());
        if (task != null) {
            scheduler.cancel(task);
        }
    }

    /** 角色被刪除時呼叫：連同資源池一併清除。 */
    public void discard(UUID characterId) {
        ReagentPool pool = pools.remove(characterId);
        if (pool != null) {
            pool.clear();
        }
    }

    /** 停止所有排程，插件關閉時呼叫。 */
    public void shutdown() {
        tasks.values().forEach(scheduler::cancel);
        tasks.clear();
    }

    /**
     * 等級或裝備變動後重新計算上限。
     *
     * <p>只調整上限、不動當前值，玩家升級時不會被強制回滿，
     * 也不會因為換下裝備而讓資源憑空蒸發。</p>
     */
    public void refreshMaxima(Player player, String classId) {
        ReagentPool pool = pool(player);
        if (pool == null) {
            return;
        }
        int level = levelResolver.applyAsInt(player);
        for (ReagentType type : typesFor(classId)) {
            pool.register(type.id(), type.maxAt(level), type.startValueAt(level));
        }
    }

    // ------------------------------------------------------------------
    // 查詢與異動
    // ------------------------------------------------------------------

    /** 取得玩家的資源池；尚未建立時回傳 {@code null}。 */
    public ReagentPool pool(Player player) {
        if (player == null) {
            return null;
        }
        UUID characterId = characterIdResolver.resolve(player);
        return characterId == null ? null : pools.get(characterId);
    }

    /** 資源當前值；外部託管的資源會轉向其來源查詢。 */
    public double value(Player player, String typeId) {
        ExternalReagent bridge = external.get(typeId);
        if (bridge != null) {
            return bridge.value(player);
        }
        ReagentPool pool = pool(player);
        return pool == null ? 0 : pool.value(typeId);
    }

    /** 資源上限；外部託管的資源會轉向其來源查詢。 */
    public double max(Player player, String typeId) {
        ExternalReagent bridge = external.get(typeId);
        if (bridge != null) {
            return bridge.max(player);
        }
        ReagentPool pool = pool(player);
        return pool == null ? 0 : pool.max(typeId);
    }

    /** 資源百分比（0~1）。 */
    public double ratio(Player player, String typeId) {
        double max = max(player, typeId);
        return max <= 0 ? 0 : Math.max(0, Math.min(1, value(player, typeId) / max));
    }

    /**
     * 扣除資源。
     *
     * <p>外部託管的資源若其橋接<b>沒有</b>開放扣除權（{@link ExternalReagent#spendable()}
     * 為 {@code false}），會回傳 {@link ReagentSpendResult.Outcome#UNAVAILABLE}，
     * 呼叫端應改用原模組的扣除路徑，以免兩邊各扣一次。</p>
     */
    public ReagentSpendResult spend(Player player, String typeId, double amount) {
        if (typeId == null) {
            return ReagentSpendResult.unavailable("");
        }
        ExternalReagent bridge = external.get(typeId);
        if (bridge != null) {
            if (!bridge.spendable()) {
                return ReagentSpendResult.unavailable(typeId);
            }
            // 先讀一次餘額,失敗時才講得出「還差多少」而不是只說扣不動
            double available = bridge.value(player);
            return bridge.spend(player, amount)
                    ? ReagentSpendResult.success(typeId, amount, bridge.value(player))
                    : ReagentSpendResult.insufficient(typeId, amount, available);
        }
        ReagentPool pool = pool(player);
        if (pool == null || !pool.has(typeId)) {
            return ReagentSpendResult.unavailable(typeId);
        }
        double available = pool.value(typeId);
        if (pool.spend(typeId, amount)) {
            return ReagentSpendResult.success(typeId, amount, pool.value(typeId));
        }
        return ReagentSpendResult.insufficient(typeId, amount, available);
    }

    /**
     * 回復資源。
     *
     * <p>外部託管的資源導回其橋接 —— 否則扣款走橋接、回捲走資源池，
     * 一次失敗的多資源技能就會把法力退到一個沒有人讀的地方。</p>
     *
     * @return 實際回復的量
     */
    public double restore(Player player, String typeId, double amount) {
        ExternalReagent bridge = typeId == null ? null : external.get(typeId);
        if (bridge != null) {
            return bridge.spendable() ? bridge.restore(player, amount) : 0;
        }
        ReagentPool pool = pool(player);
        return pool == null ? 0 : pool.restore(typeId, amount);
    }

    /** 直接設值，供指令與劇情腳本使用。 */
    public void set(Player player, String typeId, double value) {
        ReagentPool pool = pool(player);
        if (pool != null) {
            pool.set(typeId, value);
        }
    }

    /**
     * 組出這名玩家所有資源的統一格式字串，供選單與指令輸出。
     *
     * @return 資源代號對應顯示字串，已依 {@code order} 排序
     */
    public Map<String, String> describe(Player player, String classId) {
        Map<String, String> lines = new LinkedHashMap<>();
        for (ReagentType type : typesFor(classId)) {
            lines.put(type.id(), type.format(value(player, type.id()), max(player, type.id())));
        }
        return lines;
    }

    // ------------------------------------------------------------------
    // 週期結算
    // ------------------------------------------------------------------

    private void startTicking(Player player, UUID characterId) {
        UUID playerId = player.getUniqueId();
        ScheduledTask existing = tasks.remove(playerId);
        if (existing != null) {
            scheduler.cancel(existing);
        }
        double seconds = periodTicks / 20.0;
        ScheduledTask task = scheduler.runEntityAtFixedRate(
                player,
                ignored -> tick(player, characterId, seconds),
                () -> tasks.remove(playerId),
                periodTicks,
                periodTicks);
        if (task != null) {
            tasks.put(playerId, task);
        }
    }

    /**
     * 單次結算：依各資源的行為模式回復或衰退。
     *
     * <p>本方法跑在玩家自己的區域執行緒上，可安全存取該玩家的狀態。</p>
     */
    private void tick(Player player, UUID characterId, double seconds) {
        ReagentPool pool = pools.get(characterId);
        if (pool == null || !player.isOnline()) {
            return;
        }
        boolean fighting = inCombat.test(player);

        for (ReagentType type : types.values()) {
            if (!pool.has(type.id()) || !type.behavior().needsTicking()) {
                continue;
            }
            switch (type.behavior()) {
                case REGENERATE -> {
                    double rate = type.regenPerSecond()
                            * (fighting ? type.combatRegenMultiplier() : 1.0);
                    if (rate > 0) {
                        pool.restore(type.id(), rate * seconds);
                    }
                }
                case DECAY -> {
                    // 只有脫離戰鬥才會消退，戰鬥中累積的怒氣不該邊打邊掉
                    if (!fighting && type.decayPerSecond() > 0) {
                        double floor = type.startValueAt(levelResolver.applyAsInt(player));
                        double current = pool.value(type.id());
                        if (current > floor) {
                            pool.set(type.id(),
                                    Math.max(floor, current - type.decayPerSecond() * seconds));
                        }
                    }
                }
                case STATIC -> {
                    // 靜止資源不做任何事
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 注入介面
    // ------------------------------------------------------------------

    /** 取得玩家目前角色識別碼。 */
    @FunctionalInterface
    public interface CharacterIdResolver {
        UUID resolve(Player player);
    }

    /**
     * 外部託管資源的橋接。
     *
     * <p>法力這類已有專責模組保管的資源，透過本介面併入統一查詢，
     * 資料仍只有一份，存在原模組裡。</p>
     *
     * <p>預設只開放讀取。橋接可以另外覆寫 {@link #spendable()} 開放扣除權，
     * 讓 {@code skills.yml} 寫得出 {@code costs: {mana: 24}} ——
     * 否則設定檔能寫、程式卻扣不動，企劃得記住「哪些資源是特別的」，
     * 而那正是這套抽象要消滅的東西。扣除仍然轉呼叫原模組，
     * 不會在本服務裡另存一份餘額。</p>
     */
    public interface ExternalReagent {
        double value(Player player);

        double max(Player player);

        /** 本橋接是否也負責扣除與回補；預設 {@code false}，寫入權留在原模組。 */
        default boolean spendable() {
            return false;
        }

        /** @return 扣除成功為 {@code true}；餘額不足為 {@code false} */
        default boolean spend(Player player, double amount) {
            return false;
        }

        /** @return 實際回補的量 */
        default double restore(Player player, double amount) {
            return 0;
        }
    }
}
