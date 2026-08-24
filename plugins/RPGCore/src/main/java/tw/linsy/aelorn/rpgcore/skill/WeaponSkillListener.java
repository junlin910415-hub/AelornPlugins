package tw.linsy.aelorn.rpgcore.skill;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

/**
 * 武器技能的觸發入口 —— 右鍵施放。
 *
 * <p>操作只有兩種：右鍵是主技能，潛行 + 右鍵是次技能。
 * 這兩個動作玩家不用學就會，拿起武器立刻能打。</p>
 *
 * <h2>冷卻</h2>
 * <p>冷卻記在這裡而非技能腳本上，因為冷卻是「玩家對這個技能」的狀態，
 * 而腳本是共用的定義。兩者混在一起的話，兩個玩家會共用同一份冷卻。</p>
 *
 * <h2>吟唱</h2>
 * <p>技能若有 {@code cast-seconds}，會先走讀條再執行；
 * 讀條期間移動或受擊都會中斷，冷卻也不會進入。
 * 這讓「大招被打斷」變成真正的攻防，而不是白白吃掉冷卻。</p>
 *
 * <h2>資源</h2>
 * <p>{@code skills.yml} 的 {@code costs} 在這裡結算。付款時機是「真的要放了」
 * 的那一刻：讀條前只確認付不付得起，被打斷的玩家不會白付；
 * 而冷卻在扣款成功之後才進入，資源不夠不會順便吃掉冷卻。</p>
 */
public final class WeaponSkillListener implements Listener {

    private final SkillEngine engine;
    private final WeaponSkillBinding bindings;
    private final Function<ItemStack, String[]> itemResolver;
    private final ToDoubleFunction<Player> powerResolver;
    private final CastGate castGate;
    private final SkillCostGate costGate;
    private final BiConsumer<Player, String> notifier;

    /** 玩家 → 技能代號 → 冷卻結束時刻。 */
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    /**
     * @param engine 技能引擎
     * @param bindings 武器綁定表
     * @param itemResolver 由物品解析出 {@code [類型, 代號]}；無法辨識時回傳 {@code null}
     * @param powerResolver 取得玩家的威力係數（通常是攻擊力）
     * @param castGate 吟唱轉接；為 {@code null} 代表全部瞬發
     * @param costGate 資源結算轉接；為 {@code null} 代表技能不收費
     * @param notifier 提示玩家的方式
     */
    public WeaponSkillListener(SkillEngine engine,
                               WeaponSkillBinding bindings,
                               Function<ItemStack, String[]> itemResolver,
                               ToDoubleFunction<Player> powerResolver,
                               CastGate castGate,
                               SkillCostGate costGate,
                               BiConsumer<Player, String> notifier) {
        this.engine = engine;
        this.bindings = bindings;
        this.itemResolver = itemResolver;
        this.powerResolver = powerResolver == null ? player -> 1.0 : powerResolver;
        this.castGate = castGate;
        this.costGate = costGate;
        this.notifier = notifier == null ? (player, message) -> { } : notifier;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // 只認主手右鍵：副手會讓同一次點擊觸發兩遍
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        if (held == null || held.getType().isAir()) {
            return;
        }

        String[] identity = itemResolver == null ? null : itemResolver.apply(held);
        if (identity == null) {
            return;
        }
        WeaponSkillBinding.Slots slots = bindings.resolve(identity[1], identity[0]);
        if (slots == null) {
            return;
        }
        String skillId = slots.forInput(player.isSneaking());
        if (skillId == null) {
            return;
        }
        SkillScript script = engine.script(skillId);
        if (script == null) {
            return;
        }

        event.setCancelled(true);
        attemptCast(player, script);
    }

    /** 嘗試施放：先檢查冷卻，再決定要不要吟唱。 */
    private void attemptCast(Player player, SkillScript script) {
        long now = System.currentTimeMillis();
        Map<String, Long> personal = cooldowns.computeIfAbsent(
                player.getUniqueId(), ignored -> new ConcurrentHashMap<>());

        Long readyAt = personal.get(script.id());
        if (readyAt != null && now < readyAt) {
            double remaining = (readyAt - now) / 1000.0;
            notifier.accept(player, String.format(java.util.Locale.ROOT,
                    "&7%s &c尚在冷卻 &f%.1f &c秒", stripColors(script.displayName()), remaining));
            return;
        }

        // 讀條前先問「付得起嗎」。真正扣款要等到 fire(),
        // 否則被打斷的玩家等於付了錢卻沒拿到技能。
        if (charges(script)) {
            String problem = costGate.check(player, script.costs());
            if (problem != null) {
                notifier.accept(player, problem);
                return;
            }
        }

        if (script.requiresCast() && castGate != null) {
            boolean started = castGate.beginCast(
                    player, script.id(), stripColors(script.displayName()), script.castSeconds(),
                    () -> fire(player, script, personal));
            if (!started) {
                notifier.accept(player, "&c你正在吟唱其他技能。");
            }
            return;
        }
        fire(player, script, personal);
    }

    /** 真正執行並進入冷卻。扣款與冷卻都只在確定放得出技能時才發生。 */
    private void fire(Player player, SkillScript script, Map<String, Long> personal) {
        // 沒有步驟的技能演不出任何東西,先擋掉才不會有「付了錢什麼也沒發生」的情況。
        // SkillScript.from() 已經濾掉這種腳本,這裡是程式化施放時的最後一道保險。
        if (script.steps().isEmpty()) {
            return;
        }
        if (charges(script)) {
            String problem = costGate.charge(player, script.costs());
            if (problem != null) {
                notifier.accept(player, problem);
                return;
            }
        }
        if (!engine.cast(player, script, powerResolver.applyAsDouble(player))) {
            return;
        }
        if (script.cooldownSeconds() > 0) {
            personal.put(script.id(),
                    System.currentTimeMillis() + (long) (script.cooldownSeconds() * 1000));
        }
        String spent = charges(script) ? " &8" + costGate.describe(script.costs()) : "";
        notifier.accept(player,
                script.tier().label() + " &f" + stripColors(script.displayName()) + spent);
    }

    /** 這個技能要不要收費：設定檔沒寫消耗、或根本沒接資源服務時都不收。 */
    private boolean charges(SkillScript script) {
        return costGate != null && script.hasCosts();
    }

    /** 離線清除冷卻紀錄，避免長期累積死資料。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    /** 清空全部冷卻，插件停用時呼叫。 */
    public void clear() {
        cooldowns.clear();
    }

    private static String stripColors(String text) {
        return text == null ? "" : text.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    /**
     * 吟唱轉接 —— 讓本類別不必直接依賴讀條服務。
     */
    @FunctionalInterface
    public interface CastGate {

        /**
         * @param onComplete 吟唱完成後執行
         * @return 成功開始吟唱為 {@code true}
         */
        boolean beginCast(Player player, String skillId, String displayName,
                          double seconds, Runnable onComplete);
    }
}
