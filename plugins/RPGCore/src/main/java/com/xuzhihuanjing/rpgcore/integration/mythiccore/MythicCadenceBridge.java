package com.xuzhihuanjing.rpgcore.integration.mythiccore;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Cached, optional bridge to the MythicCore 2.1 attack cadence service. */
public final class MythicCadenceBridge {
    private static final String API_CLASS =
            "tw.linsy.aelorn.mythiccore.api.combat.AttackCadenceApi";

    private final Logger logger;
    private final Object provider;
    private final Method calculateTimeline;
    private final Method snapshot;
    private volatile TimelineAccess timelineAccess;
    private volatile Method snapshotGet;
    private volatile boolean warned;

    public MythicCadenceBridge(PluginManager pluginManager, Logger logger) {
        this.logger = logger;
        Object discoveredProvider = null;
        Method discoveredMethod = null;
        Method discoveredSnapshot = null;
        Plugin plugin = pluginManager.getPlugin("MythicCore");
        if (plugin != null && plugin.isEnabled()) {
            try {
                Class<?> apiType = Class.forName(API_CLASS, true, plugin.getClass().getClassLoader());
                RegisteredServiceProvider<?> registration =
                        Bukkit.getServicesManager().getRegistration(apiType);
                if (registration != null && registration.getProvider() != null) {
                    discoveredProvider = registration.getProvider();
                    discoveredMethod = apiType.getMethod(
                            "calculateAttackTimeline", String.class, int.class, double.class);
                    discoveredSnapshot = discoveredProvider.getClass()
                            .getMethod("snapshot", LivingEntity.class);
                }
            } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
                warn("MythicCore 攻擊節奏 API 尚未可用，將使用 RPGCore 保守節奏："
                        + rootMessage(exception));
            }
        }
        this.provider = discoveredProvider;
        this.calculateTimeline = discoveredMethod;
        this.snapshot = discoveredSnapshot;
    }

    public boolean available() {
        return provider != null && calculateTimeline != null;
    }

    public Timeline timeline(
            String profileId,
            int comboStep,
            double attackSpeedRating,
            long fallbackCooldownMillis) {
        if (!available()) {
            return Timeline.fallback(profileId, fallbackCooldownMillis);
        }
        try {
            Object raw = calculateTimeline.invoke(
                    provider, profileId, comboStep, attackSpeedRating);
            if (raw == null) {
                return Timeline.fallback(profileId, fallbackCooldownMillis);
            }
            TimelineAccess access = timelineAccess;
            if (access == null || access.type() != raw.getClass()) {
                access = TimelineAccess.create(raw.getClass());
                timelineAccess = access;
            }
            return access.read(raw);
        } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
            warn("MythicCore 攻擊節奏呼叫失敗，已切換保守節奏：" + rootMessage(exception));
            return Timeline.fallback(profileId, fallbackCooldownMillis);
        }
    }

    public double attackSpeedRating(Player player, double fallback) {
        if (!available() || snapshot == null || player == null) {
            return sanitizeRating(fallback);
        }
        try {
            Object statSnapshot = snapshot.invoke(provider, player);
            if (statSnapshot == null) {
                return sanitizeRating(fallback);
            }
            Method getter = snapshotGet;
            if (getter == null || getter.getDeclaringClass() != statSnapshot.getClass()) {
                getter = statSnapshot.getClass().getMethod("get", String.class);
                snapshotGet = getter;
            }
            Object raw = getter.invoke(statSnapshot, "ATTACK_SPEED");
            if (raw instanceof Number number && Double.isFinite(number.doubleValue())) {
                return sanitizeRating(number.doubleValue());
            }
        } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
            warn("MythicCore 攻速快照讀取失敗，改用武器數值：" + rootMessage(exception));
        }
        return sanitizeRating(fallback);
    }

    private void warn(String message) {
        if (!warned) {
            warned = true;
            logger.warning(message);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable instanceof InvocationTargetException invocation
                && invocation.getCause() != null ? invocation.getCause() : throwable;
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    private static double sanitizeRating(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(5000.0, value)) : 0.0;
    }

    public record Timeline(
            String profileId,
            int comboStep,
            int maximumComboSteps,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            int inputBufferTicks,
            int comboResetTicks,
            double damageMultiplier,
            double rangeMultiplier,
            boolean interruptible,
            double interruptDamagePercent) {

        public Timeline {
            profileId = profileId == null || profileId.isBlank() ? "default" : profileId;
            comboStep = Math.max(1, comboStep);
            maximumComboSteps = Math.max(1, Math.min(8, maximumComboSteps));
            windupTicks = Math.max(1, Math.min(80, windupTicks));
            activeTicks = Math.max(1, Math.min(20, activeTicks));
            recoveryTicks = Math.max(1, Math.min(80, recoveryTicks));
            inputBufferTicks = Math.max(0, Math.min(recoveryTicks, inputBufferTicks));
            comboResetTicks = Math.max(1, Math.min(200, comboResetTicks));
            damageMultiplier = finiteClamp(damageMultiplier, 0.1, 3.0, 1.0);
            rangeMultiplier = finiteClamp(rangeMultiplier, 0.5, 1.5, 1.0);
            interruptDamagePercent = finiteClamp(interruptDamagePercent, 0.0, 100.0, 0.0);
        }

        public int activeStartTick() {
            return windupTicks;
        }

        public int recoveryStartTick() {
            return windupTicks + activeTicks;
        }

        public int totalTicks() {
            return windupTicks + activeTicks + recoveryTicks;
        }

        private static Timeline fallback(String profileId, long cooldownMillis) {
            int total = Math.max(5, Math.min(60,
                    (int) Math.ceil(Math.max(100L, cooldownMillis) / 50.0)));
            int windup = Math.max(2, (int) Math.round(total * 0.34));
            int active = 1;
            int recovery = Math.max(2, total - windup - active);
            return new Timeline(profileId, 1, 1, windup, active, recovery,
                    Math.min(3, recovery), 20, 1.0, 1.0, false, 0.0);
        }

        private static double finiteClamp(double value, double minimum, double maximum, double fallback) {
            return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
        }
    }

    private record TimelineAccess(
            Class<?> type,
            Method profileId,
            Method comboStep,
            Method maximumComboSteps,
            Method windupTicks,
            Method activeTicks,
            Method recoveryTicks,
            Method inputBufferTicks,
            Method comboResetTicks,
            Method damageMultiplier,
            Method rangeMultiplier,
            Method interruptible,
            Method interruptDamagePercent) {

        private static TimelineAccess create(Class<?> type) throws NoSuchMethodException {
            return new TimelineAccess(
                    type,
                    type.getMethod("profileId"),
                    type.getMethod("comboStep"),
                    type.getMethod("maximumComboSteps"),
                    type.getMethod("windupTicks"),
                    type.getMethod("activeTicks"),
                    type.getMethod("recoveryTicks"),
                    type.getMethod("inputBufferTicks"),
                    type.getMethod("comboResetTicks"),
                    type.getMethod("damageMultiplier"),
                    type.getMethod("rangeMultiplier"),
                    type.getMethod("interruptible"),
                    type.getMethod("interruptDamagePercent"));
        }

        private Timeline read(Object value) throws ReflectiveOperationException {
            return new Timeline(
                    text(profileId.invoke(value)),
                    integer(comboStep.invoke(value)),
                    integer(maximumComboSteps.invoke(value)),
                    integer(windupTicks.invoke(value)),
                    integer(activeTicks.invoke(value)),
                    integer(recoveryTicks.invoke(value)),
                    integer(inputBufferTicks.invoke(value)),
                    integer(comboResetTicks.invoke(value)),
                    decimal(damageMultiplier.invoke(value)),
                    decimal(rangeMultiplier.invoke(value)),
                    Boolean.TRUE.equals(interruptible.invoke(value)),
                    decimal(interruptDamagePercent.invoke(value)));
        }

        private static int integer(Object value) {
            return value instanceof Number number ? number.intValue() : 0;
        }

        private static double decimal(Object value) {
            return value instanceof Number number ? number.doubleValue() : 0.0;
        }

        private static String text(Object value) {
            return value == null ? "" : value.toString();
        }
    }
}
