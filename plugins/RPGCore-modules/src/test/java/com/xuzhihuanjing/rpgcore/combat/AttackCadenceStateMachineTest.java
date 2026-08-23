package com.xuzhihuanjing.rpgcore.combat;

import com.xuzhihuanjing.rpgcore.integration.mythiccore.MythicCadenceBridge;

public final class AttackCadenceStateMachineTest {
    private AttackCadenceStateMachineTest() {
    }

    public static void main(String[] arguments) {
        MythicCadenceBridge.Timeline timeline = new MythicCadenceBridge.Timeline(
                "great-weapon", 1, 2, 13, 2, 15, 5, 26,
                1.55, 1.08, true, 5.0);

        require(AttackCadenceStateMachine.phase(timeline, 0)
                == AttackCadenceStateMachine.Phase.WINDUP, "windup phase failed");
        require(AttackCadenceStateMachine.phase(timeline, 13)
                == AttackCadenceStateMachine.Phase.ACTIVE, "active phase failed");
        require(AttackCadenceStateMachine.phase(timeline, 15)
                == AttackCadenceStateMachine.Phase.RECOVERY, "recovery phase failed");
        require(AttackCadenceStateMachine.phase(timeline, 30)
                == AttackCadenceStateMachine.Phase.COMPLETE, "completion phase failed");
        require(!AttackCadenceStateMachine.acceptsBufferedInput(timeline, 20, false),
                "early recovery input must not queue");
        require(AttackCadenceStateMachine.acceptsBufferedInput(timeline, 26, false),
                "late recovery input must queue");
        require(!AttackCadenceStateMachine.acceptsBufferedInput(timeline, 26, true),
                "only one buffered input is allowed");
        require(AttackCadenceStateMachine.nextComboStep(
                "great-weapon", "great-weapon", 1, 1_000_000L, timeline) == 2,
                "combo did not advance");
        require(AttackCadenceStateMachine.nextComboStep(
                "great-weapon", "great-weapon", 2, 1_000_000L, timeline) == 1,
                "combo did not wrap");
        require(AttackCadenceStateMachine.nextComboStep(
                "great-weapon", "great-weapon", 1, 2_000_000_000L, timeline) == 1,
                "expired combo did not reset");

        System.out.println("AttackCadenceStateMachineTest PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
