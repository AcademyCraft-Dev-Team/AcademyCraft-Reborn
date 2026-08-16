package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.api.common.ability.program.ProgramValue;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Resumable, fuel-metered interpreter. Exhausting a time slice never blocks the server tick.
 */
public final class ProgramVm {
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
        private boolean completed;
        private boolean failed;

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

            var budget = new Fuel(fuel);
            var context = new ProgramVmContext(gameTime, variables, executorState, attachment);
            while (true) {
                // Existing graphs sometimes wire the end of a foreach body back explicitly.
                // Treat that edge as the structured return instead of retaining a stale frame.
                while (!returnNodes.isEmpty() && returnNodes.peek() == currentNodeId) {
                    returnNodes.pop();
                }
                var node = program.nodes().get(currentNodeId);
                if (node == null) return fail(ProgramVmDiagnostic.INVALID_FLOW_OUTPUT);
                if (!budget.tryConsume()) {
                    return new ProgramVmResult(
                            ProgramVmResult.Status.FUEL_EXHAUSTED,
                            currentNodeId,
                            ProgramVmDiagnostic.NONE
                    );
                }
                if (node.role() == ProgramNodeRole.ENTRY) {
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
                    step.outputs().forEach((port, value) ->
                            latchedOutputs.put(new CompiledProgram.OutputKey(node.id(), port), value));
                    switch (step.directive()) {
                        case DATA -> {
                            return fail(ProgramVmDiagnostic.INVALID_FLOW_OUTPUT);
                        }
                        case STOP -> {
                            return complete();
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
                            if (target == null) {
                                if (returnNodes.isEmpty()) return complete();
                                currentNodeId = returnNodes.pop();
                            } else {
                                currentNodeId = target;
                            }
                            if (step.directive() == ProgramNodeStep.Directive.YIELD) {
                                wakeAt = gameTime + step.delayTicks();
                                return new ProgramVmResult(
                                        ProgramVmResult.Status.SUSPENDED,
                                        currentNodeId,
                                        ProgramVmDiagnostic.NONE
                                );
                            }
                        }
                    }
                } catch (FuelExhausted exception) {
                    return new ProgramVmResult(
                            ProgramVmResult.Status.FUEL_EXHAUSTED,
                            currentNodeId,
                            ProgramVmDiagnostic.NONE
                    );
                } catch (ExecutionFailure exception) {
                    return fail(exception.diagnostic);
                } catch (RuntimeException exception) {
                    return fail(ProgramVmDiagnostic.EXECUTOR_ERROR);
                }
            }
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
                    resolved.add(resolveValue(
                            output,
                            context,
                            executors,
                            dataCache,
                            evaluated,
                            fuel
                    ));
                }
                if (input.required() && resolved.isEmpty()) {
                    throw new ExecutionFailure(ProgramVmDiagnostic.MISSING_INPUT_VALUE);
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
                throw new ExecutionFailure(ProgramVmDiagnostic.MISSING_INPUT_VALUE);
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
            if (result == null) throw new ExecutionFailure(ProgramVmDiagnostic.MISSING_INPUT_VALUE);
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
        @SuppressWarnings("unchecked")
        var configuration = (C) node.configuration();
        return executor.execute(context, configuration, inputs);
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

        private ExecutionFailure(ProgramVmDiagnostic diagnostic) {
            this.diagnostic = diagnostic;
        }
    }
}
