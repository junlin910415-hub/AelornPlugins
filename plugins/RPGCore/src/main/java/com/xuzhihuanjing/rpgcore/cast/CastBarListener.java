package com.xuzhihuanjing.rpgcore.cast;

import com.xuzhihuanjing.rpgcore.api.event.RpgAbilityCastEvent;
import com.xuzhihuanjing.rpgcore.domain.ability.AbilityDefinition;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 把有吟唱時間的技能導向讀條流程。
 *
 * <h2>運作方式</h2>
 * <p>沿用與資源消耗相同的策略：不改動 {@code AbilityCastService}，
 * 而是攔截可取消的 {@code RpgAbilityCastEvent}。</p>
 *
 * <ol>
 *   <li>玩家按下技能 → 事件觸發 → 本監聽器發現該技能需要吟唱 → <b>取消事件</b>
 *       （核心會自動退還法力）並啟動讀條</li>
 *   <li>讀條完成 → 標記該次施放為「已吟唱」→ 重新呼叫施放</li>
 *   <li>第二次事件觸發時，本監聽器看到標記 → 清除標記並放行 → 技能正常結算</li>
 * </ol>
 *
 * <p>那個標記是關鍵：少了它，重新呼叫施放會再次被攔截，變成無窮迴圈。</p>
 *
 * <h2>設定寫法</h2>
 * <p>寫在 {@code abilities.yml} 既有技能底下，沒寫的技能維持按下即發：</p>
 * <pre>
 * abilities:
 *   arcanist_meteor:
 *     class: arcanist
 *     mana: 45.0
 *     cast-seconds: 2.5     # ← 新增,需吟唱 2.5 秒
 * </pre>
 */
public final class CastBarListener implements Listener {

    /** 單一技能的吟唱時間上限，避免設定檔手滑寫出永遠唱不完的技能。 */
    private static final double MAX_CAST_SECONDS = 30.0;

    private final CastBarService castBars;
    private final Map<String, Double> castTimes = new HashMap<>();
    /** 已完成吟唱、等待放行的「玩家＋技能」組合。 */
    private final Set<String> channelled = ConcurrentHashMap.newKeySet();
    private final AbilityInvoker invoker;

    /**
     * @param castBars 讀條服務
     * @param invoker 重新觸發施放的方式（吟唱完成後呼叫）
     */
    public CastBarListener(CastBarService castBars, AbilityInvoker invoker) {
        this.castBars = castBars;
        this.invoker = invoker;
    }

    /**
     * 從 {@code abilities.yml} 根區段載入吟唱時間。
     *
     * @return 有吟唱時間的技能數量
     */
    public int load(ConfigurationSection root) {
        castTimes.clear();
        if (root == null) {
            return 0;
        }
        ConfigurationSection abilities = root.getConfigurationSection("abilities");
        if (abilities == null) {
            return 0;
        }
        for (String abilityId : abilities.getKeys(false)) {
            ConfigurationSection ability = abilities.getConfigurationSection(abilityId);
            if (ability == null) {
                continue;
            }
            double seconds = ability.getDouble("cast-seconds", ability.getDouble("cast_seconds", 0));
            if (Double.isFinite(seconds) && seconds > 0) {
                castTimes.put(normalize(abilityId), Math.min(MAX_CAST_SECONDS, seconds));
            }
        }
        return castTimes.size();
    }

    public boolean isEmpty() {
        return castTimes.isEmpty();
    }

    /**
     * 攔截需要吟唱的技能。
     *
     * <p>用 {@code HIGH} 優先度：晚於資源扣除（{@code NORMAL}），
     * 這樣資源不足的技能會先被擋下，不會白白唱完才發現放不出來。</p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCast(RpgAbilityCastEvent event) {
        if (castTimes.isEmpty()) {
            return;
        }
        AbilityDefinition ability = event.ability();
        Double seconds = castTimes.get(normalize(ability.id()));
        if (seconds == null) {
            return;
        }

        Player player = event.player();
        String key = key(player.getUniqueId(), ability.id());

        // 這是吟唱完成後的第二次呼叫 → 放行
        if (channelled.remove(key)) {
            return;
        }

        // 第一次呼叫 → 取消並開始吟唱（核心會自動退還法力）
        event.setCancelled(true);

        CharacterProfile character = event.character();
        String displayName = plain(ability.displayName());
        boolean started = castBars.beginCast(player, ability.id(), displayName, seconds,
                () -> {
                    channelled.add(key);
                    if (!invoker.invoke(player, character, ability)) {
                        // 施放最終仍失敗（冷卻、資源等），別讓標記留著污染下一次
                        channelled.remove(key);
                    }
                },
                () -> channelled.remove(key));

        if (!started) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("正在吟唱中"));
        }
    }

    /** 受擊即中斷吟唱。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && castBars.isCasting(victim)) {
            castBars.interrupt(victim, "受到攻擊");
        }
    }

    /** 離線清除，避免標記與排程殘留。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        castBars.clear(playerId);
        channelled.removeIf(entry -> entry.startsWith(playerId + "|"));
    }

    private static String key(UUID playerId, String abilityId) {
        return playerId + "|" + normalize(abilityId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    /** 去除 MiniMessage 標記，讀條上顯示乾淨的技能名。 */
    private static String plain(String miniMessage) {
        return miniMessage == null ? "" : miniMessage.replaceAll("<[^>]+>", "");
    }

    /** 重新觸發技能施放的方式。 */
    @FunctionalInterface
    public interface AbilityInvoker {
        boolean invoke(Player player, CharacterProfile character, AbilityDefinition ability);
    }
}
