package com.xuzhihuanjing.rpgcore.skill;

import com.xuzhihuanjing.rpgcore.platform.RpgScheduler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 技能腳本引擎 —— 載入、查詢與執行。
 *
 * <p>取代原本「一個 enum 值配一支手寫 Java 方法」的做法。
 * 先前每新增一個技能都要改 {@code AbilityEffectType}、
 * 在 {@code AbilityExecutor} 的 switch 補一個 case、再寫一支方法、
 * 然後重新編譯部署——想鋪十組武器技能等於改上百行 Java。
 * 現在新技能只要在 {@code skills.yml} 加一段設定。</p>
 *
 * <h2>Folia 安全</h2>
 * <p>步驟依延遲排程在<b>施法者自己的區域執行緒</b>上。
 * 目標搜尋只用 {@code getNearbyEntities}（走區塊索引），
 * 不會跨區域碰其他實體，也不會掃全世界。</p>
 *
 * <h2>延遲步驟的中止條件</h2>
 * <p>施法者離線或死亡時，後續步驟一律停止。
 * 少了這個檢查，玩家登出後那顆隕石照樣會砸下來。</p>
 */
public final class SkillEngine {

    private final RpgScheduler scheduler;
    private final Map<String, SkillScript> scripts = new ConcurrentHashMap<>();
    private SkillContext.Hooks hooks;

    public SkillEngine(RpgScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** 接上對外部服務的轉接；插件啟動時呼叫一次。 */
    public void bind(SkillContext.Hooks hooks) {
        this.hooks = hooks;
    }

    // ------------------------------------------------------------------
    // 載入
    // ------------------------------------------------------------------

    /**
     * 從設定檔載入所有技能腳本。
     *
     * @param section {@code skills.yml} 根區段
     * @param problems 解析過程中的問題會附加到這裡，由呼叫端決定怎麼回報
     * @return 成功載入的技能數量
     */
    public int load(ConfigurationSection section, List<String> problems) {
        scripts.clear();
        if (section == null) {
            return 0;
        }
        ConfigurationSection list = section.getConfigurationSection("skills");
        ConfigurationSection source = list == null ? section : list;

        for (String key : source.getKeys(false)) {
            ConfigurationSection entry = source.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            SkillScript script = SkillScript.from(key, entry, problems);
            if (script != null && !script.id().isBlank()) {
                scripts.put(script.id(), script);
            }
        }
        return scripts.size();
    }

    /** 取得技能腳本；不存在時回傳 {@code null}。 */
    public SkillScript script(String skillId) {
        return skillId == null ? null
                : scripts.get(skillId.trim().toLowerCase(Locale.ROOT).replace('-', '_'));
    }

    /** 全部已載入的技能。 */
    public List<SkillScript> scripts() {
        return List.copyOf(scripts.values());
    }

    /** 依層級分組，供選單與文件輸出使用。 */
    public Map<SkillScript.Tier, List<SkillScript>> byTier() {
        Map<SkillScript.Tier, List<SkillScript>> grouped = new LinkedHashMap<>();
        for (SkillScript.Tier tier : SkillScript.Tier.values()) {
            grouped.put(tier, new ArrayList<>());
        }
        scripts.values().forEach(script -> grouped.get(script.tier()).add(script));
        return grouped;
    }

    // ------------------------------------------------------------------
    // 執行
    // ------------------------------------------------------------------

    /**
     * 執行一個技能。
     *
     * @param caster 施法者
     * @param skillId 技能代號
     * @param powerScale 威力係數，通常是施法者的攻擊力
     * @return 技能存在並開始執行為 {@code true}
     */
    public boolean cast(Player caster, String skillId, double powerScale) {
        SkillScript script = script(skillId);
        if (script == null || caster == null) {
            return false;
        }
        return cast(caster, script, powerScale);
    }

    /** 執行一個已解析的技能腳本。 */
    public boolean cast(Player caster, SkillScript script, double powerScale) {
        if (caster == null || script == null || script.steps().isEmpty()) {
            return false;
        }
        SkillContext context = new SkillContext(caster, powerScale, hooks);

        for (SkillScript.Step step : script.steps()) {
            if (step.delayTicks() <= 0) {
                runStep(caster, context, step);
                continue;
            }
            scheduler.runEntityLater(
                    caster,
                    () -> {
                        // 延遲期間玩家可能已登出或死亡,後續步驟必須停手
                        if (caster.isOnline() && !caster.isDead()) {
                            runStep(caster, context, step);
                        }
                    },
                    () -> { },
                    step.delayTicks());
        }
        return true;
    }

    /** 執行單一步驟：先選目標再套用機制。 */
    private void runStep(Player caster, SkillContext context, SkillScript.Step step) {
        List<LivingEntity> targets = step.targeter().resolve(caster, step.targetParams());
        step.mechanic().apply(context, targets, step.mechanicParams());
    }
}
