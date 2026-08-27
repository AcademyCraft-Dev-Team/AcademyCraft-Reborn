package org.academy.internal.common.ability.mentalout.precision;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.program.*;

import java.util.*;

/**
 * Shared-VM execution gateway for Precision Operation.
 *
 * <p>The native path lets common and category-specific nodes participate in the same data and
 * control-flow graph. The replay path remains available only for compatibility tests and migrated
 * callers while the old planner is retired.</p>
 */
final class PrecisionProgramExecutionBridge {
    private static final int MAX_FUEL = PrecisionGraph.MAX_NODES * PrecisionGraph.MAX_NODES + 1;
    private static final Map<Identifier, ProgramNodeExecutor<?>> PRECISION_EXECUTORS =
            createPrecisionExecutors();

    private PrecisionProgramExecutionBridge() {
    }

    static ProgramExecutorLookup categoryExecutors() {
        return PRECISION_EXECUTORS::get;
    }

    static NativeResult executeNative(
            CompiledProgram program,
            long gameTime,
            PrecisionProgramRuntimeView runtimeView,
            ProgramTargetResolver targetResolver,
            ProgramActionTransaction transaction,
            NativeNodeHandler nodeHandler
    ) {
        var environment = new NativeEnvironment(runtimeView, targetResolver, nodeHandler);
        var result = new ProgramVm.Session(program).run(
                gameTime,
                MAX_FUEL,
                AbilityProgramDefinitions.mentalout().executors(),
                new ProgramExecutionFrame(transaction, environment)
        );
        if (result.status() == ProgramVmResult.Status.COMPLETED) {
            return NativeResult.success();
        }
        var diagnostic = result.status() == ProgramVmResult.Status.FUEL_EXHAUSTED
                ? PrecisionGraph.Diagnostic.PLANNING_BUDGET_EXHAUSTED
                : PrecisionGraph.Diagnostic.ADAPTER_ERROR;
        return NativeResult.failure(diagnostic, result.nodeId(), result.diagnostic());
    }

    static ReplayResult replay(
            CompiledProgram program,
            Map<Integer, Object> values,
            List<Integer> flowTrace,
            long gameTime,
            PrecisionProgramRuntimeView runtimeView,
            ProgramActionTransaction transaction,
            ActionFactory actionFactory
    ) {
        return replay(
                program,
                values,
                flowTrace,
                gameTime,
                runtimeView,
                UnavailableTargetResolver.INSTANCE,
                transaction,
                actionFactory
        );
    }

    static ReplayResult replay(
            CompiledProgram program,
            Map<Integer, Object> values,
            List<Integer> flowTrace,
            long gameTime,
            PrecisionProgramRuntimeView runtimeView,
            ProgramTargetResolver targetResolver,
            ProgramActionTransaction transaction,
            ActionFactory actionFactory
    ) {
        var trace = new Trace(values, flowTrace, runtimeView, targetResolver, actionFactory);
        var frame = new ProgramExecutionFrame(transaction, trace);
        var result = new ProgramVm.Session(program).run(
                gameTime,
                MAX_FUEL,
                AbilityProgramDefinitions.mentalout().executors(),
                frame
        );
        if (result.status() == ProgramVmResult.Status.COMPLETED && trace.complete()) {
            return ReplayResult.success();
        }
        var diagnostic = result.status() == ProgramVmResult.Status.FUEL_EXHAUSTED
                ? PrecisionGraph.Diagnostic.PLANNING_BUDGET_EXHAUSTED
                : PrecisionGraph.Diagnostic.ADAPTER_ERROR;
        return ReplayResult.failure(diagnostic, result.nodeId(), result.diagnostic());
    }

    private static Map<Identifier, ProgramNodeExecutor<?>> createPrecisionExecutors() {
        var result = new HashMap<Identifier, ProgramNodeExecutor<?>>();
        for (var kind : PrecisionGraph.NodeKind.values()) {
            result.put(PrecisionProgramNodeIds.id(kind), new TraceExecutor(kind));
        }
        return Map.copyOf(result);
    }

    private static ProgramValueType valueType(PrecisionGraph.PortType type) {
        return switch (type) {
            case ENTITY -> ProgramValueTypes.ENTITY_REFERENCE;
            case ENTITY_SET -> ProgramValueTypes.ENTITY_SET;
            case DESTINATION -> ProgramValueTypes.CONTROL_DESTINATION;
            case FLOW -> ProgramValueTypes.FLOW;
            case DIRECTION -> ProgramValueTypes.DIRECTION;
        };
    }

