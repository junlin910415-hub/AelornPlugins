package tw.linsy.aelorn.rpgcore.reagent;

/**
 * 資源扣除的結果 —— 讓呼叫端能給出精確且統一的失敗訊息。
 *
 * <p>單純回傳 {@code boolean} 的話，「法力不足」和「這個職業根本沒有怒氣」
 * 會被混為一談，玩家看到的提示自然也就不知所云。</p>
 *
 * @param outcome 結果類型
 * @param typeId 相關的資源代號
 * @param required 需要的量
 * @param available 實際擁有的量
 */
public record ReagentSpendResult(Outcome outcome, String typeId, double required, double available) {

    /** 結果類型。 */
    public enum Outcome {
        /** 扣除成功。 */
        SUCCESS,
        /** 資源不足。 */
        INSUFFICIENT,
        /** 該角色沒有這種資源。 */
        UNAVAILABLE
    }

    public static ReagentSpendResult success(String typeId, double required, double remaining) {
        return new ReagentSpendResult(Outcome.SUCCESS, typeId, required, remaining);
    }

    public static ReagentSpendResult insufficient(String typeId, double required, double available) {
        return new ReagentSpendResult(Outcome.INSUFFICIENT, typeId, required, available);
    }

    public static ReagentSpendResult unavailable(String typeId) {
        return new ReagentSpendResult(Outcome.UNAVAILABLE, typeId, 0, 0);
    }

    public boolean succeeded() {
        return outcome == Outcome.SUCCESS;
    }

    /** 還差多少才夠。 */
    public double shortfall() {
        return Math.max(0, required - available);
    }

    /**
     * 組出給玩家看的說明。
     *
     * @param type 對應的資源定義；可為 {@code null}
     */
    public String describe(ReagentType type) {
        String name = type == null ? typeId : type.displayName();
        return switch (outcome) {
            case SUCCESS -> "已消耗 " + name + " " + (long) Math.ceil(required);
            case INSUFFICIENT -> name + "不足，還差 " + (long) Math.ceil(shortfall());
            case UNAVAILABLE -> "你目前的職業沒有「" + name + "」這種資源";
        };
    }
}
