package org.academy.internal.common.ability.program;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.ProgramStructureControl;
import org.academy.api.common.ability.program.ProgramStructureOrientationRuntime;
import org.academy.api.common.ability.program.ProgramStructurePose;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.api.common.structure.upgrade.StructureOrientation;
import org.academy.api.common.structure.upgrade.StructureOrientations;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import static org.academy.api.common.ability.program.ProgramStructureOrientationRuntime.Operation;

/** Server policy and runtime-scoped receipts shared by all three structure-capable categories. */
public final class ProgramOrientationControl {
    static final double MAX_ANGULAR_SPEED = 2.0;
    static final int DEFAULT_ROTATE_TICKS = 100;
    static final int BRAKE_TICKS = 40;
    private static final int MAX_PENDING = 256;

    private final ServerPlayer player;
    private final double maximumRange;
    private final Function<ProgramStructureControl, @Nullable String> validateToken;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ProgramOrientationControl(ServerPlayer player, double maximumRange,
                                     Function<ProgramStructureControl, @Nullable String> validateToken) {
        this.player = Objects.requireNonNull(player, "player");
        if (!Double.isFinite(maximumRange) || maximumRange <= 0) throw new IllegalArgumentException("Invalid range");
        this.maximumRange = maximumRange;
        this.validateToken = Objects.requireNonNull(validateToken, "validateToken");
    }

    public Operation rotate(ProgramStructureControl control, @Nullable ProgramStructurePose pose,
                            Skill skill, float cost) {
        if (pose == null) return Operation.rejected("structure_pose_required");
        final StructurePose target;
        try {
            target = SubLevelProgramControl.requestedRotation(pose);
        } catch (IllegalArgumentException exception) {
            return Operation.rejected("invalid_structure_pose");
        }
        var limits = limits(DEFAULT_ROTATE_TICKS);
        return submit(control, limits, skill, cost, context -> StructureOrientations.service().rotateTo(context,
                new StructureOrientation.RotateRequest(control.controlId(), target, limits)));
    }

    public Operation reset(ProgramStructureControl control, ProgramStructureOrientationRuntime.ResetRequest request,
                           Skill skill, float cost) {
        Objects.requireNonNull(request, "request");
        var limits = limits(request.timeoutTicks());
        return submit(control, limits, skill, cost, context -> StructureOrientations.service().reset(context,
                new StructureOrientation.ResetRequest(control.controlId(), request.mode(),
                        request.missingBaselinePolicy(), limits)));
    }

    public Operation spin(ProgramStructureControl control, ProgramStructureOrientationRuntime.SpinRequest request,
                          Skill skill, float cost) {
        Objects.requireNonNull(request, "request");
        var rejection = spinRejection(request);
        if (rejection != null) return Operation.rejected(rejection);
        var limits = limits(request.durationTicks() + BRAKE_TICKS);
        var axis = new Vec3(request.axis().x(), request.axis().y(), request.axis().z());
        return submit(control, limits, skill, cost, context -> StructureOrientations.service().spin(context,
                new StructureOrientation.SpinRequest(control.controlId(), axis, request.frame(),
                        request.angularSpeed(), request.durationTicks(), request.finishMode(), limits)));
    }

    public Operation status(ProgramStructureControl control, UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        var receipt = pending.get(operationId);
        if (receipt == null || !receipt.control().equals(control)) {
            return Operation.rejected("orientation_receipt_not_owned");
        }
        var rejection = validate(control);
        if (rejection != null) {
            stop(operationId, receipt);
            return Operation.rejected(rejection);
        }
        try {
            // Keep the submitted content revision. A poll must never accept a changed body epoch.
            var result = operation(StructureOrientations.service().status(receipt.context(),
                    new StructureOrientation.StatusRequest(control.controlId(), operationId)));
            if (result.operationId() != null && !result.operationId().equals(operationId)) {
                stop(operationId, receipt);
                return Operation.rejected("orientation_receipt_changed");
            }
            if (!isPending(result)) pending.remove(operationId);
            return result;
        } catch (RuntimeException exception) {
            stop(operationId, receipt);
            throw exception;
        }
    }

    /** Stops only receipts issued by this runtime, never a replacement command or the standing lease. */
    public void cancel(ProgramStructureControl control) {
        for (var entry : List.copyOf(pending.entrySet())) {
            if (entry.getValue().control().equals(control)) stop(entry.getKey(), entry.getValue());
        }
    }

    public void close() {
        for (var entry : List.copyOf(pending.entrySet())) stop(entry.getKey(), entry.getValue());
    }

