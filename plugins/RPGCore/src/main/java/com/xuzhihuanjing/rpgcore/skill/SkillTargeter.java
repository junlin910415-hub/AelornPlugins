package com.xuzhihuanjing.rpgcore.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * 目標選擇器 —— 決定一個技能步驟「打到誰」。
 *
 * <p>技能的表現力有一半來自命中判定的形狀。同樣是一發火焰，
 * 打成錐形是噴射、打成直線是雷射、打成圓形是爆炸，
 * 三者的玩法完全不同。把形狀抽成可設定的選項，
 * 企劃就能靠組合做出各種技能，不必每次都回頭改 Java。</p>
 *
 * <p>所有選擇器都<b>不會選到施法者自己</b>（{@link #SELF} 除外），
 * 也不會選到已死亡或無敵的目標。</p>
 */
public enum SkillTargeter {

    /** 只作用在施法者自己。用於增益、位移、護盾。 */
    SELF {
        @Override
        List<LivingEntity> resolve(Player caster, Params params) {
            return List.of(caster);
        }
    },

    /**
     * 以施法者為圓心的圓形範圍。
     *
     * <p>用於原地爆發：戰吼、震地、冰霜新星。</p>
     */
    CIRCLE {
        @Override
        List<LivingEntity> resolve(Player caster, Params params) {
            return nearby(caster, params.radius(), params.maxTargets(), candidate -> true);
        }
    },

    /**
     * 面向前方的扇形。
     *
     * <p>用於揮砍類：橫掃、劈斬。{@code angle} 為總張角（度），
     * 120 度大約是一個順手的橫揮範圍。</p>
     */
    CONE {
        @Override
        List<LivingEntity> resolve(Player caster, Params params) {
            Vector facing = caster.getLocation().getDirection().setY(0).normalize();
            double cosHalf = Math.cos(Math.toRadians(params.angle() / 2.0));
            Location origin = caster.getLocation();
            return nearby(caster, params.radius(), params.maxTargets(), candidate -> {
                Vector toTarget = candidate.getLocation().toVector()
                        .subtract(origin.toVector()).setY(0);
                if (toTarget.lengthSquared() < 1.0E-6) {
                    return true;
                }
                return facing.dot(toTarget.normalize()) >= cosHalf;
            });
        }
    },

    /**
     * 面向前方的直線（膠囊形）。
     *
     * <p>用於突刺、雷射、貫穿箭。{@code radius} 在此代表線的<b>半徑粗細</b>，
     * {@code range} 才是長度。</p>
     */
    LINE {
        @Override
        List<LivingEntity> resolve(Player caster, Params params) {
            Location eye = caster.getEyeLocation();
            Vector direction = eye.getDirection().normalize();
            double range = params.range();
            double thickness = Math.max(0.3, params.radius());
            return nearby(caster, range + thickness, params.maxTargets(), candidate -> {
                Vector toTarget = candidate.getLocation().add(0, candidate.getHeight() / 2, 0)
                        .toVector().subtract(eye.toVector());
                double along = toTarget.dot(direction);
                if (along < 0 || along > range) {
                    return false;
                }
                // 距離中心線的垂直距離
                double perpendicular = toTarget.clone()
                        .subtract(direction.clone().multiply(along)).length();
                return perpendicular <= thickness;
            });
        }
    },

    /**
     * 視線正前方的單一目標（射線命中）。
     *
     * <p>用於指向性技能：奧術飛彈、鎖定射擊。命中方塊即中止，
     * 因此隔牆是打不到的。</p>
     */
    RAYCAST {
        @Override
        List<LivingEntity> resolve(Player caster, Params params) {
            RayTraceResult hit = caster.getWorld().rayTrace(
                    caster.getEyeLocation(),
                    caster.getEyeLocation().getDirection(),
                    params.range(),
                    org.bukkit.FluidCollisionMode.NEVER,
                    true,
                    Math.max(0.1, params.radius()),
                    entity -> entity instanceof LivingEntity
                            && !entity.equals(caster)
                            && isValidTarget((LivingEntity) entity));
            if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
                return List.of();
            }
            return List.of(target);
        }
    },

    /**
     * 距離最近的若干個目標。
     *
     * <p>用於連鎖類：連鎖閃電、彈射飛刀。</p>
     */
    NEAREST {
        @Override
        List<LivingEntity> resolve(Player caster, Params params) {
            List<LivingEntity> found = nearby(caster, params.radius(), Integer.MAX_VALUE, candidate -> true);
            Location origin = caster.getLocation();
            found.sort(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(origin)));
            int limit = Math.min(found.size(), Math.max(1, params.maxTargets()));
            return List.copyOf(found.subList(0, limit));
        }
    };

    abstract List<LivingEntity> resolve(Player caster, Params params);

    /**
     * 由設定檔字串解析。
     *
     * @return 無法辨識時退回 {@link #CIRCLE}，並由呼叫端負責回報
     */
    public static SkillTargeter of(String name) {
        if (name == null || name.isBlank()) {
            return CIRCLE;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return CIRCLE;
        }
    }

    /**
     * 共用的鄰近搜尋。
     *
     * <p>用 {@code getNearbyEntities} 而非掃全世界實體：前者走區塊索引，
     * 後者在大型地圖上會是災難。技能每次施放都會呼叫，成本必須壓住。</p>
     */
    private static List<LivingEntity> nearby(Player caster, double radius, int maxTargets,
                                             java.util.function.Predicate<LivingEntity> filter) {
        double safeRadius = Math.max(0.5, radius);
        List<LivingEntity> result = new ArrayList<>();
        for (org.bukkit.entity.Entity entity :
                caster.getNearbyEntities(safeRadius, safeRadius, safeRadius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(caster)) {
                continue;
            }
            if (!isValidTarget(living) || !filter.test(living)) {
                continue;
            }
            result.add(living);
            if (maxTargets > 0 && result.size() >= maxTargets) {
                break;
            }
        }
        return result;
    }

    /** 排除死亡、無敵與觀察者模式的目標。 */
    private static boolean isValidTarget(LivingEntity entity) {
        if (entity.isDead() || !entity.isValid() || entity.isInvulnerable()) {
            return false;
        }
        if (entity instanceof Player player) {
            return player.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && player.getGameMode() != org.bukkit.GameMode.CREATIVE;
        }
        return true;
    }

    /**
     * 選擇器參數。
     *
     * @param radius 半徑；{@link #LINE} 時代表線的粗細
     * @param range 射程；{@link #LINE} 與 {@link #RAYCAST} 使用
     * @param angle 張角（度）；{@link #CONE} 使用
     * @param maxTargets 最多命中幾個；0 或負數代表不限
     */
    public record Params(double radius, double range, double angle, int maxTargets) {

        public Params {
            radius = clamp(radius, 0, 64);
            range = clamp(range, 0, 128);
            angle = clamp(angle, 1, 360);
            maxTargets = maxTargets <= 0 ? Integer.MAX_VALUE : Math.min(maxTargets, 200);
        }

        private static double clamp(double value, double low, double high) {
            if (!Double.isFinite(value)) {
                return low;
            }
            return Math.max(low, Math.min(high, value));
        }
    }
}
