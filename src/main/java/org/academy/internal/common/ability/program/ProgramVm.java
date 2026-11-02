package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.internal.common.ability.program.compile.CompiledProgram;
import org.academy.internal.common.ability.program.registry.ProgramExecutorLookup;
import org.academy.internal.common.ability.program.registry.ProgramNodeExecutor;


import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramActionRejection;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.api.common.ability.program.ProgramInputView;
import org.academy.api.common.ability.program.ProgramNodeStep;
import org.academy.api.common.ability.program.ProgramValue;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Resumable, fuel-metered interpreter. Exhausting a time slice never blocks the server tick.
 */
public final class ProgramVm {
    private static final long FAILURE_LOG_INTERVAL_TICKS = 1_200L;
    private static final int MAX_FAILURE_LOG_KEYS = 256;
    private static final int MAX_FAILURE_REASON_LENGTH = 160;
    private static final Map<FailureKey, FailureWindow> FAILURE_LOGS = new LinkedHashMap<>();

    private ProgramVm() {
    }

    public static final class Session {
        private final CompiledProgram program;
        private final Map<CompiledProgram.OutputKey, ProgramValue<?>> latchedOutputs = new HashMap<>();
        private final Map<String, ProgramValue<?>> variables = new HashMap<>();
        private final Map<String, Object> executorState = new HashMap<>();
        private final ArrayDeque<Integer> returnNodes = new ArrayDeque<>();
        private int currentNodeId;
        private long wakeAt;
        private boolean completeAfterWake;
        private boolean completed;
        private boolean failed;
        private long lastRunLogicalTick = -1;
        private int suspensionSource = -1;
        private org.academy.api.common.ability.program.ProgramRunStatus.WaitKind suspensionKind =
                org.academy.api.common.ability.program.ProgramRunStatus.WaitKind.NONE;
        private @Nullable ProgramVmResult lastObservationResult;

        public Session(CompiledProgram program) {
            this.program = program;
            currentNodeId = program.entryNodeId();
        }

        public ProgramVmResult run(
                long gameTime,
                int fuel,
                ProgramExecutorLookup executors,
                @Nullable Object attachment
        ) {
            lastRunLogicalTick = gameTime;
            var result = runSlice(gameTime, fuel, executors, attachment);
            lastObservationResult = result;
            if (attachment instanceof ProgramExecutionFrame frame) {
                ProgramRunObservations.vmSlice(this, result, frame);
                frame.invocation().ifPresent(invocation -> invocation.reportRunResult(result));
            }
            return result;
        }