    private Operation submit(ProgramStructureControl control, StructureOrientation.Limits limits, Skill skill,
                             float cost, Function<StructureOperation.Context,
            StructureOperation.Result<StructureOrientation.Snapshot>> action) {
        var rejection = validate(control);
        if (rejection != null) return Operation.rejected(rejection);
        var context = StructureOrientations.context(player.level(), control.reference(), player).orElse(null);
        if (context == null) return Operation.unavailable("orientation_context_unavailable");
        if (!context.runtimeId().equals(control.structureRuntimeId())) return Operation.rejected("structure_epoch_changed");
        if (pending.size() >= MAX_PENDING && pending.values().stream().noneMatch(value -> value.control().equals(control))) {
            return Operation.rejected("orientation_receipt_budget");
        }
        var submitted = new java.util.ArrayList<Operation>(1);
        try {
            // This shared gate checks spendable CP before invoking action and charges only accepted work.
            return StructureProgramActions.execute(player, skill, cost, () -> {
                var result = operation(action.apply(context));
                submitted.add(result);
                if (accepted(result)) {
                    pending.entrySet().removeIf(entry -> entry.getValue().control().equals(control));
                    if (isPending(result)) pending.put(result.operationId(), new Pending(control, context, limits));
                }
                return result;
            }, ProgramOrientationControl::accepted);
        } catch (RuntimeException exception) {
            // A failed resource settlement must not leave a free angular command running.
            if (!submitted.isEmpty() && accepted(submitted.getFirst())) cancel(control);
            throw exception;
        }
    }

    private @Nullable String validate(ProgramStructureControl control) {
        var rejection = validateToken.apply(Objects.requireNonNull(control, "control"));
        if (rejection != null) return rejection;
        if (!player.isAlive() || player.isRemoved() || player.hasDisconnected() || player.isSpectator()) {
            return "orientation_actor_unavailable";
        }
        if (!player.level().dimension().identifier().equals(control.reference().dimension())) return "structure_wrong_dimension";
        if (StructureDirectControl.validate(player.level(), control.reference(), player, control.controlId())
                != StructureDirectControl.Status.ACTIVE) return "structure_control_expired";
        var motion = StructureDynamics.motion(player.level(), control.reference()).orElse(null);
        if (motion == null) return "structure_motion_unavailable";
        if (motion.pose().localToWorldPoint(motion.localCenterOfMass()).distanceToSqr(player.position())
                > maximumRange * maximumRange) return "structure_out_of_range";
        return null;
    }

    private void stop(UUID id, Pending receipt) {
        pending.remove(id);
        StructureOrientations.service().stop(receipt.context(), new StructureOrientation.StopRequest(
                receipt.control().controlId(), id, StructureOrientation.StopMode.RELEASE_INERTIA, receipt.limits()));
    }

    static StructureOrientation.Limits limits(int ticks) {
        return new StructureOrientation.Limits(MAX_ANGULAR_SPEED, 6.0, 1_000.0, ticks,
                0.01, 0.02, 0.0, 16_384);
    }

    static @Nullable String spinRejection(ProgramStructureOrientationRuntime.SpinRequest request) {
        if (Math.abs(request.angularSpeed()) > MAX_ANGULAR_SPEED) return "orientation_speed_exceeds_server_limit";
        if (request.durationTicks() > StructureOrientation.MAX_DURATION_TICKS - BRAKE_TICKS) {
            return "orientation_duration_leaves_no_braking_budget";
        }
        return null;
    }

    static Operation operation(StructureOperation.Result<StructureOrientation.Snapshot> result) {
        return switch (result.status()) {
            case RUNNING -> Operation.pending(result.operationId().orElseThrow(), result.detail());
            case TERRAIN_PENDING -> result.operationId().map(id -> Operation.pending(id, result.detail()))
                    .orElseGet(() -> Operation.blocked(result.detail()));
            case COMPLETED -> new Operation(org.academy.api.common.ability.program.ProgramStructureOperationResult
                    .completed(result.detail()), result.operationId().orElse(null));
            case BLOCKED -> Operation.blocked(result.detail());
            case UNSUPPORTED -> Operation.unavailable(result.detail());
            case READY -> Operation.rejected("orientation_preview_not_started");
            case CONFLICT, STALE, DENIED -> Operation.rejected(result.detail());
        };
    }

    private static boolean accepted(Operation operation) {
        return isPending(operation) || operation.result().status()
                == org.academy.api.common.ability.program.ProgramStructureOperationResult.Status.COMPLETED;
    }

    private static boolean isPending(Operation operation) {
        return operation.result().status()
                == org.academy.api.common.ability.program.ProgramStructureOperationResult.Status.PENDING;
    }

    private record Pending(ProgramStructureControl control, StructureOperation.Context context,
                           StructureOrientation.Limits limits) {}
}
