package com.xuzhihuanjing.rpgcore.aura;

import com.xuzhihuanjing.rpgcore.platform.RpgScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

/**
 * 增益／減益系統 —— 管理所有角色與怪物身上正在生效的狀態。
 *
 * <p>在此之前插件完全沒有這一層，技能想做「灼燒三秒」只能各自寫排程，
 * 效果無法疊層、無法顯示、也無法被驅散。本服務把狀態集中管理，
 * 技能只要說「給目標掛上 deep_poison 兩層」即可。</p>
 *
 * <h2>設計要點</h2>
 * <ul>
 *   <li><b>屬性彙總分兩段</b>：先加總所有 {@code flat}，再套用 {@code percent} 總和。
 *       若改成逐個相乘，三個 +20% 會變成 1.728 倍而不是 1.6 倍，
 *       疊到五六層就會失控。</li>
 *   <li><b>到期掃描不碰實體</b>：純時間比對，可在任意執行緒安全執行；
 *       只有週期傷害需要接觸實體，會轉交該實體自己的區域執行緒。</li>
 *   <li><b>弱引用持有實體</b>：怪物被清除後不會因為身上還有中毒狀態而洩漏記憶體。</li>
 * </ul>
 */
public final class AuraService {

    /** 掃描間隔，預設每 10 tick（半秒）一次。 */
    private static final long DEFAULT_SWEEP_TICKS = 10L;

    private final RpgScheduler scheduler;
    private final Map<String, AuraDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, AuraInstance>> active = new ConcurrentHashMap<>();
    private final Map<UUID, WeakReference<LivingEntity>> entities = new ConcurrentHashMap<>();
    /**
     * 屬性彙總快取 —— 這是效能上最要緊的一塊。
     *
     * <p>{@link #aggregate} 掛在 {@code StatService.calculate()} 的出口，
     * 而後者<b>每次傷害結算、每次 HUD 更新都會被呼叫</b>。
     * 沒有快取的話，一名身上有五個狀態的玩家每秒會產生數十份
     * 短命的 Map 與 record，戰鬥人數一多就是持續的 GC 壓力。</p>
     *
     * <p>快取在任何異動（施加、疊層、移除、驅散、到期）時失效，
     * 並額外記錄「最早到期時刻」——時間一到即使沒人動過也要重算，
     * 否則增益會多撐到下一次掃描（最多半秒）才消失。</p>
     */
    private final Map<UUID, AggregateCache> aggregateCache = new ConcurrentHashMap<>();

    private ScheduledTask sweepTask;
    private long sweepTicks = DEFAULT_SWEEP_TICKS;

    public AuraService(RpgScheduler scheduler) {
        this.scheduler = scheduler;
    }

    // ------------------------------------------------------------------
    // 設定載入
    // ------------------------------------------------------------------

    /**
     * 載入所有增益定義，重載時整批換新。
     *
     * @return 載入的定義數量
     */
    public int load(ConfigurationSection section) {
        definitions.clear();
        if (section == null) {
            return 0;
        }
        this.sweepTicks = Math.max(1L, section.getLong("sweep-period-ticks", DEFAULT_SWEEP_TICKS));

        ConfigurationSection list = section.getConfigurationSection("types");
        ConfigurationSection source = list == null ? section : list;
        for (String key : source.getKeys(false)) {
            ConfigurationSection entry = source.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            AuraDefinition definition = AuraDefinition.from(key, entry);
            if (!definition.id().isBlank()) {
                definitions.put(definition.id(), definition);
            }
        }
        return definitions.size();
    }

    /** 以程式方式登錄定義，供測試與內建預設值使用。 */
    public void register(AuraDefinition definition) {
        if (definition != null && !definition.id().isBlank()) {
            definitions.put(definition.id(), definition);
        }
    }

    /** 取得定義；不存在時回傳 {@code null}。 */
    public AuraDefinition definition(String auraId) {
        return auraId == null ? null : definitions.get(normalize(auraId));
    }

