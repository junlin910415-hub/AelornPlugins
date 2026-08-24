package tw.linsy.aelorn.rpgcore.reagent;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 單一角色的資源池 —— 存放這名角色所有資源的當前值與上限。
 *
 * <p>Folia 之下同一名玩家的事件可能來自不同區域執行緒，
 * 因此扣除與回復一律走 {@link ConcurrentHashMap#compute} 的原子路徑。
 * 若用「先讀再寫」的兩段式寫法，兩個技能同時結算時會出現
 * 資源扣一次卻放兩個技能的漏洞。</p>
 */
public final class ReagentPool {

    private final UUID characterId;
    private final Map<String, Double> values = new ConcurrentHashMap<>();
    private final Map<String, Double> maxima = new ConcurrentHashMap<>();

    public ReagentPool(UUID characterId) {
        this.characterId = characterId;
    }

    public UUID characterId() {
        return characterId;
    }

    /** 目前擁有的資源代號。 */
    public Map<String, Double> snapshot() {
        return Collections.unmodifiableMap(values);
    }

    /** 某資源的當前值；未註冊時回傳 0。 */
    public double value(String typeId) {
        return values.getOrDefault(typeId, 0.0);
    }

    /** 某資源的上限；未註冊時回傳 0。 */
    public double max(String typeId) {
        return maxima.getOrDefault(typeId, 0.0);
    }

    /** 某資源的百分比（0~1），上限為 0 時回傳 0。 */
    public double ratio(String typeId) {
        double max = max(typeId);
        return max <= 0 ? 0 : Math.max(0, Math.min(1, value(typeId) / max));
    }

    /** 是否已註冊該資源。 */
    public boolean has(String typeId) {
        return maxima.containsKey(typeId);
    }

    /**
     * 註冊或更新一種資源的上限。
     *
     * @param typeId 資源代號
     * @param newMax 新的上限
     * @param initialValue 首次註冊時採用的初始值
     */
    public void register(String typeId, double newMax, double initialValue) {
        double safeMax = Math.max(0, newMax);
        maxima.put(typeId, safeMax);
        // 已存在就只夾住上限，不要把玩家目前的資源洗掉
        values.compute(typeId, (key, current) ->
                current == null
                        ? Math.max(0, Math.min(safeMax, initialValue))
                        : Math.min(current, safeMax));
    }

    /** 移除一種資源（例如轉職後不再使用怒氣）。 */
    public void remove(String typeId) {
        values.remove(typeId);
        maxima.remove(typeId);
    }

    /**
     * 嘗試扣除資源。
     *
     * @return 餘額足夠並成功扣除時為 {@code true}；不足則原封不動回傳 {@code false}
     */
    public boolean spend(String typeId, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (!maxima.containsKey(typeId)) {
            return false;
        }
        AtomicBoolean success = new AtomicBoolean(false);
        values.compute(typeId, (key, current) -> {
            double available = current == null ? 0 : current;
            if (available + 1.0E-9 < amount) {
                return current;
            }
            success.set(true);
            return available - amount;
        });
        return success.get();
    }

    /**
     * 回復資源，自動夾在上限之內。
     *
     * @return 實際回復的量
     */
    public double restore(String typeId, double amount) {
        if (amount <= 0 || !maxima.containsKey(typeId)) {
            return 0;
        }
        double limit = max(typeId);
        double[] gained = new double[1];
        values.compute(typeId, (key, current) -> {
            double available = current == null ? 0 : current;
            double updated = Math.min(limit, available + amount);
            gained[0] = updated - available;
            return updated;
        });
        return gained[0];
    }

    /** 直接設定資源值，自動夾在 0 與上限之間。 */
    public void set(String typeId, double value) {
        if (!maxima.containsKey(typeId)) {
            return;
        }
        double limit = max(typeId);
        values.put(typeId, Math.max(0, Math.min(limit, value)));
    }

    /** 清空所有資源（角色刪除或登出時使用）。 */
    public void clear() {
        values.clear();
        maxima.clear();
    }
}
