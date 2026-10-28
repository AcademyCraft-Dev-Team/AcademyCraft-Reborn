package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.*;

import java.util.*;
import java.util.function.Supplier;

import static org.academy.api.common.ability.program.ProgramStructureOrientationRuntime.Operation;

/** One receipt per VM node. Polls the original snapshot instead of re-querying and re-acquiring a group. */
public final class ProgramStructureGroupExecutors {
    private ProgramStructureGroupExecutors() {}

    public static ProgramNodeStep move(ProgramVmContext context, ProgramStructureMovementNodes.Configuration config, ProgramInputView inputs) {
        var runtime = runtime(context);
        return run(context, runtime, () -> runtime.moveStructures(targets(inputs), config.request(
                (ProgramVector) inputs.requireCompatible("displacement", ProgramValueTypes.VECTOR).value())));
    }

    public static ProgramNodeStep rotate(ProgramVmContext context, Object configuration, ProgramInputView inputs) {
        var runtime = runtime(context);
        return run(context, runtime, () -> runtime.rotateStructures(targets(inputs),
                (ProgramStructurePose) inputs.requireCompatible("pose", ProgramValueTypes.STRUCTURE_POSE).value()));
    }
    public static ProgramNodeStep reset(ProgramVmContext context, ProgramStructureOrientationNodes.ResetConfiguration configuration, ProgramInputView inputs) {
        var runtime = runtime(context);
        return run(context, runtime, () -> runtime.resetStructures(targets(inputs), configuration.request()));
    }
    public static ProgramNodeStep spin(ProgramVmContext context, ProgramStructureOrientationNodes.SpinConfiguration configuration, ProgramInputView inputs) {
        var runtime = runtime(context);
        return run(context, runtime, () -> runtime.spinStructures(targets(inputs), configuration.request(
                (ProgramDirection) inputs.requireCompatible("axis", ProgramValueTypes.DIRECTION).value())));
    }
    private static ProgramNodeStep run(ProgramVmContext context, ProgramStructureGroupRuntime runtime, Supplier<Operation> start) {
        var key = "structure_group:" + context.nodeId();
        var receipt = context.executorState(key).map(UUID.class::cast).orElse(null);
        Operation operation;
        try { operation = Objects.requireNonNull(receipt == null ? start.get() : runtime.structureGroupStatus(receipt)); }
        catch (RuntimeException failure) { context.removeExecutorState(key); throw failure; }
        if (receipt != null && operation.operationId() != null && !receipt.equals(operation.operationId())) {
            context.removeExecutorState(key);
            throw new ProgramActionFailure(ProgramVmDiagnostic.ACTION_REJECTED, "group_receipt_changed");
        }
        if (operation.result().status() == ProgramStructureOperationResult.Status.PENDING) {
            context.setExecutorState(key, operation.operationId());
            return ProgramNodeStep.retry(1);
        }
        context.removeExecutorState(key);
        var result = operation.result();
        if (result.status() != ProgramStructureOperationResult.Status.COMPLETED) {
            throw new ProgramActionFailure(ProgramVmDiagnostic.structureFailure(result), result.status().name().toLowerCase(Locale.ROOT) + ":" + result.detail());
        }
        return ProgramNodeStep.next("flow", Map.of("result", new ProgramValue<>(ProgramValueTypes.STRUCTURE_OPERATION_RESULT, result)));
    }
    private static List<ProgramStructureReference> targets(ProgramInputView inputs) {
        var values = (List<?>) inputs.requireCompatible("structures", ProgramValueTypes.STRUCTURE_SET).value();
        return ProgramStructureGroupControl.canonical(values.stream().map(ProgramStructureReference.class::cast).toList());
    }
    private static ProgramStructureGroupRuntime runtime(ProgramVmContext context) {
        return context.attachment(ProgramExecutionFrame.class).flatMap(frame -> frame.environment(ProgramStructureGroupRuntime.class))
                .orElseThrow(() -> new IllegalStateException("Missing structure group runtime"));
    }
}
