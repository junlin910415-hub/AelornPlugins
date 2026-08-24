package tw.linsy.aelorn.rpgcore.domain.quest;

import java.util.List;
import org.bukkit.Material;

/**
 * 任務完成後發放的獎勵。
 *
 * <p>原本只有一個 {@code rewardExperience} 數字。Wynncraft 的任務獎勵通常是
 * 「經驗 + 物品 + 有時候是專業經驗」的組合,所以拆成獨立的記錄。
 *
 * @param experience   角色經驗
 * @param items        物品獎勵;背包滿的時候會掉在腳邊而不是憑空消失
 * @param professionExperience 專業經驗,key 為專業 id
 */
public record QuestReward(long experience, List<ItemReward> items,
                          java.util.Map<String, Long> professionExperience) {

    public static final QuestReward NONE = new QuestReward(0L, List.of(), java.util.Map.of());

    public QuestReward {
        experience = Math.max(0L, experience);
        items = items == null ? List.of() : List.copyOf(items);
        professionExperience = professionExperience == null
            ? java.util.Map.of() : java.util.Map.copyOf(professionExperience);
    }

    /** 只有經驗的舊格式任務走這條路,維持與 schema 3 相同的行為。 */
    public static QuestReward experienceOnly(long experience) {
        return new QuestReward(experience, List.of(), java.util.Map.of());
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    public boolean hasProfessionExperience() {
        return !professionExperience.isEmpty();
    }

    /**
     * 一筆物品獎勵。
     *
     * @param material 原版材質
     * @param amount   數量
     * @param displayName 覆寫顯示名稱(MiniMessage);null 表示用原本的物品名
     */
    public record ItemReward(Material material, int amount, String displayName) {
        public ItemReward {
            amount = Math.max(1, Math.min(2304, amount));
        }
    }
}