    record ReplayResult(
            boolean valid,
            PrecisionGraph.Diagnostic diagnostic,
            int nodeId,
            ProgramVmDiagnostic vmDiagnostic
    ) {
        private static ReplayResult success() {
            return new ReplayResult(
                    true,
                    PrecisionGraph.Diagnostic.OK,
                    -1,
                    ProgramVmDiagnostic.NONE
            );
        }

        private static ReplayResult failure(
                PrecisionGraph.Diagnostic diagnostic,
                int nodeId,
                ProgramVmDiagnostic vmDiagnostic
        ) {
            return new ReplayResult(false, diagnostic, nodeId, vmDiagnostic);
        }
    }

    record NativeResult(
            boolean valid,
            PrecisionGraph.Diagnostic diagnostic,
            int nodeId,
            ProgramVmDiagnostic vmDiagnostic
    ) {
        private static NativeResult success() {
            return new NativeResult(
                    true,
                    PrecisionGraph.Diagnostic.OK,
                    -1,
                    ProgramVmDiagnostic.NONE
            );
        }

        private static NativeResult failure(
                PrecisionGraph.Diagnostic diagnostic,
                int nodeId,
                ProgramVmDiagnostic vmDiagnostic
        ) {
            return new NativeResult(false, diagnostic, nodeId, vmDiagnostic);
        }
    }

    @FunctionalInterface
    interface ActionFactory {
        ProgramActionTransaction.ProgramAction create(int nodeId, ProgramInputView inputs);
    }

    @FunctionalInterface
    interface NativeNodeHandler {
        ProgramNodeStep execute(
                ProgramVmContext context,
                PrecisionGraph.NodeKind kind,
                PrecisionProgramNodeCatalog.PrecisionConfiguration configuration,
                ProgramInputView inputs
        );
    }

