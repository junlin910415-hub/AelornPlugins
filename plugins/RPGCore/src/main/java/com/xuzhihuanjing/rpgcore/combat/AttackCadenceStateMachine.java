package com.xuzhihuanjing.rpgcore.combat;

import com.xuzhihuanjing.rpgcore.integration.mythiccore.MythicCadenceBridge;

/** Pure state rules shared by the runtime and offline regression tests. */
public final class AttackCadenceStateMachine {
    public enum Phase {
        WINDUP,
        ACTIVE,
        RECOVERY,
        COMPLETE
    }

    private AttackCadenceStateMachine() {
    }

    public static Phase phase(MythicCadenceBridge.Timeline timeline, int elapsedTicks) {
        if (elapsedTicks < timeline.activeStartTick()) {
            return Phase.WINDUP;
        }
        if (elapsedTicks < timeline.recoveryStartTick()) {
            return Phase.ACTIVE;
        }
        if (elapsedTicks < timeline.totalTicks()) {
            return Phase.RECOVERY;
        }
        return Phase.COMPLETE;
    }

    public static boolean acceptsBufferedInput(
            MythicCadenceBridge.Timeline timeline,
            int elapsedTicks,
            boolean alreadyQueued) {
        return !alreadyQueued
                && phase(timeline, elapsedTicks) == Phase.RECOVERY
                && timeline.totalTicks() - elapsedTicks <= timeline.inputBufferTicks();
    }

    public static int nextComboStep(
            String profileId,
            String previousProfileId,
            int previousStep,
            long elapsedNanos,
            MythicCadenceBridge.Timeline timeline) {
        long resetNanos = timeline.comboResetTicks() * 50_000_000L;
        if (profileId != null
                && profileId.equals(previousProfileId)
                && elapsedNanos >= 0L
                && elapsedNanos <= resetNanos) {
            return previousStep % timeline.maximumComboSteps() + 1;
        }
        return 1;
    }
}
