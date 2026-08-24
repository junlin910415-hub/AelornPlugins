package tw.linsy.aelorn.rpgcore.domain.quest;

/**
 * 任務目標的種類。
 *
 * <p>每一種都對應一個明確的觸發來源。新增種類時必須同時補上觸發點與
 * {@code QuestRegistry.validateTargets} 的驗證規則,否則設定寫錯只會在
 * 執行期靜默不計數,很難察覺。
 */
public enum QuestObjectiveType {
    /** 擊殺指定 MythicMobs 怪物,target 為怪物 id。 */
    KILL_MONSTER,
    /** 完成指定遭遇戰,target 為遭遇 id。 */
    COMPLETE_ENCOUNTER,
    /** 抵達指定發現點,target 為發現 id。 */
    DISCOVER_LOCATION,
    /** 拾取指定 MMOItems 物品,target 為 {@code 類型:id}。 */
    COLLECT_ITEM,
    /** 與指定 NPC 對話,target 為 MythicMobs NPC id。 */
    TALK_TO_NPC,

    /** 使用(右鍵)指定物品,target 為 {@code 類型:id}。 */
    USE_ITEM,
    /** 向 NPC 交付並**消耗**指定物品,target 為 {@code NPC id/類型:物品 id}。 */
    DELIVER_ITEM,
    /** 施放指定技能,target 為技能 id。 */
    CAST_ABILITY,
    /** 角色等級達到門檻,target 為等級數字;接受任務與升級時各檢查一次。 */
    REACH_LEVEL,
    /** 護送 Citizens NPC 抵達其設定的終點,target 為 NPC 名稱。 */
    ESCORT_NPC
}