    private record TraceExecutor(PrecisionGraph.NodeKind kind)
                implements ProgramNodeExecutor<PrecisionProgramNodeCatalog.PrecisionConfiguration> {

        @Override
            public ProgramNodeStep execute(
                    ProgramVmContext context,
                    PrecisionProgramNodeCatalog.PrecisionConfiguration configuration,
                    ProgramInputView inputs
            ) {
                var frame = context.attachment(ProgramExecutionFrame.class).orElseThrow();
                var nativeEnvironment = frame.environment(NativeEnvironment.class).orElse(null);
                if (nativeEnvironment != null) {
                    if (kind == PrecisionGraph.NodeKind.CASTER) {
                        return dataOutput(kind, nativeEnvironment.runtimeView.caster());
                    }
                    if (isNativeCollection(kind)) {
                        return executeNativeCollection(kind, configuration, inputs);
                    }
                    if (isNativeWorldData(kind)) {
                        return executeNativeWorldData(
                                kind, configuration, inputs, nativeEnvironment.runtimeView);
                    }
                    if (kind.isConditionalBranch()) {
                        var selected = executeNativeBranch(
                                kind, configuration, inputs, nativeEnvironment.runtimeView);
                        return ProgramNodeStep.next(Boolean.toString(selected));
                    }
                    return nativeEnvironment.nodeHandler.execute(
                            context, kind, configuration, inputs);
                }
                var trace = frame.environment(Trace.class).orElseThrow();
                var nodeId = context.nodeId();
                if (kind == PrecisionGraph.NodeKind.CASTER) {
                    return dataOutput(kind, trace.runtimeView.caster());
                }
                if (isNativeCollection(kind)) {
                    return executeNativeCollection(kind, configuration, inputs);
                }
                if (isNativeWorldData(kind)) {
                    return executeNativeWorldData(kind, configuration, inputs, trace.runtimeView);
                }
                if (kind.isAction()) {
                    trace.visit(nodeId);
                    if (kind.isConditionalBranch()) {
                        var selected = executeNativeBranch(
                                kind,
                                configuration,
                                inputs,
                                trace.runtimeView
                        );
                        return ProgramNodeStep.next(Boolean.toString(selected));
                    }
                    if (!trace.values.containsKey(nodeId)) {
                        throw new IllegalStateException("Missing action trace for node " + nodeId);
                    }
                    frame.stage(context, trace.action(nodeId, inputs));
                    return ProgramNodeStep.next("flow");
                }

                if (kind.outputDefinitions().size() != 1 || !trace.values.containsKey(nodeId)) {
                    throw new IllegalStateException("Missing data trace for node " + nodeId);
                }
                var value = trace.values.get(nodeId);
                if (value == null) return ProgramNodeStep.data(Map.of());
                var output = kind.outputDefinitions().getFirst();
                return ProgramNodeStep.data(Map.of(
                        output.key(),
                        new ProgramValue<>(valueType(output.type()), value)
                ));
            }

            private static boolean isNativeCollection(PrecisionGraph.NodeKind kind) {
                return switch (kind) {
                    case ENTITY_TO_SET, UNION, INTERSECTION, SUBTRACT_SET, EXCLUDE, LIMIT -> true;
                    default -> false;
                };
            }

            private static boolean isNativeWorldData(PrecisionGraph.NodeKind kind) {
                return switch (kind) {
                    case NEARBY_ENTITIES, NEARBY_ALL_ENTITIES, NEARBY_ITEMS, NEARBY_PROJECTILES,
                         ALIVE, DISTANCE, ALLIES, ENEMIES, TYPE_FILTER, HEALTH_FILTER,
                         HEALTH_BELOW, HAS_TARGET, VISIBLE_FROM, NEAREST, FARTHEST,
                         LOWEST_HEALTH, HIGHEST_HEALTH, SORT_BY_DISTANCE, RANDOM -> true;
                    default -> false;
                };
            }

            private static ProgramNodeStep executeNativeCollection(
                    PrecisionGraph.NodeKind kind,
                    PrecisionProgramNodeCatalog.PrecisionConfiguration configuration,
                    ProgramInputView inputs
            ) {
                var result = switch (kind) {
                    case ENTITY_TO_SET -> List.of(input(
                            inputs, "entity", ProgramValueTypes.ENTITY_REFERENCE));
                    case UNION -> union(collection(inputs, "left"), collection(inputs, "right"));
                    case INTERSECTION -> {
                        var right = new HashSet<>(collection(inputs, "right"));
                        yield collection(inputs, "left").stream().filter(right::contains).toList();
                    }
                    case SUBTRACT_SET -> {
                        var right = new HashSet<>(collection(inputs, "right"));
                        yield collection(inputs, "left").stream()
                                .filter(value -> !right.contains(value)).toList();
                    }
                    case EXCLUDE -> {
                        var excluded = input(inputs, "excluded", ProgramValueTypes.ENTITY_REFERENCE);
                        yield collection(inputs, "entities").stream()
                                .filter(value -> value != excluded).toList();
                    }
                    case LIMIT -> collection(inputs, "entities").stream()
                            .limit((int) configuration.parameter()).toList();
                    default -> throw new IllegalStateException("Unsupported native collection node " + kind);
                };
                return dataOutput(kind, result);
            }

            private static ProgramNodeStep executeNativeWorldData(
                    PrecisionGraph.NodeKind kind,
                    PrecisionProgramNodeCatalog.PrecisionConfiguration configuration,
                    ProgramInputView inputs,
                    PrecisionProgramRuntimeView view
            ) {
                var parameter = configuration.parameter();
                return switch (kind) {
                    case NEARBY_ENTITIES -> dataOutput(kind, view.nearbyLiving(parameter));
                    case NEARBY_ALL_ENTITIES -> dataOutput(kind, view.nearbyEntities(parameter));
                    case NEARBY_ITEMS -> dataOutput(kind, view.nearbyItems(parameter));
                    case NEARBY_PROJECTILES -> dataOutput(kind, view.nearbyProjectiles(parameter));
                    case ALIVE -> dataOutput(kind, collection(inputs, "entities").stream()
                            .filter(view::alive).toList());
                    case DISTANCE -> dataOutput(kind, collection(inputs, "entities").stream()
                            .filter(value -> view.withinDistance(value, parameter)).toList());
                    case ALLIES -> dataOutput(kind, collection(inputs, "entities").stream()
                            .filter(view::ally).toList());
                    case ENEMIES -> dataOutput(kind, collection(inputs, "entities").stream()
                            .filter(value -> value != view.caster() && !view.ally(value)).toList());
                    case TYPE_FILTER -> dataOutput(kind, collection(inputs, "entities").stream()
                            .filter(value -> view.typeMatches((int) parameter, value)).toList());
                    case HEALTH_FILTER -> dataOutput(kind, collection(inputs, "entities").stream()
                            .filter(value -> view.healthPercent(value) >= parameter).toList());
                    case HEALTH_BELOW -> dataOutput(kind, collection(inputs, "entities").stream()
                            .filter(value -> view.healthPercent(value) <= parameter).toList());
                    case HAS_TARGET -> dataOutput(kind, collection(inputs, "entities").stream()
                            .filter(view::hasTarget).toList());
                    case VISIBLE_FROM -> {
                        var observer = input(inputs, "observer", ProgramValueTypes.ENTITY_REFERENCE);
                        yield dataOutput(kind, collection(inputs, "entities").stream()
                                .filter(value -> view.visibleFrom(observer, value)).toList());
                    }
                    case NEAREST -> selectedOutput(kind, collection(inputs, "entities").stream()
                            .min(distanceComparator(view)).orElse(null));
                    case FARTHEST -> selectedOutput(kind, collection(inputs, "entities").stream()
                            .min(farthestComparator(view)).orElse(null));
                    case LOWEST_HEALTH -> selectedOutput(kind, collection(inputs, "entities").stream()
                            .filter(value -> !Double.isNaN(view.sortableHealthPercent(value)))
                            .min(healthComparator(view, false)).orElse(null));
                    case HIGHEST_HEALTH -> selectedOutput(kind, collection(inputs, "entities").stream()
                            .filter(value -> !Double.isNaN(view.sortableHealthPercent(value)))
                            .min(healthComparator(view, true)).orElse(null));
                    case SORT_BY_DISTANCE -> {
                        var comparator = parameter == 0.0
                                ? distanceComparator(view)
                                : farthestComparator(view);
                        yield dataOutput(kind, collection(inputs, "entities").stream()
                                .sorted(comparator).toList());
                    }
                    case RANDOM -> {
                        var values = collection(inputs, "entities");
                        yield selectedOutput(kind, values.isEmpty()
                                ? null : values.get(view.randomIndex(values.size())));
                    }
                    default -> throw new IllegalStateException("Unsupported native world node " + kind);
                };
            }

            private static boolean executeNativeBranch(
                    PrecisionGraph.NodeKind kind,
                    PrecisionProgramNodeCatalog.PrecisionConfiguration configuration,
                    ProgramInputView inputs,
                    PrecisionProgramRuntimeView view
            ) {
                var subject = input(inputs, "subject", ProgramValueTypes.ENTITY_REFERENCE);
                return switch (kind) {
                    case HEALTH_RATIO_BRANCH -> view.healthPercent(subject) <= configuration.parameter();
                    case DISTANCE_BRANCH -> view.withinDistance(subject, configuration.parameter());
                    case ENTITY_TYPE_BRANCH -> view.typeMatches((int) configuration.parameter(), subject);
                    case STATUS_EFFECT_BRANCH -> view.hasStatusEffect(subject);
                    default -> throw new IllegalStateException("Unsupported native branch " + kind);
                };
            }

            private static Object input(
                    ProgramInputView inputs,
                    String port,
                    ProgramValueType type
            ) {
                return inputs.requireCompatible(port, type).value();
            }

            private static List<?> collection(ProgramInputView inputs, String port) {
                var value = input(inputs, port, ProgramValueTypes.ENTITY_SET);
                if (!(value instanceof List<?> list)) {
                    throw new IllegalArgumentException("Entity-set input is not a list");
                }
                return list;
            }

            private static List<?> union(List<?> left, List<?> right) {
                var result = new LinkedHashSet<>();
                result.addAll(left);
                result.addAll(right);
                return List.copyOf(result);
            }

            private static Comparator<Object> distanceComparator(PrecisionProgramRuntimeView view) {
                return Comparator.comparingDouble(view::distanceSqr).thenComparing(view::stableKey);
            }

            private static Comparator<Object> farthestComparator(PrecisionProgramRuntimeView view) {
                return Comparator.comparingDouble((Object value) -> -view.distanceSqr(value))
                        .thenComparing(view::stableKey);
            }

            private static Comparator<Object> healthComparator(
                    PrecisionProgramRuntimeView view,
                    boolean highest
            ) {
                return Comparator.comparingDouble((Object value) ->
                                (highest ? -1.0 : 1.0) * view.sortableHealthPercent(value))
                        .thenComparingDouble(view::distanceSqr)
                        .thenComparing(view::stableKey);
            }

            private static ProgramNodeStep selectedOutput(
                    PrecisionGraph.NodeKind kind,
                    Object value
            ) {
                return value == null ? ProgramNodeStep.data(Map.of()) : dataOutput(kind, value);
            }

            private static ProgramNodeStep dataOutput(
                    PrecisionGraph.NodeKind kind,
                    Object value
            ) {
                var output = kind.outputDefinitions().getFirst();
                return ProgramNodeStep.data(Map.of(
                        output.key(),
                        new ProgramValue<>(valueType(output.type()), value)
                ));
            }
        }

