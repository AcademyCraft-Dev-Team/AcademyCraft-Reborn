package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramStructureOperationResult;

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
    ACTION_CONDITION_FAILED,
    DIVISION_BY_ZERO,
    NON_FINITE_RESULT,
    ALREADY_RUNNING,
    EXECUTION_EXPIRED,
    CONTROL_RESISTANCE,
    NO_SIGHT_TARGET,
    NO_EFFECTIVE_SUBJECTS,
    UNREACHABLE_DESTINATION,
    PROFICIENCY_REQUIRED,
    TARGET_LIMIT,
    CLIENT_TIMEOUT,
    PLANNING_BUDGET_EXHAUSTED,
    UNSUPPORTED_MOVEMENT_MODE,
    ACTION_UNAVAILABLE,
    SUBLEVEL_RUNTIME_UNAVAILABLE,
    SUBLEVEL_PLOT_CAPACITY_UNAVAILABLE,
    TARGET_PHYSICS_PREPARE_FAILED,
    TARGET_PHYSICS_FINALIZE_FAILED,
    NO_FREE_NATIVE_SLOT,
    NOT_IN_PLAIN_BLOCK_WHITELIST;

    /** Capture services prefix stable reason tokens with their transaction status. */
    public static ProgramVmDiagnostic structureFailure(ProgramStructureOperationResult result) {
        for (var token : result.detail().split(":")) {
            var diagnostic = switch (token.trim().toLowerCase(Locale.ROOT)) {
                case "sublevel_runtime_unavailable" -> SUBLEVEL_RUNTIME_UNAVAILABLE;
                case "sublevel_plot_capacity_unavailable" -> SUBLEVEL_PLOT_CAPACITY_UNAVAILABLE;
                case "target_physics_prepare_failed" -> TARGET_PHYSICS_PREPARE_FAILED;
                case "target_finalize_failed", "target_physics_finalize_failed" -> TARGET_PHYSICS_FINALIZE_FAILED;
                case "no_free_native_slot" -> NO_FREE_NATIVE_SLOT;
                case "not_in_plain_block_whitelist" -> NOT_IN_PLAIN_BLOCK_WHITELIST;
                default -> null;
            };
            if (diagnostic != null) return diagnostic;
        }
        return result.status() == ProgramStructureOperationResult.Status.UNAVAILABLE
                ? ACTION_UNAVAILABLE : ACTION_REJECTED;
    }

    public String translationKey() {
        var prefix = switch (this) {
            case CONTROL_RESISTANCE, NO_SIGHT_TARGET, NO_EFFECTIVE_SUBJECTS,
                 UNREACHABLE_DESTINATION, PROFICIENCY_REQUIRED, TARGET_LIMIT, CLIENT_TIMEOUT,
                 PLANNING_BUDGET_EXHAUSTED, UNSUPPORTED_MOVEMENT_MODE -> "message.academy.precision_operation.";
            default -> "message.academy.program.execution.diagnostic.";
        };
        return prefix + name().toLowerCase(Locale.ROOT);
    }
}
