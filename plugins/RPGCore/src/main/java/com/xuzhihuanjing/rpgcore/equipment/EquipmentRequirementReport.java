package com.xuzhihuanjing.rpgcore.equipment;

import java.util.List;
import java.util.Optional;

/**
 * 裝備需求的**逐項**評估結果。
 *
 * <p>{@link EquipmentRequirementResult} 只回答「能不能穿」並附上第一個失敗原因,
 * 那對阻擋穿戴足夠,但不足以畫出 Wynncraft 式的提示框——後者要同時顯示
 * 每一條需求以及各自的 ✔ / ✖。這個型別保留全部項目,不做短路。
 *
 * <p>{@link EquipmentService#requirements} 改為建立在本型別之上,因此
 * 「阻擋穿戴」與「畫提示框」共用同一份判定邏輯,不會各自漂移。
 */
public record EquipmentRequirementReport(List<Entry> entries) {

    public EquipmentRequirementReport {
        entries = List.copyOf(entries);
    }

    /** 需求類別。提示框依此決定顯示順序與圖示。 */
    public enum Kind {
        CLASS,
        LEVEL,
        SKILL,
        QUEST
    }

    /**
     * 單一需求項。
     *
     * @param label       顯示名稱,如「戰鬥等級」「力量」「前置任務」
     * @param requirement 需求值的顯示字串,如「25」「盜賊」「見習廚師」
     * @param met         角色是否已滿足
     */
    public record Entry(Kind kind, String label, String requirement, boolean met) {
    }

    private static final EquipmentRequirementReport EMPTY =
            new EquipmentRequirementReport(List.of());

    /** 沒有任何需求(非裝備、或模板未設限制)。 */
    public static EquipmentRequirementReport empty() {
        return EMPTY;
    }

    /** 全部項目皆滿足才可穿戴。 */
    public boolean usable() {
        return entries.stream().allMatch(Entry::met);
    }

    /** 第一個未滿足項,用來組出阻擋穿戴時的提示訊息。 */
    public Optional<Entry> firstUnmet() {
        return entries.stream().filter(entry -> !entry.met()).findFirst();
    }
}