    /** 全部已載入的定義。 */
    public List<AuraDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    // ------------------------------------------------------------------
    // 施加與移除
    // ------------------------------------------------------------------

    /**
     * 對目標施加增益／減益。
     *
     * @param target 目標
     * @param auraId 定義代號
     * @param stacks 施加層數，至少 1
     * @param source 施加者；可為 {@code null}
     * @return 成功施加或更新時為 {@code true}；定義不存在或被疊層規則忽略時為 {@code false}
     */
    public boolean apply(LivingEntity target, String auraId, int stacks, UUID source) {
        AuraDefinition definition = definition(auraId);
        if (target == null || definition == null) {
            return false;
        }
        UUID targetId = target.getUniqueId();
        entities.put(targetId, new WeakReference<>(target));

        long now = System.currentTimeMillis();
        Map<String, AuraInstance> onTarget =
                active.computeIfAbsent(targetId, ignored -> new ConcurrentHashMap<>());

        AuraInstance existing = onTarget.get(definition.id());
        if (existing != null) {
            boolean changed = existing.reapply(stacks, now);
            if (changed) {
                invalidate(targetId);
            }
            return changed;
        }
        onTarget.put(definition.id(), new AuraInstance(definition, source, stacks, now));
        invalidate(targetId);
        return true;
    }

    /** 施加單層的簡便寫法。 */
    public boolean apply(LivingEntity target, String auraId, UUID source) {
        return apply(target, auraId, 1, source);
    }

    /** 移除指定效果。 */
    public boolean remove(LivingEntity target, String auraId) {
        if (target == null) {
            return false;
        }
        Map<String, AuraInstance> onTarget = active.get(target.getUniqueId());
        boolean removed = onTarget != null && onTarget.remove(normalize(auraId)) != null;
        if (removed) {
            invalidate(target.getUniqueId());
        }
        return removed;
    }

    /**
     * 消耗指定層數；層數歸零時整個效果消失。
     *
     * @return 效果因此被完全移除時為 {@code true}
     */
    public boolean consume(LivingEntity target, String auraId, int amount) {
        if (target == null) {
            return false;
        }
        Map<String, AuraInstance> onTarget = active.get(target.getUniqueId());
        if (onTarget == null) {
            return false;
        }
        String key = normalize(auraId);
        AuraInstance instance = onTarget.get(key);
        if (instance == null) {
            return false;
        }
        // 先改狀態再讓快取失效。順序反過來的話,
        // 兩者之間若有另一條執行緒讀取,會把舊值重新寫回快取。
        boolean depleted = instance.consumeStacks(amount);
        if (depleted) {
            onTarget.remove(key);
        }
        invalidate(target.getUniqueId());
        return depleted;
    }

    /**
     * 驅散效果。
     *
     * @param beneficial {@code true} 只驅散增益，{@code false} 只驅散減益
     * @return 被移除的數量
     */
    public int dispel(LivingEntity target, boolean beneficial) {
        if (target == null) {
            return 0;
        }
        Map<String, AuraInstance> onTarget = active.get(target.getUniqueId());
        if (onTarget == null) {
            return 0;
        }
        List<String> removable = onTarget.values().stream()
                .filter(instance -> instance.definition().beneficial() == beneficial)
                .map(instance -> instance.definition().id())
                .toList();
        removable.forEach(onTarget::remove);
        if (!removable.isEmpty()) {
            invalidate(target.getUniqueId());
        }
        return removable.size();
    }

    /** 清除目標身上所有效果（死亡、登出、重生時呼叫）。 */
    public void clear(LivingEntity target) {
        if (target != null) {
            clear(target.getUniqueId());
        }
    }

    /** 依識別碼清除。 */
    public void clear(UUID targetId) {
        active.remove(targetId);
        entities.remove(targetId);
        invalidate(targetId);
    }

    // ------------------------------------------------------------------
    // 查詢
    // ------------------------------------------------------------------

    /** 目標是否帶有指定效果。 */
    public boolean has(LivingEntity target, String auraId) {
        return stacks(target, auraId) > 0;
    }

