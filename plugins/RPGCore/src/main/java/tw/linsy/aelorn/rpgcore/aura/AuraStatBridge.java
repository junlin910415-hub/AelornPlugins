package tw.linsy.aelorn.rpgcore.aura;

import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.combat.CombatStats;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * 把增益減益折進戰鬥屬性 —— 讓狀態效果真的生效。
 *
 * <p>{@link AuraService} 只負責「算出修飾量」，本類別負責「套用」。
 * 套用點刻意選在 {@code StatService.calculate()} 的出口：
 * 那是全插件唯一產出 {@link CombatStats} 的地方，
 * 只要在這裡折一次，傷害計算、HUD、屬性同步全都自動吃到，
 * 不必到十幾個呼叫端各加一次（那種做法遲早會漏掉一處）。</p>
 *
 * <h2>為什麼需要反查表</h2>
 * <p>增益是掛在<b>玩家實體</b>上（用 Player UUID 索引，怪物也適用），
 * 但 {@code StatService.calculate()} 只拿得到 {@link CharacterProfile}，
 * 兩者的識別碼不同。掃描全線上玩家去比對太貴（每次算傷害都要掃一遍），
 * 因此在角色啟用時記一筆對照，停用時移除。</p>
 *
 * <p>以弱引用持有玩家，避免玩家離線後仍被這張表卡住無法回收。</p>
 */
public final class AuraStatBridge {

    private final AuraService auras;
    private final Map<UUID, WeakReference<Player>> characterToPlayer = new ConcurrentHashMap<>();
    /** 屬性安全閘,由 auras.yml 的 limits 區段載入。 */
    private volatile AuraLimits limits = AuraLimits.DEFAULTS;

    public AuraStatBridge(AuraService auras) {
        this.auras = auras;
    }

    /** 套用設定檔中的屬性上下限；重載設定時再呼叫一次即可。 */
    public void applyLimits(AuraLimits newLimits) {
        if (newLimits != null) {
            this.limits = newLimits;
        }
    }

    /** 目前生效的屬性安全閘。 */
    public AuraLimits limits() {
        return limits;
    }

    /** 角色啟用時登記對照。 */
    public void link(Player player, CharacterProfile character) {
        if (player != null && character != null) {
            characterToPlayer.put(character.id(), new WeakReference<>(player));
        }
    }

    /** 角色停用時移除對照。 */
    public void unlink(CharacterProfile character) {
        if (character != null) {
            characterToPlayer.remove(character.id());
        }
    }

    /** 依角色識別碼移除對照。 */
    public void unlink(UUID characterId) {
        characterToPlayer.remove(characterId);
    }

    /** 清空對照表。 */
    public void clear() {
        characterToPlayer.clear();
    }

    /**
     * 把該角色身上的增益減益折進屬性。
     *
     * <p>計算一律是「先加後乘」：{@code (原值 + flat) × (1 + percent/100)}。
     * 沒有任何狀態時原封不動回傳原物件，不額外配置。</p>
     *
     * @param character 角色
     * @param stats 原始屬性
     * @return 套用狀態後的屬性
     */
    public CombatStats decorate(CharacterProfile character, CombatStats stats) {
        if (character == null || stats == null) {
            return stats;
        }
        WeakReference<Player> reference = characterToPlayer.get(character.id());
        Player player = reference == null ? null : reference.get();
        if (player == null) {
            return stats;
        }
        Map<String, AuraDefinition.Modifier> modifiers = auras.aggregate(player);
        if (modifiers.isEmpty()) {
            return stats;
        }

        // 每一項的上下限都由 auras.yml 的 limits 區段決定,不寫死在程式裡。
        // 疊層是會失控的:四層碎甲能把防禦推成負值、三層遲滯讓人完全不能動,
        // 那不是「數值很高」而是遊戲直接壞掉,而修正它不該每次都要重新編譯。
        return new CombatStats(
                AuraLimits.clamp(apply(modifiers, "max_health", stats.maximumHealth()),
                        limits.minimumHealth(), 0),
                AuraLimits.clamp(apply(modifiers, "max_mana", stats.maximumMana()),
                        limits.minimumMana(), 0),
                AuraLimits.clamp(apply(modifiers, "attack_power", stats.attackPower()),
                        0, limits.maximumAttackPower()),
                AuraLimits.clamp(apply(modifiers, "defense", stats.defense()),
                        0, limits.maximumDefense()),
                Math.max(0.0, apply(modifiers, "resistance", stats.resistance())),
                AuraLimits.clamp(apply(modifiers, "speed", stats.speed()),
                        limits.minimumSpeed(), limits.maximumSpeed()),
                Math.max(0.0, apply(modifiers, "damage_taken_multiplier", stats.damageTakenMultiplier())),
                Math.max(0.0, apply(modifiers, "basic_attack_multiplier", stats.basicAttackMultiplier())),
                stats.strengthPoints(),
                stats.dexterityPoints(),
                stats.intelligencePoints(),
                stats.defencePoints(),
                stats.agilityPoints(),
                AuraLimits.clamp(apply(modifiers, "critical_chance", stats.criticalChance()),
                        0, limits.maximumCriticalChance()),
                AuraLimits.clamp(apply(modifiers, "spell_cost_reduction", stats.spellCostReduction()),
                        0, limits.maximumSpellCostReduction()),
                AuraLimits.clamp(apply(modifiers, "dodge_chance", stats.dodgeChance()),
                        0, limits.maximumDodgeChance()),
                apply(modifiers, "knockback_bonus", stats.knockbackBonus()),
                Math.max(0.0, apply(modifiers, "health_regeneration", stats.healthRegeneration())),
                Math.max(0.0, apply(modifiers, "mana_regeneration", stats.manaRegeneration())));
    }

    /** 對單一屬性套用「先加後乘」。 */
    private static double apply(Map<String, AuraDefinition.Modifier> modifiers, String stat, double baseValue) {
        AuraDefinition.Modifier modifier = modifiers.get(stat);
        if (modifier == null) {
            return baseValue;
        }
        double result = (baseValue + modifier.flat()) * (1 + modifier.percent() / 100.0);
        return Double.isFinite(result) ? result : baseValue;
    }
}