        private ProgramVmResult runSlice(
                long gameTime,
                int fuel,
                ProgramExecutorLookup executors,
                @Nullable Object attachment
        ) {
            if (fuel <= 0) throw new IllegalArgumentException("Program VM fuel must be positive");
            if (failed) {
                return new ProgramVmResult(
                        ProgramVmResult.Status.FAILED,
                        currentNodeId,
                        ProgramVmDiagnostic.EXECUTOR_ERROR
                );
            }
            if (completed) {
                return new ProgramVmResult(
                        ProgramVmResult.Status.COMPLETED,
                        currentNodeId,
                        ProgramVmDiagnostic.NONE
                );
            }
            if (gameTime < wakeAt) {
                return new ProgramVmResult(
                        ProgramVmResult.Status.SUSPENDED,
                        currentNodeId,
                        ProgramVmDiagnostic.NONE
                );
            }
            if (completeAfterWake) return complete();
            suspensionKind = org.academy.api.common.ability.program.ProgramRunStatus.WaitKind.NONE;
            suspensionSource = -1;

            var budget = new Fuel(fuel);
            var context = new ProgramVmContext(gameTime, variables, executorState, attachment);
            var trace = context.attachment(ProgramExecutionFrame.class)
                    .flatMap(ProgramExecutionFrame::invocation)
                    .map(ProgramInvocationContext::runTrace).orElse(null);
            while (true) {
                // Existing graphs sometimes wire the end of a foreach body back explicitly.
                // Treat that edge as the structured return instead of retaining a stale frame.
                while (!returnNodes.isEmpty() && returnNodes.peek() == currentNodeId) {
                    returnNodes.pop();
                }
                var node = program.nodes().get(currentNodeId);
                if (node == null) return fail(ProgramVmDiagnostic.INVALID_FLOW_OUTPUT);
                if (!budget.tryConsume()) {
                    suspensionKind = org.academy.api.common.ability.program.ProgramRunStatus.WaitKind.FUEL;
                    suspensionSource = currentNodeId;
                    return new ProgramVmResult(
                            ProgramVmResult.Status.FUEL_EXHAUSTED,
                            currentNodeId,
                            ProgramVmDiagnostic.NONE
                    );
                }
                if (node.role() == ProgramNodeRole.ENTRY) {
                    if (trace != null) trace.record(node.id(), "", "");
                    var target = soleFlowTarget(node);
                    if (target == null) return complete();
                    currentNodeId = target;
                    continue;
                }

                try {
                    var dataCache = new HashMap<CompiledProgram.OutputKey, ProgramValue<?>>();
                    var evaluated = new HashSet<Integer>();
                    var inputs = resolveInputs(
                            node,
                            context,
                            executors,
                            dataCache,
                            evaluated,
                            budget
                    );
                    var executor = executors.find(node.typeId());
                    if (executor == null) return fail(ProgramVmDiagnostic.MISSING_EXECUTOR);
                    context.enterNode(node.id(), node.typeId());
                    var step = execute(executor, node, context, inputs);
                    validateStep(node, step);
                    if (trace != null) trace.record(node, step.flowOutput(), inputs);
                    step.outputs().forEach((port, value) ->
                            latchedOutputs.put(new CompiledProgram.OutputKey(node.id(), port), value));
                    switch (step.directive()) {
                        case RETRY -> {
                            suspensionKind = org.academy.api.common.ability.program.ProgramRunStatus.WaitKind.RETRY;
                            suspensionSource = node.id();
                            wakeAt = gameTime + step.delayTicks();
                            return new ProgramVmResult(ProgramVmResult.Status.SUSPENDED,
                                    currentNodeId, ProgramVmDiagnostic.NONE);
                        }
                        case DATA -> {
                            return fail(ProgramVmDiagnostic.INVALID_FLOW_OUTPUT);
                        }
                        case STOP -> {
                            return complete();
                        }
                        case LOOP_CONTINUE -> {
                            var loop = nearestStructuredLoop();
                            if (loop == null) {
                                return fail(ProgramVmDiagnostic.INVALID_FLOW_OUTPUT);
                            }
                            returnNodes.pop();
                            currentNodeId = loop.id();
                        }
                        case LOOP_BREAK -> {
                            var loop = nearestStructuredLoop();
                            if (loop == null) {
                                return fail(ProgramVmDiagnostic.INVALID_FLOW_OUTPUT);
                            }
                            returnNodes.pop();
                            executorState.remove(ProgramVmContext.structuredLoopStateKey(loop.id()));
                            var target = program.flowTarget(loop.id(), "done");
                            if (target != null) {
                                currentNodeId = target;
                            } else if (returnNodes.isEmpty()) {
                                return complete();
                            } else {
                                currentNodeId = returnNodes.pop();
                            }
                        }
                        case CALL -> {
                            var target = program.flowTarget(node.id(), step.flowOutput());
                            if (target == null) {
                                currentNodeId = node.id();
                            } else {
                                returnNodes.push(node.id());
                                currentNodeId = target;
                            }
                        }
                        case CONTINUE, YIELD -> {
                            var target = program.flowTarget(node.id(), step.flowOutput());
                            var terminalYield = false;
                            if (target == null) {
                                if (returnNodes.isEmpty()) {
                                    if (step.directive() == ProgramNodeStep.Directive.CONTINUE) {
                                        return complete();
                                    }
                                    terminalYield = true;
                                } else {
                                    currentNodeId = returnNodes.pop();
                                }
                            } else {
                                currentNodeId = target;
                            }
                            if (step.directive() == ProgramNodeStep.Directive.YIELD) {
                                suspensionKind = org.academy.api.common.ability.program.ProgramRunStatus.WaitKind.WAIT;
                                suspensionSource = node.id();
                                wakeAt = gameTime + step.delayTicks();
                                completeAfterWake = terminalYield;
                                return new ProgramVmResult(
                                        ProgramVmResult.Status.SUSPENDED,
                                        currentNodeId,
                                        ProgramVmDiagnostic.NONE
                                );
                            }
                        }
                    }
                } catch (FuelExhausted exception) {
                    suspensionKind = org.academy.api.common.ability.program.ProgramRunStatus.WaitKind.FUEL;
                    suspensionSource = currentNodeId;
                    return new ProgramVmResult(
                            ProgramVmResult.Status.FUEL_EXHAUSTED,
                            currentNodeId,
                            ProgramVmDiagnostic.NONE
                    );
                } catch (ExecutionFailure exception) {
                    if (trace != null) trace.record(exception.nodeId < 0 ? currentNodeId : exception.nodeId,
                            "error", "");
                    failed = true;
                    return new ProgramVmResult(ProgramVmResult.Status.FAILED,
                            exception.nodeId < 0 ? currentNodeId : exception.nodeId,
                            exception.diagnostic, exception.port, exception.detail);
                } catch (RuntimeException exception) {
                    if (trace != null) trace.record(currentNodeId, "error", "");
                    return fail(ProgramVmDiagnostic.EXECUTOR_ERROR);
                }
            }
        }