    /** 目標身上該效果的層數；沒有時回傳 0。 */
    public int stacks(LivingEntity target, String auraId) {
        if (target == null) {
            return 0;
        }
        Map<String, AuraInstance> onTarget = active.get(target.getUniqueId());
        if (onTarget == null) {
            return 0;
        }
        AuraInstance instance = onTarget.get(normalize(auraId));
        return instance == null ? 0 : instance.stacks();
    }

    /** 目標身上所有生效中的效果。 */
    public List<AuraInstance> auras(LivingEntity target) {
        if (target == null) {
            return List.of();
        }
        Map<String, AuraInstance> onTarget = active.get(target.getUniqueId());
        return onTarget == null ? List.of() : List.copyOf(onTarget.values());
    }

    /**
     * 彙總目標身上所有效果對各屬性的修飾。
     *
     * <p>回傳的每一項都是「先加後乘」的成品：
     * {@code 最終值 = (原始值 + flat總和) × (1 + percent總和/100)}。
     * 呼叫端只要照這條式子套用即可，不必自行處理疊加順序。</p>
     *
     * @return 屬性代號對應彙總後的修飾量
     */
    public Map<String, AuraDefinition.Modifier> aggregate(LivingEntity target) {
        if (target == null) {
            return Map.of();
        }
        UUID targetId = target.getUniqueId();
        Map<String, AuraInstance> onTarget = active.get(targetId);
        if (onTarget == null || onTarget.isEmpty()) {
            return Map.of();
        }

        long now = System.currentTimeMillis();
        AggregateCache cached = aggregateCache.get(targetId);
        if (cached != null && now < cached.validUntilMillis()) {
            return cached.value();
        }

        Map<String, double[]> totals = new LinkedHashMap<>();
        long earliestExpiry = Long.MAX_VALUE;
        for (AuraInstance instance : onTarget.values()) {
            if (instance.expired(now)) {
                continue;
            }
            earliestExpiry = Math.min(earliestExpiry, instance.expiresAtMillis());
            int stacks = instance.stacks();
            instance.definition().modifiers().forEach((stat, modifier) -> {
                double[] sum = totals.computeIfAbsent(stat, ignored -> new double[2]);
                sum[0] += modifier.flat() * stacks;
                sum[1] += modifier.percent() * stacks;
            });
        }

        Map<String, AuraDefinition.Modifier> result;
        if (totals.isEmpty()) {
            result = Map.of();
        } else {
            Map<String, AuraDefinition.Modifier> built = new LinkedHashMap<>(totals.size() * 2);
            totals.forEach((stat, sum) ->
                    built.put(stat, new AuraDefinition.Modifier(sum[0], sum[1])));
            result = Map.copyOf(built);
        }
        aggregateCache.put(targetId, new AggregateCache(result, earliestExpiry));
        return result;
    }

    /** 任何會改變彙總結果的異動都要呼叫，否則屬性會停在舊值。 */
    private void invalidate(UUID targetId) {
        aggregateCache.remove(targetId);
    }

    /**
     * 已彙總的屬性修飾。
     *
     * @param value 不可變的彙總結果
     * @param validUntilMillis 最早到期時刻；到了就得重算，即使沒人動過
     */
    private record AggregateCache(Map<String, AuraDefinition.Modifier> value, long validUntilMillis) {
    }

    /**
     * 對單一數值套用彙總後的修飾。
     *
     * @param target 目標
     * @param stat 屬性代號
     * @param baseValue 原始數值
     * @return 修飾後的數值
     */
    public double modify(LivingEntity target, String stat, double baseValue) {
        AuraDefinition.Modifier modifier = aggregate(target).get(normalize(stat));
        if (modifier == null) {
            return baseValue;
        }
        return (baseValue + modifier.flat()) * (1 + modifier.percent() / 100.0);
    }

    /** 目標身上所有效果的顯示字串，供 HUD 與選單使用。 */
    public List<String> describe(LivingEntity target) {
        long now = System.currentTimeMillis();
        return auras(target).stream()
                .filter(instance -> !instance.expired(now))
                .map(instance -> instance.display(now))
                .toList();
    }