    private record NativeEnvironment(PrecisionProgramRuntimeView runtimeView, ProgramTargetResolver targetResolver,
                                     NativeNodeHandler nodeHandler) implements ProgramTargetResolver {

        @Override
            public Object caster() {
                return targetResolver.caster();
            }

            @Override
            public Optional<Object> lookTarget() {
                return targetResolver.lookTarget();
            }

            @Override
            public Optional<ProgramBlockPosition> lookBlockTarget() {
                return targetResolver.lookBlockTarget();
            }

            @Override
            public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
                return targetResolver.positionOf(entityReference);
            }

            @Override
            public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
                return targetResolver.lookDirectionOf(entityReference);
            }

            @Override
            public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
                return targetResolver.entitiesAround(center, radius);
            }

            @Override
            public Optional<ProgramBlockPosition> raycastBlock(
                    ProgramWorldPosition origin,
                    ProgramDirection direction,
                    double maximumDistance
            ) {
                return targetResolver.raycastBlock(origin, direction, maximumDistance);
            }

            @Override
            public Optional<Object> raycastEntity(
                    ProgramWorldPosition origin,
                    ProgramDirection direction,
                    double maximumDistance
            ) {
                return targetResolver.raycastEntity(origin, direction, maximumDistance);
            }
        }

    private static final class Trace implements ProgramTargetResolver {
        private final Map<Integer, Object> values;
        private final List<Integer> flowTrace;
        private final PrecisionProgramRuntimeView runtimeView;
        private final ProgramTargetResolver targetResolver;
        private final ActionFactory actionFactory;
        private int flowIndex;

        private Trace(
                Map<Integer, Object> values,
                List<Integer> flowTrace,
                PrecisionProgramRuntimeView runtimeView,
                ProgramTargetResolver targetResolver,
                ActionFactory actionFactory
        ) {
            this.values = Collections.unmodifiableMap(new HashMap<>(values));
            this.flowTrace = List.copyOf(flowTrace);
            this.runtimeView = runtimeView;
            this.targetResolver = targetResolver;
            this.actionFactory = actionFactory;
        }

        @Override
        public Object caster() {
            return targetResolver.caster();
        }

        @Override
        public Optional<Object> lookTarget() {
            return targetResolver.lookTarget();
        }

        @Override
        public Optional<ProgramBlockPosition> lookBlockTarget() {
            return targetResolver.lookBlockTarget();
        }

        @Override
        public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
            return targetResolver.positionOf(entityReference);
        }

        @Override
        public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
            return targetResolver.lookDirectionOf(entityReference);
        }

        @Override
        public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
            return targetResolver.entitiesAround(center, radius);
        }

        @Override
        public Optional<ProgramBlockPosition> raycastBlock(
                ProgramWorldPosition origin,
                ProgramDirection direction,
                double maximumDistance
        ) {
            return targetResolver.raycastBlock(origin, direction, maximumDistance);
        }

        @Override
        public Optional<Object> raycastEntity(
                ProgramWorldPosition origin,
                ProgramDirection direction,
                double maximumDistance
        ) {
            return targetResolver.raycastEntity(origin, direction, maximumDistance);
        }

        private void visit(int nodeId) {
            if (flowIndex >= flowTrace.size() || flowTrace.get(flowIndex) != nodeId) {
                throw new IllegalStateException("Precision flow trace diverged at node " + nodeId);
            }
            flowIndex++;
        }

        private boolean complete() {
            return flowIndex == flowTrace.size();
        }

        private ProgramActionTransaction.ProgramAction action(
                int nodeId,
                ProgramInputView inputs
        ) {
            var action = actionFactory.create(nodeId, inputs);
            if (action == null) {
                throw new IllegalStateException("Missing staged action for node " + nodeId);
            }
            return action;
        }
    }

    private enum UnavailableTargetResolver implements ProgramTargetResolver {
        INSTANCE;

        @Override
        public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
            return Optional.empty();
        }

        @Override
        public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
            return Optional.empty();
        }

        @Override
        public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
            return List.of();
        }

        @Override
        public Optional<ProgramBlockPosition> raycastBlock(
                ProgramWorldPosition origin,
                ProgramDirection direction,
                double maximumDistance
        ) {
            return Optional.empty();
        }
    }
}
