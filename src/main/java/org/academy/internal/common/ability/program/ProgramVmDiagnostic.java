package org.academy.internal.common.ability.program;

import java.util.Locale;

public enum ProgramVmDiagnostic {
    NONE,
    MISSING_EXECUTOR,
    MISSING_INPUT_VALUE,
    INVALID_OUTPUT,
    INVALID_FLOW_OUTPUT,
    EXECUTOR_ERROR,
    ACTION_REJECTED,
    TARGET_OUT_OF_RANGE,
    INSUFFICIENT_CP,
    SKILL_UNAVAILABLE,
    TARGET_INVALID,
    TARGET_MOVEMENT_PROTECTED,
    TARGET_PROTECTED,
    TARGET_REJECTED,
    TARGET_TYPE_UNSUPPORTED,
    WORLD_UNAVAILABLE,
    BLOCK_BREAK_DISABLED,
    BLOCK_UNBREAKABLE,
    DESTINATION_BLOCKED,
    DESTINATION_UNSAFE,
    INVENTORY_FULL,
    INVALID_DIRECTION,
    POWER_LIMIT,
    SPAWN_FAILED,
    ACTION_CONDITION_FAILED;

    public String translationKey() {
        return "message.academy.program.execution.diagnostic."
                + name().toLowerCase(Locale.ROOT);
    }
}