    // ------------------------------------------------------------------
    // 週期掃描
    // ------------------------------------------------------------------

    /** 啟動到期掃描，插件啟用時呼叫一次。 */
    public void start() {
        if (sweepTask != null) {
            return;
        }
        sweepTask = scheduler.runGlobalAtFixedRate(ignored -> sweep(), sweepTicks, sweepTicks);
    }

    /** 停止掃描並清空狀態，插件停用時呼叫。 */
    public void shutdown() {
        if (sweepTask != null) {
            scheduler.cancel(sweepTask);
            sweepTask = null;
        }
        active.clear();
        entities.clear();
        aggregateCache.clear();
    }

    /**
     * 單次掃描：清除到期效果並觸發週期效果。
     *
     * <p>到期判定是純粹的時間比對，不接觸任何實體，因此可在全域執行緒安全執行。
     * 需要碰到實體的週期傷害則轉交該實體自己的區域執行緒。</p>
     */
    private void sweep() {
        long now = System.currentTimeMillis();
        // 這兩個清單多數時候都用不到（沒人到期、沒有週期效果），
        // 因此延後到真的有東西要裝時才配置。掃描每半秒對「所有」帶狀態的目標
        // 各跑一次，戰場上幾十隻中毒的怪就是幾十份短命物件，白白餵給 GC。
        List<UUID> emptyTargets = null;

        for (Map.Entry<UUID, Map<String, AuraInstance>> entry : active.entrySet()) {
            UUID targetId = entry.getKey();
            Map<String, AuraInstance> onTarget = entry.getValue();

            if (onTarget.values().removeIf(instance -> instance.expired(now))) {
                // 有東西到期就得讓彙總重算,否則屬性會停在含過期效果的舊值
                invalidate(targetId);
            }
            if (onTarget.isEmpty()) {
                if (emptyTargets == null) {
                    emptyTargets = new ArrayList<>();
                }
                emptyTargets.add(targetId);
                continue;
            }

            List<AuraInstance> due = null;
            for (AuraInstance instance : onTarget.values()) {
                // 先問 hasPeriodic 再 pollPeriodic：後者會推進計時器，
                // 順序反過來等於對沒有週期效果的狀態也做無謂的同步。
                if (instance.definition().hasPeriodic() && instance.pollPeriodic(now)) {
                    if (due == null) {
                        due = new ArrayList<>(2);
                    }
                    due.add(instance);
                }
            }
            if (due != null) {
                dispatchPeriodic(targetId, due);
            }
        }

        if (emptyTargets != null) {
            for (UUID targetId : emptyTargets) {
                active.remove(targetId);
                entities.remove(targetId);
                invalidate(targetId);
            }
        }
    }

    /** 把週期效果交回目標所在的區域執行緒執行。 */
    private void dispatchPeriodic(UUID targetId, List<AuraInstance> due) {
        WeakReference<LivingEntity> reference = entities.get(targetId);
        LivingEntity target = reference == null ? null : reference.get();
        if (target == null) {
            // 實體已被回收，連同狀態一併清掉
            active.remove(targetId);
            entities.remove(targetId);
            return;
        }
        scheduler.executeEntity(target, () -> applyPeriodic(target, due), () -> clear(targetId));
    }

    /** 實際結算週期傷害與治療；本方法跑在目標自己的區域執行緒上。 */
    private void applyPeriodic(LivingEntity target, List<AuraInstance> due) {
        if (target.isDead() || !target.isValid()) {
            clear(target.getUniqueId());
            return;
        }
        for (AuraInstance instance : due) {
            AuraDefinition.Periodic periodic = instance.definition().periodic();
            int stacks = instance.stacks();

            double damage = periodic.damage() * stacks;
            if (damage > 0) {
                target.damage(damage);
            }
            double healing = periodic.healing() * stacks;
            if (healing > 0) {
                double max = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                        ? target.getHealth()
                        : target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                target.setHealth(Math.min(max, target.getHealth() + healing));
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
