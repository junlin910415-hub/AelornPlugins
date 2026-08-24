package tw.linsy.aelorn.rpgcore.aura;

import java.util.UUID;

/**
 * 一個正在生效的增益／減益。
 *
 * <p>疊層與到期時間會被多個區域執行緒同時碰觸（施法者在 A 區、目標在 B 區），
 * 因此所有異動都走 {@code synchronized}。這裡的競爭頻率極低，
 * 用鎖換取正確性遠比自己拼裝原子操作划算。</p>
 */
public final class AuraInstance {

    private final AuraDefinition definition;
    private final UUID sourceId;
    private int stacks;
    private long expiresAtMillis;
    private long nextPeriodicAtMillis;

    AuraInstance(AuraDefinition definition, UUID sourceId, int stacks, long nowMillis) {
        this.definition = definition;
        this.sourceId = sourceId;
        this.stacks = Math.max(1, Math.min(definition.maxStacks(), stacks));
        this.expiresAtMillis = computeExpiry(definition, nowMillis);
        this.nextPeriodicAtMillis = definition.hasPeriodic()
                ? nowMillis + (long) (definition.periodic().intervalSeconds() * 1000)
                : Long.MAX_VALUE;
    }

    private static long computeExpiry(AuraDefinition definition, long nowMillis) {
        return definition.permanent()
                ? Long.MAX_VALUE
                : nowMillis + (long) (definition.durationSeconds() * 1000);
    }

    public AuraDefinition definition() {
        return definition;
    }

    /** 施加者；可能為 {@code null}（環境效果、地形傷害）。 */
    public UUID sourceId() {
        return sourceId;
    }

    public synchronized int stacks() {
        return stacks;
    }

    /** 剩餘秒數；永久效果回傳 {@link Double#POSITIVE_INFINITY}。 */
    public synchronized double remainingSeconds(long nowMillis) {
        if (expiresAtMillis == Long.MAX_VALUE) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0, (expiresAtMillis - nowMillis) / 1000.0);
    }

    public synchronized boolean expired(long nowMillis) {
        return expiresAtMillis != Long.MAX_VALUE && nowMillis >= expiresAtMillis;
    }

    /** 到期時刻；永久效果為 {@link Long#MAX_VALUE}。供屬性彙總快取推算有效期限。 */
    synchronized long expiresAtMillis() {
        return expiresAtMillis;
    }

    /**
     * 重複施加時依疊層規則更新自身。
     *
     * @return 實際發生變化時為 {@code true}
     */
    synchronized boolean reapply(int addedStacks, long nowMillis) {
        return switch (definition.stackRule()) {
            case IGNORE -> false;
            case REFRESH -> {
                expiresAtMillis = computeExpiry(definition, nowMillis);
                yield true;
            }
            case STACK -> {
                int before = stacks;
                stacks = Math.min(definition.maxStacks(), stacks + Math.max(1, addedStacks));
                expiresAtMillis = computeExpiry(definition, nowMillis);
                yield stacks != before || !definition.permanent();
            }
        };
    }

    /** 手動移除若干層；層數歸零代表整個效果應該消失。 */
    synchronized boolean consumeStacks(int amount) {
        stacks -= Math.max(1, amount);
        return stacks <= 0;
    }

    /**
     * 判斷週期效果是否到點，到點時同時推進下一次觸發時間。
     *
     * @return 應該觸發時為 {@code true}
     */
    synchronized boolean pollPeriodic(long nowMillis) {
        if (!definition.hasPeriodic() || nowMillis < nextPeriodicAtMillis) {
            return false;
        }
        long interval = (long) (definition.periodic().intervalSeconds() * 1000);
        // 伺服器卡頓後可能一次落後多個週期，直接對齊到現在，避免補償式連續觸發
        nextPeriodicAtMillis = nowMillis + Math.max(50L, interval);
        return true;
    }

    /** 顯示標籤，格式與 {@link AuraDefinition#format} 一致。 */
    public String display(long nowMillis) {
        return definition.format(stacks(), remainingSeconds(nowMillis));
    }
}
