package com.xuzhihuanjing.rpgcore.reagent;

import java.util.Locale;

/**
 * 資源的行為模式 —— 決定一種資源平時會自己怎麼變動。
 *
 * <p>不同職業的資源手感天差地遠：法師的法力平時該慢慢回滿，
 * 戰士的怒氣則要在戰鬥中越打越多、脫戰後迅速消退，
 * 而連擊點這類資源根本不該自己動。把這三種節奏抽成模式，
 * 新增一種資源只要改設定檔，不必再寫一次排程邏輯。</p>
 */
public enum ReagentBehavior {

    /**
     * 自動回復 —— 平時穩定回升至上限，戰鬥中可套用較低的倍率。
     *
     * <p>適用：法力、精力。</p>
     */
    REGENERATE,

    /**
     * 戰鬥累積 —— 脫離戰鬥後隨時間衰退回起始值。
     *
     * <p>適用：怒氣、戰意。讓玩家有「趁熱打鐵」的壓力，
     * 而不是掛機攢滿再開怪。</p>
     */
    DECAY,

    /**
     * 靜止 —— 完全不自動變動，只受技能、道具與事件影響。
     *
     * <p>適用：連擊點、專注層數。</p>
     */
    STATIC;

    /**
     * 由設定檔字串解析。
     *
     * @return 無法辨識時退回 {@link #REGENERATE}
     */
    public static ReagentBehavior of(String name) {
        if (name == null || name.isBlank()) {
            return REGENERATE;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return REGENERATE;
        }
    }

    /** 該模式是否需要週期性排程。 */
    public boolean needsTicking() {
        return this != STATIC;
    }

    /** 顯示用的中文名稱。 */
    public String displayName() {
        return switch (this) {
            case REGENERATE -> "自動回復";
            case DECAY -> "戰鬥累積";
            case STATIC -> "靜止";
        };
    }
}
