package tw.linsy.aelorn.rpgcore.skill;

import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * 技能機制 —— 一個步驟「做什麼事」。
 *
 * <p>這些是技能的最小積木。單獨看每一塊都很樸素，
 * 但配上目標形狀與延遲之後就能組出各種花樣：
 * 「前搖粒子 → 停頓 12 tick → 錐形重擊 + 擊退 + 掛碎甲 → 餘波音效」
 * 這種節奏完全不需要寫任何 Java。</p>
 *
 * <p>刻意保持每個機制只做一件事。合併成「傷害兼擊退兼特效」看似方便，
 * 實際上會讓企劃無法單獨調整其中一項，最後又得回頭加參數。</p>
 */
public enum SkillMechanic {

    /**
     * 造成傷害。實際數值由 {@link SkillContext} 依施法者屬性換算。
     *
     * <p>{@code damage-kind} 填 {@code MAGIC} 會走魔法減傷，
     * 留空則預設物理。法杖與元素技能應該填 {@code MAGIC}，
     * 否則玩家堆魔抗完全擋不到法師的輸出。</p>
     */
    DAMAGE {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            double amount = context.scaleDamage(params.amount());
            boolean magic = "MAGIC".equalsIgnoreCase(params.text());
            for (LivingEntity target : targets) {
                context.dealDamage(target, amount, magic);
            }
        }
    },

    /** 治療。目標為施法者時就是自我回復。 */
    HEAL {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            double amount = context.scaleHealing(params.amount());
            for (LivingEntity target : targets) {
                context.heal(target, amount);
            }
        }
    },

    /**
     * 掛上增益或減益。
     *
     * <p>這是把 {@code auras.yml} 那八種效果接進實戰的關鍵機制。
     * {@code aura} 填定義代號，{@code stacks} 填層數。</p>
     */
    AURA {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            for (LivingEntity target : targets) {
                context.applyAura(target, params.text(), (int) Math.max(1, params.amount()));
            }
        }
    },

    /** 移除增益或減益。{@code aura} 填代號，留空代表驅散全部減益。 */
    CLEANSE {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            for (LivingEntity target : targets) {
                context.cleanse(target, params.text());
            }
        }
    },

    /** 把目標往外推。{@code amount} 為力道。 */
    KNOCKBACK {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            Location origin = context.caster().getLocation();
            for (LivingEntity target : targets) {
                Vector push = target.getLocation().toVector().subtract(origin.toVector());
                if (push.lengthSquared() < 1.0E-6) {
                    push = context.caster().getLocation().getDirection();
                }
                push = push.normalize().multiply(params.amount()).setY(Math.max(0.25, params.amount() * 0.35));
                context.push(target, push);
            }
        }
    },

    /** 把目標拉向施法者。用於鉤爪、吸引類技能。 */
    PULL {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            Location origin = context.caster().getLocation();
            for (LivingEntity target : targets) {
                Vector pull = origin.toVector().subtract(target.getLocation().toVector());
                if (pull.lengthSquared() < 1.0E-6) {
                    continue;
                }
                context.push(target, pull.normalize().multiply(params.amount()).setY(0.2));
            }
        }
    },

    /**
     * 施法者向前衝刺。
     *
     * <p>只作用在施法者身上，與目標無關；{@code amount} 為衝刺力道。</p>
     */
    DASH {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            Player caster = context.caster();
            Vector direction = caster.getLocation().getDirection().normalize()
                    .multiply(params.amount());
            // 保留一點上拋,避免貼地衝刺被方塊邊緣卡住
            direction.setY(Math.max(direction.getY(), 0.2));
            context.push(caster, direction);
        }
    },

    /** 施法者向上躍起。 */
    LEAP {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            Player caster = context.caster();
            Vector up = caster.getLocation().getDirection().normalize()
                    .multiply(params.amount() * 0.4);
            up.setY(params.amount());
            context.push(caster, up);
        }
    },

    /** 播放粒子效果。{@code particle} 填粒子名稱，{@code amount} 為數量。 */
    PARTICLE {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            Particle particle = parseParticle(params.text());
            if (particle == null) {
                return;
            }
            int count = (int) Math.max(1, params.amount());
            if (targets.isEmpty()) {
                context.spawnParticle(particle, context.caster().getLocation(), count, params.spread());
                return;
            }
            for (LivingEntity target : targets) {
                context.spawnParticle(particle,
                        target.getLocation().add(0, target.getHeight() / 2, 0), count, params.spread());
            }
        }
    },

    /** 播放音效。{@code sound} 填音效名稱，{@code amount} 為音量。 */
    SOUND {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            Sound sound = parseSound(params.text());
            if (sound == null) {
                return;
            }
            float volume = (float) Math.max(0.1, params.amount());
            float pitch = (float) (params.spread() > 0 ? params.spread() : 1.0);
            context.playSound(sound, context.caster().getLocation(), volume, pitch);
        }
    },

    /** 在目標位置降下視覺閃電（不造成原版傷害，傷害請另外用 DAMAGE）。 */
    LIGHTNING {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            for (LivingEntity target : targets) {
                context.strikeLightningEffect(target.getLocation());
            }
        }
    },

    /** 點燃目標。{@code amount} 為秒數。 */
    IGNITE {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            int ticks = (int) Math.max(1, params.amount() * 20);
            for (LivingEntity target : targets) {
                context.ignite(target, ticks);
            }
        }
    },

    /**
     * 施加原版藥水效果。
     *
     * <p>{@code potion} 填效果名稱，{@code amount} 為持續秒數，
     * {@code spread} 借用為等級（0 代表 I 級）。</p>
     */
    POTION {
        @Override
        void apply(SkillContext context, List<LivingEntity> targets, Params params) {
            PotionEffectType type = parsePotion(params.text());
            if (type == null) {
                return;
            }
            int ticks = (int) Math.max(1, params.amount() * 20);
            int amplifier = (int) Math.max(0, params.spread());
            for (LivingEntity target : targets) {
                context.addPotion(target, new PotionEffect(type, ticks, amplifier, true, false));
            }
        }
    };

    abstract void apply(SkillContext context, List<LivingEntity> targets, Params params);

    /**
     * 由設定檔字串解析。
     *
     * @return 無法辨識時回傳 {@code null}，由載入端回報錯誤而非默默略過
     */
    public static SkillMechanic of(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Particle parseParticle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 解析音效名稱。
     *
     * <p>{@code Sound} 在新版 API 已從列舉改為登錄表型別，沒有 {@code valueOf}，
     * 因此走 {@link org.bukkit.Registry}。設定檔可寫
     * {@code ENTITY_BLAZE_SHOOT} 或 {@code entity.blaze.shoot}，兩種都認。</p>
     */
    private static Sound parseSound(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT).replace('_', '.');
        return org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.minecraft(key));
    }

    private static PotionEffectType parsePotion(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return org.bukkit.Registry.EFFECT.get(
                org.bukkit.NamespacedKey.minecraft(
                        name.trim().toLowerCase(Locale.ROOT).replace('-', '_')));
    }

    /**
     * 機制參數。
     *
     * @param amount 主要數值（傷害、治療、力道、秒數、層數…依機制而異）
     * @param text 文字參數（增益代號、粒子名、音效名、藥水名）
     * @param spread 次要數值（粒子擴散、音高、藥水等級）
     */
    public record Params(double amount, String text, double spread) {

        public Params {
            amount = Double.isFinite(amount) ? amount : 0;
            text = text == null ? "" : text.trim();
            spread = Double.isFinite(spread) ? Math.max(0, spread) : 0;
        }
    }
}