        private CompiledProgram.CompiledNode nearestStructuredLoop() {
            if (returnNodes.isEmpty()) return null;
            var caller = program.nodes().get(returnNodes.peek());
            if (caller == null) return null;
            var body = caller.schema().output("body").orElse(null);
            var done = caller.schema().output("done").orElse(null);
            return body != null && done != null
                    && body.type().equals(ProgramValueTypes.FLOW)
                    && done.type().equals(ProgramValueTypes.FLOW)
                    ? caller : null;
        }

        public Map<String, ProgramValue<?>> variables() {
            return Map.copyOf(variables);
        }

        public int currentNodeId() {
            return currentNodeId;
        }

        public long wakeAt() {
            return wakeAt;
        }

        ProgramVmResult observationStatus() {
            return lastObservationResult == null ? new ProgramVmResult(ProgramVmResult.Status.FUEL_EXHAUSTED,
                    currentNodeId, ProgramVmDiagnostic.NONE) : lastObservationResult;
        }

        ObservationView observationView() {
            var kind = lastObservationResult != null && (lastObservationResult.status() == ProgramVmResult.Status.SUSPENDED
                    || lastObservationResult.status() == ProgramVmResult.Status.FUEL_EXHAUSTED)
                    ? suspensionKind : org.academy.api.common.ability.program.ProgramRunStatus.WaitKind.NONE;
            int source = kind == org.academy.api.common.ability.program.ProgramRunStatus.WaitKind.NONE ? -1 : suspensionSource;
            int actual = lastObservationResult != null && lastObservationResult.status() == ProgramVmResult.Status.FAILED
                    ? lastObservationResult.nodeId() : currentNodeId;
            var node = program.nodes().get(source >= 0 ? source : actual);
            return new ObservationView(source, node == null ? null : node.typeId(), lastRunLogicalTick,
                    kind == org.academy.api.common.ability.program.ProgramRunStatus.WaitKind.WAIT
                            || kind == org.academy.api.common.ability.program.ProgramRunStatus.WaitKind.RETRY ? wakeAt : -1, kind);
        }

        record ObservationView(int sourceNodeId, @Nullable Identifier nodeType, long logicalTick, long wakeAt,
                               org.academy.api.common.ability.program.ProgramRunStatus.WaitKind kind) {}

        private ProgramInputView resolveInputs(
                CompiledProgram.CompiledNode node,
                ProgramVmContext context,
                ProgramExecutorLookup executors,
                Map<CompiledProgram.OutputKey, ProgramValue<?>> dataCache,
                HashSet<Integer> evaluated,
                Fuel fuel
        ) {
            var values = new HashMap<String, List<ProgramValue<?>>>();
            for (var input : node.schema().inputs()) {
                if (input.type().equals(ProgramValueTypes.FLOW)) continue;
                var resolved = new ArrayList<ProgramValue<?>>();
                for (var output : program.inputs(node.id(), input.name())) {
                    try {
                        resolved.add(resolveValue(
                                output,
                                context,
                                executors,
                                dataCache,
                                evaluated,
                                fuel
                        ));
                    } catch (ExecutionFailure failure) {
                        if (failure.diagnostic == ProgramVmDiagnostic.MISSING_INPUT_VALUE
                                && failure.nodeId < 0) {
                            throw new ExecutionFailure(
                                    failure.diagnostic, -1, input.name(), null);
                        }
                        throw failure;
                    }
                }
                if (input.required() && resolved.isEmpty()) {
                    throw new ExecutionFailure(
                            ProgramVmDiagnostic.MISSING_INPUT_VALUE, node.id(), input.name(), null);
                }
                if (!resolved.isEmpty()) values.put(input.name(), resolved);
            }
            return new ProgramInputView(values);
        }

