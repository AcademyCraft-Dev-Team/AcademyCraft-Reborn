package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.*;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Keeps each asynchronous receipt in VM-private state, never in serializable graph variables. */
public final class ProgramStructureOrientationExecutors {
    private ProgramStructureOrientationExecutors() {
    }

    public static ProgramNodeStep rotate(ProgramVmContext context, Object configuration, ProgramInputView inputs) {
        var control = control(inputs);
        var runtime = runtime(context);
        var pose = inputs.first("pose").isPresent()
                ? (ProgramStructurePose) inputs.requireCompatible("pose", ProgramValueTypes.STRUCTURE_POSE).value()
                : null;
        return run(context, runtime, control, () -> runtime.rotateOrientation(control, pose));
    }

    public static ProgramNodeStep reset(ProgramVmContext context,
                                        ProgramStructureOrientationNodes.ResetConfiguration configuration,
                                        ProgramInputView inputs) {
        var control = control(inputs);
        var runtime = runtime(context);
        return run(context, runtime, control, () -> runtime.resetOrientation(control, configuration.request()));
    }

    public static ProgramNodeStep spin(ProgramVmContext context,
                                       ProgramStructureOrientationNodes.SpinConfiguration configuration,
                                       ProgramInputView inputs) {
        var control = control(inputs);
        var runtime = runtime(context);
        return run(context, runtime, control, () -> runtime.spinStructure(control,
                configuration.request((ProgramDirection) inputs.requireCompatible(
                        "axis", ProgramValueTypes.DIRECTION).value())));
    }

    private static ProgramNodeStep run(ProgramVmContext context, ProgramStructureOrientationRuntime runtime,
                                       ProgramStructureControl control,
                                       Supplier<ProgramStructureOrientationRuntime.Operation> submit) {
        var key = "structure_orientation:" + context.nodeId();
        var previous = context.executorState(key).map(Pending.class::cast).orElse(null);
        if (previous != null && !previous.control().equals(control)) {
            context.removeExecutorState(key);
            throw failure(ProgramStructureOperationResult.rejected("orientation_control_changed"));
        }
        ProgramStructureOrientationRuntime.Operation operation;
        try {
            operation = Objects.requireNonNull(previous == null ? submit.get()
                    : runtime.orientationStatus(previous.control(), previous.operationId()), "orientation operation");
        } catch (RuntimeException exception) {
            context.removeExecutorState(key);
            throw exception;
        }
        if (previous != null && operation.operationId() != null
                && !previous.operationId().equals(operation.operationId())) {
            context.removeExecutorState(key);
            throw failure(ProgramStructureOperationResult.rejected("orientation_receipt_changed"));
        }
        var result = operation.result();
        if (result.status() == ProgramStructureOperationResult.Status.PENDING) {
            context.setExecutorState(key, new Pending(control, operation.operationId()));
            return ProgramNodeStep.retry(1);
        }
        context.removeExecutorState(key);
        if (result.status() != ProgramStructureOperationResult.Status.COMPLETED) throw failure(result);
        return ProgramNodeStep.next("flow", Map.of("result",
                new ProgramValue<>(ProgramValueTypes.STRUCTURE_OPERATION_RESULT, result)));
    }

    private static ProgramActionFailure failure(ProgramStructureOperationResult result) {
        return new ProgramActionFailure(ProgramVmDiagnostic.structureFailure(result),
                result.status().name().toLowerCase(Locale.ROOT) + ":" + result.detail());
    }

    private static ProgramStructureControl control(ProgramInputView inputs) {
        return (ProgramStructureControl) inputs.requireCompatible("control", ProgramValueTypes.STRUCTURE_CONTROL).value();
    }

    private static ProgramStructureOrientationRuntime runtime(ProgramVmContext context) {
        return context.attachment(ProgramExecutionFrame.class)
                .flatMap(frame -> frame.environment(ProgramStructureOrientationRuntime.class))
                .orElseThrow(() -> new IllegalStateException("Missing structure orientation runtime"));
    }

    private record Pending(ProgramStructureControl control, UUID operationId) {
        private Pending {
            Objects.requireNonNull(control, "control");
            Objects.requireNonNull(operationId, "operationId");
        }
    }
}
