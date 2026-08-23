package com.xuzhihuanjing.rpgcore.skill;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

/**
 * 一次技能施放的執行環境。
 *
 * <p>機制不直接碰傷害管線與增益服務，而是透過這裡的 {@link Hooks} 轉接。
 * 這樣 {@code skill} 套件就不反向依賴戰鬥與增益模組，
 * 也讓技能腳本可以在沒有整個插件的情況下單獨測試。</p>
 *
 * <p>粒子、音效、位移這類純 Bukkit 操作則直接呼叫，沒必要多包一層。</p>
 */
public final class SkillContext {

    private final Player caster;
    private final double powerScale;
    private final Hooks hooks;

    /**
     * @param caster 施法者
     * @param powerScale 威力係數，通常來自施法者的攻擊力
     * @param hooks 對外部服務的轉接
     */
    public SkillContext(Player caster, double powerScale, Hooks hooks) {
        this.caster = caster;
        this.powerScale = Double.isFinite(powerScale) && powerScale > 0 ? powerScale : 1.0;
        this.hooks = hooks;
    }

    public Player caster() {
        return caster;
    }

    /**
     * 把設定檔寫的係數換算成實際傷害。
     *
     * <p>設定檔寫的是「攻擊力的幾倍」而非絕對數值，
     * 技能才會隨玩家成長而變強，不必為每個等級各寫一份。</p>
     */
    public double scaleDamage(double coefficient) {
        return Math.max(0, coefficient) * powerScale;
    }

    /** 治療同樣依威力係數換算。 */
    public double scaleHealing(double coefficient) {
        return Math.max(0, coefficient) * powerScale;
    }

    // ------------------------------------------------------------------
    // 轉接到外部服務
    // ------------------------------------------------------------------

    void dealDamage(LivingEntity target, double amount, boolean magic) {
        if (hooks != null && amount > 0) {
            hooks.damage(caster, target, amount, magic);
        }
    }

    void heal(LivingEntity target, double amount) {
        if (hooks != null && amount > 0) {
            hooks.heal(target, amount);
        }
    }

    void applyAura(LivingEntity target, String auraId, int stacks) {
        if (hooks != null && auraId != null && !auraId.isBlank()) {
            hooks.applyAura(target, auraId, stacks, caster.getUniqueId());
        }
    }

    void cleanse(LivingEntity target, String auraId) {
        if (hooks != null) {
            hooks.cleanse(target, auraId);
        }
    }

    // ------------------------------------------------------------------
    // 直接的 Bukkit 操作
    // ------------------------------------------------------------------

    void push(LivingEntity target, Vector velocity) {
        // 夾住力道:設定檔手滑寫個 50 會把玩家彈到世界外
        Vector safe = velocity.clone();
        if (safe.length() > 4.0) {
            safe = safe.normalize().multiply(4.0);
        }
        target.setVelocity(target.getVelocity().add(safe));
    }

    void spawnParticle(Particle particle, Location location, int count, double spread) {
        double offset = spread > 0 ? spread : 0.4;
        location.getWorld().spawnParticle(particle, location, count, offset, offset, offset, 0.02);
    }

    void playSound(Sound sound, Location location, float volume, float pitch) {
        location.getWorld().playSound(location, sound, volume, pitch);
    }

    void strikeLightningEffect(Location location) {
        location.getWorld().strikeLightningEffect(location);
    }

    void ignite(LivingEntity target, int ticks) {
        target.setFireTicks(Math.max(target.getFireTicks(), ticks));
    }

    void addPotion(LivingEntity target, PotionEffect effect) {
        target.addPotionEffect(effect);
    }

    /**
     * 技能對外部服務的依賴。
     *
     * <p>由插件在啟動時接上真正的實作；測試時可以餵假物件。</p>
     */
    public interface Hooks {

        /**
         * 造成技能傷害，走既有的傷害管線（減傷、爆擊、元素都會生效）。
         *
         * @param magic {@code true} 為魔法傷害、{@code false} 為物理傷害；
         *              由技能設定的 {@code damage-kind} 決定，法杖類技能通常吃魔法減傷
         */
        void damage(Player caster, LivingEntity target, double amount, boolean magic);

        /** 回復生命。 */
        void heal(LivingEntity target, double amount);

        /** 掛上增益或減益。 */
        void applyAura(LivingEntity target, String auraId, int stacks, UUID source);

        /** 移除增益或減益；{@code auraId} 為空代表驅散全部減益。 */
        void cleanse(LivingEntity target, String auraId);
    }
}