        private ProgramValue<?> resolveValue(
                CompiledProgram.OutputKey output,
                ProgramVmContext context,
                ProgramExecutorLookup executors,
                Map<CompiledProgram.OutputKey, ProgramValue<?>> dataCache,
                HashSet<Integer> evaluated,
                Fuel fuel
        ) {
            var latched = latchedOutputs.get(output);
            if (latched != null) return latched;
            var cached = dataCache.get(output);
            if (cached != null) return cached;
            var source = program.nodes().get(output.nodeId());
            if (source == null || source.role().requiresFlow() || source.role() == ProgramNodeRole.ENTRY) {
                throw new ExecutionFailure(
                        ProgramVmDiagnostic.MISSING_INPUT_VALUE, -1, output.port(), null);
            }
            if (!evaluated.contains(source.id())) {
                if (!fuel.tryConsume()) throw new FuelExhausted();
                var executor = executors.find(source.typeId());
                if (executor == null) throw new ExecutionFailure(ProgramVmDiagnostic.MISSING_EXECUTOR);
                var inputs = resolveInputs(source, context, executors, dataCache, evaluated, fuel);
                context.enterNode(source.id(), source.typeId());
                var step = execute(executor, source, context, inputs);
                if (step.directive() != ProgramNodeStep.Directive.DATA) {
                    throw new ExecutionFailure(ProgramVmDiagnostic.INVALID_FLOW_OUTPUT);
                }
                validateStep(source, step);
                step.outputs().forEach((port, value) ->
                        dataCache.put(new CompiledProgram.OutputKey(source.id(), port), value));
                evaluated.add(source.id());
            }
            var result = dataCache.get(output);
            if (result == null) {
                throw new ExecutionFailure(
                        ProgramVmDiagnostic.MISSING_INPUT_VALUE, output.nodeId(), output.port(), null);
            }
            return result;
        }

        private Integer soleFlowTarget(CompiledProgram.CompiledNode node) {
            Integer target = null;
            for (var output : node.schema().outputs()) {
                if (!output.type().equals(ProgramValueTypes.FLOW)) continue;
                var candidate = program.flowTarget(node.id(), output.name());
                if (candidate == null) continue;
                if (target != null) return null;
                target = candidate;
            }
            return target;
        }

        private void validateStep(
                CompiledProgram.CompiledNode node,
                ProgramNodeStep step
        ) {
            for (var entry : step.outputs().entrySet()) {
                var port = node.schema().output(entry.getKey()).orElseThrow(() ->
                        new ExecutionFailure(ProgramVmDiagnostic.INVALID_OUTPUT));
                if (port.type().equals(ProgramValueTypes.FLOW)
                        || !ProgramValueTypes.canConnect(entry.getValue().type(), port.type())) {
                    throw new ExecutionFailure(ProgramVmDiagnostic.INVALID_OUTPUT);
                }
            }
            if (step.directive() == ProgramNodeStep.Directive.CONTINUE
                    || step.directive() == ProgramNodeStep.Directive.CALL
                    || step.directive() == ProgramNodeStep.Directive.YIELD) {
                var flow = step.flowOutput();
                var port = flow == null ? null : node.schema().output(flow).orElse(null);
                if (port == null || !port.type().equals(ProgramValueTypes.FLOW)) {
                    throw new ExecutionFailure(ProgramVmDiagnostic.INVALID_FLOW_OUTPUT);
                }
            }
        }

        private ProgramVmResult complete() {
            completed = true;
            return new ProgramVmResult(
                    ProgramVmResult.Status.COMPLETED,
                    currentNodeId,
                    ProgramVmDiagnostic.NONE
            );
        }

        private ProgramVmResult fail(ProgramVmDiagnostic diagnostic) {
            failed = true;
            return new ProgramVmResult(ProgramVmResult.Status.FAILED, currentNodeId, diagnostic);
        }
    }

