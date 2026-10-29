package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.*;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Waits for one accepted force window without resubmitting or charging the action again. */
public final class ProgramStructureMovementExecutors {
    private ProgramStructureMovementExecutors() {}

    public record Request(ProgramStructureControl control, @Nullable ProgramWorldPosition target,
                          @Nullable ProgramStructureAnchor anchor, double seconds) {
        public Request {
            Objects.requireNonNull(control, "control");
            if (!Double.isFinite(seconds) || seconds < .1 || seconds > 10) {
                throw new IllegalArgumentException("Movement duration must be in [0.1, 10] seconds");
            }
        }
        long ticks() { return (long) Math.ceil(seconds * 20); }
    }

    public static ProgramNodeStep run(ProgramVmContext context, ProgramStructureControlRuntime runtime,
                                      Supplier<Request> requestSupplier,
                                      Function<Request, ProgramStructureOperationResult> submit) {
        var key = "structure_movement:" + context.nodeId();
        var previous = context.executorState(key).map(Pending.class::cast).orElse(null);
        try {
            var request = previous == null ? requestSupplier.get() : previous.request();
            var status = runtime.structureControlStatus(request.control());
            if (status.status() != ProgramStructureOperationResult.Status.COMPLETED) throw failure(status);
            var now = context.gameTime();
            if (previous == null) {
                var result = Objects.requireNonNull(submit.apply(request), "movement submission");
                if (result.status() != ProgramStructureOperationResult.Status.COMPLETED) throw failure(result);
                previous = new Pending(request, now, Math.addExact(now, request.ticks()));
                context.setExecutorState(key, previous);
            }
            if (now < previous.startedAt()) throw failure(ProgramStructureOperationResult.rejected("movement_clock_reversed"));
            if (now < previous.completedAt()) return ProgramNodeStep.retry(1);
            var finished = runtime.finishStructureMovement(request.control());
            if (finished.status() != ProgramStructureOperationResult.Status.COMPLETED) throw failure(finished);
            context.removeExecutorState(key);
            return ProgramNodeStep.next("flow", Map.of(
                    "control", new ProgramValue<>(ProgramValueTypes.STRUCTURE_CONTROL, request.control()),
                    "result", new ProgramValue<>(ProgramValueTypes.STRUCTURE_OPERATION_RESULT,
                            ProgramStructureOperationResult.completed("movement_window_completed"))));
        } catch (RuntimeException failure) {
            context.removeExecutorState(key);
            throw failure;
        }
    }
    private static ProgramActionFailure failure(ProgramStructureOperationResult result) {
        return new ProgramActionFailure(ProgramVmDiagnostic.structureFailure(result),
                result.status().name().toLowerCase(Locale.ROOT) + ":" + result.detail());
    }
    private record Pending(Request request, long startedAt, long completedAt) {}
}
