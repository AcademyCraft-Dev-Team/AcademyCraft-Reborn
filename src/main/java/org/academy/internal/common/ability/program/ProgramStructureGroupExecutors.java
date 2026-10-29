package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.*;

import java.util.*;
import java.util.function.Function;

import static org.academy.api.common.ability.program.ProgramStructureOrientationRuntime.Operation;

/** One receipt per VM node. Polls the original snapshot instead of re-querying and re-acquiring a group. */
public final class ProgramStructureGroupExecutors {
    private ProgramStructureGroupExecutors() {}

    public static ProgramNodeStep move(ProgramVmContext context, ProgramStructureMovementNodes.Configuration config, ProgramInputView inputs) {
        var runtime = runtime(context);
        return run(context, runtime, inputs, targets -> runtime.moveStructures(targets, config.request(
                inputs.first("displacement").isPresent()
                        ? (ProgramVector) inputs.requireCompatible("displacement", ProgramValueTypes.VECTOR).value()
                        : runtime.defaultStructureDisplacement().orElseThrow(() -> new ProgramActionFailure(
                                ProgramVmDiagnostic.ACTION_UNAVAILABLE, "default_displacement_unavailable")), inputs)));
    }

    public static ProgramNodeStep rotate(ProgramVmContext context, Object configuration, ProgramInputView inputs) {
        var runtime = runtime(context);
        return run(context, runtime, inputs, targets -> runtime.rotateStructures(targets,
                inputs.first("pose").isPresent()
                        ? (ProgramStructurePose) inputs.requireCompatible("pose", ProgramValueTypes.STRUCTURE_POSE).value()
                        : new ProgramStructurePose(new ProgramDirection(0, 1, 0), Math.PI / 2)));
    }
    public static ProgramNodeStep reset(ProgramVmContext context, ProgramStructureOrientationNodes.ResetConfiguration configuration, ProgramInputView inputs) {
        var runtime = runtime(context);
        return run(context, runtime, inputs, targets -> runtime.resetStructures(targets, configuration.request()));
    }
    public static ProgramNodeStep spin(ProgramVmContext context, ProgramStructureOrientationNodes.SpinConfiguration configuration, ProgramInputView inputs) {
        var runtime = runtime(context);
        return run(context, runtime, inputs, targets -> runtime.spinStructures(targets, configuration.request(
                inputs.first("axis").isPresent()
                        ? (ProgramDirection) inputs.requireCompatible("axis", ProgramValueTypes.DIRECTION).value()
                        : new ProgramDirection(0, 1, 0))));
    }
    private static ProgramNodeStep run(ProgramVmContext context, ProgramStructureGroupRuntime runtime,
                                       ProgramInputView inputs, Function<List<ProgramStructureReference>, Operation> start) {
        var key = "structure_group:" + context.nodeId();
        var previous = context.executorState(key).map(Pending.class::cast).orElse(null);
        var targets = previous == null ? targets(context, inputs) : previous.targets();
        var receipt = previous == null ? null : previous.receipt();
        Operation operation;
        try { operation = Objects.requireNonNull(receipt == null ? start.apply(targets) : runtime.structureGroupStatus(receipt)); }
        catch (RuntimeException failure) { context.removeExecutorState(key); throw failure; }
        if (receipt != null && operation.operationId() != null && !receipt.equals(operation.operationId())) {
            context.removeExecutorState(key);
            throw new ProgramActionFailure(ProgramVmDiagnostic.ACTION_REJECTED, "group_receipt_changed");
        }
        if (operation.result().status() == ProgramStructureOperationResult.Status.PENDING) {
            context.setExecutorState(key, new Pending(operation.operationId(), targets));
            return ProgramNodeStep.retry(1);
        }
        context.removeExecutorState(key);
        var result = operation.result();
        if (result.status() != ProgramStructureOperationResult.Status.COMPLETED) {
            throw new ProgramActionFailure(ProgramVmDiagnostic.structureFailure(result), result.status().name().toLowerCase(Locale.ROOT) + ":" + result.detail());
        }
        return ProgramNodeStep.next("flow", Map.of("result", new ProgramValue<>(ProgramValueTypes.STRUCTURE_OPERATION_RESULT, result),
                "structures", new ProgramValue<>(ProgramValueTypes.STRUCTURE_SET, targets)));
    }
    private record Pending(UUID receipt, List<ProgramStructureReference> targets) {
        private Pending {
            Objects.requireNonNull(receipt, "receipt");
            targets = List.copyOf(targets);
        }
    }
    private static List<ProgramStructureReference> targets(ProgramVmContext context, ProgramInputView inputs) {
        boolean group = inputs.first("structures").isPresent();
        boolean single = inputs.first("structure").isPresent();
        if (group && single) throw new ProgramActionFailure(ProgramVmDiagnostic.ACTION_REJECTED, "ambiguous_structure_selection");
        if (group) {
            var values = (List<?>) inputs.requireCompatible("structures", ProgramValueTypes.STRUCTURE_SET).value();
            return ProgramStructureGroupControl.canonical(values.stream().map(ProgramStructureReference.class::cast).toList());
        }
        if (single) return List.of((ProgramStructureReference) inputs.requireCompatible("structure", ProgramValueTypes.STRUCTURE_REFERENCE).value());
        return List.of(context.targetResolver().flatMap(ProgramTargetResolver::lookStructureTarget)
                .orElseThrow(() -> new ProgramActionFailure(ProgramVmDiagnostic.ACTION_REJECTED, "look_structure_missing")));
    }
    private static ProgramStructureGroupRuntime runtime(ProgramVmContext context) {
        return context.attachment(ProgramExecutionFrame.class).flatMap(frame -> frame.environment(ProgramStructureGroupRuntime.class))
                .orElseThrow(() -> new IllegalStateException("Missing structure group runtime"));
    }
}