    private static <C> ProgramNodeStep execute(
            ProgramNodeExecutor<C> executor,
            CompiledProgram.CompiledNode node,
            ProgramVmContext context,
            ProgramInputView inputs
    ) {
        @SuppressWarnings("unchecked") var configuration = (C) node.configuration();
        @SuppressWarnings("unchecked") var type =
                (org.academy.api.common.ability.program.ProgramNodeType<C>) node.type();
        try {
            return executor.execute(
                    context,
                    type.resolveConfiguration(configuration, inputs),
                    inputs
            );
        } catch (ExecutionFailure failure) {
            throw failure;
        } catch (ProgramActionFailure failure) {
            throw new ExecutionFailure(
                    failure.diagnostic(), node.id(), null, failure.detail());
        } catch (RuntimeException exception) {
            var diagnostic = AbilityProgramManager.actionDiagnostic(exception);
            if (diagnostic == ProgramVmDiagnostic.ACTION_REJECTED) {
                var programId = context.attachment(ProgramExecutionFrame.class)
                        .flatMap(ProgramExecutionFrame::invocation)
                        .map(ProgramInvocationContext::programId).orElse(null);
                var suppressed = recordFailure(programId, node.id(), node.typeId(),
                        exception, context.gameTime());
                if (suppressed >= 0) {
                    AcademyCraft.LOGGER.warn("Program node {} ({}) failed in program {} ({} repeats suppressed)",
                            node.id(), node.typeId(), programId, suppressed, exception);
                }
            }
            throw new ExecutionFailure(diagnostic == ProgramVmDiagnostic.ACTION_REJECTED
                    ? ProgramVmDiagnostic.EXECUTOR_ERROR : diagnostic, node.id());
        }
    }

    static synchronized long recordFailure(@Nullable UUID programId, int nodeId, Identifier nodeType,
                                          Throwable failure, long tick) {
        var key = FailureKey.from(programId, nodeId, nodeType, failure);
        var window = FAILURE_LOGS.get(key);
        if (window == null) {
            if (FAILURE_LOGS.size() >= MAX_FAILURE_LOG_KEYS) {
                FAILURE_LOGS.remove(FAILURE_LOGS.keySet().iterator().next());
            }
            FAILURE_LOGS.put(key, new FailureWindow(tick));
            return 0;
        }
        if (tick < window.lastLoggedTick || tick - window.lastLoggedTick >= FAILURE_LOG_INTERVAL_TICKS) {
            var suppressed = window.suppressed;
            window.lastLoggedTick = tick;
            window.suppressed = 0;
            return suppressed;
        }
        window.suppressed++;
        return -1;
    }

    private record FailureKey(@Nullable UUID programId, int nodeId, Identifier nodeType,
                              Class<? extends Throwable> exceptionType, String reason) {
        private static FailureKey from(@Nullable UUID programId, int nodeId, Identifier nodeType,
                                       Throwable failure) {
            var root = failure;
            String reason = null;
            var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
            for (var current = failure; current != null && seen.add(current); current = current.getCause()) {
                root = current;
                if (reason == null && current instanceof ProgramActionRejection rejection) {
                    reason = rejection.reason().name();
                }
            }
            if (reason == null) {
                var message = root.getMessage();
                reason = message == null ? "" : message.substring(0,
                        Math.min(message.length(), MAX_FAILURE_REASON_LENGTH)).strip().toLowerCase(Locale.ROOT);
            }
            return new FailureKey(programId, nodeId, nodeType, root.getClass(), reason);
        }
    }

    private static final class FailureWindow {
        private long lastLoggedTick;
        private long suppressed;

        private FailureWindow(long lastLoggedTick) {
            this.lastLoggedTick = lastLoggedTick;
        }
    }

    private static final class Fuel {
        private int remaining;

        private Fuel(int remaining) {
            this.remaining = remaining;
        }

        private boolean tryConsume() {
            if (remaining <= 0) return false;
            remaining--;
            return true;
        }
    }

    private static final class FuelExhausted extends RuntimeException {
    }

    private static final class ExecutionFailure extends RuntimeException {
        private final ProgramVmDiagnostic diagnostic;
        private final int nodeId;
        private final @Nullable String port;
        private final @Nullable String detail;

        private ExecutionFailure(ProgramVmDiagnostic diagnostic) {
            this(diagnostic, -1, null, null);
        }

        private ExecutionFailure(ProgramVmDiagnostic diagnostic, int nodeId) {
            this(diagnostic, nodeId, null, null);
        }

        private ExecutionFailure(
                ProgramVmDiagnostic diagnostic,
                int nodeId,
                @Nullable String port,
                @Nullable String detail
        ) {
            this.diagnostic = diagnostic;
            this.nodeId = nodeId;
            this.port = port;
            this.detail = detail;
        }
    }
}
