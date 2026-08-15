package org.academy.internal.common.ability.mentalout.precision;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.PrecisionProgramCompilation;
import org.academy.internal.common.ability.program.PrecisionProgramNodeIds;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramExecutionFrame;
import org.academy.internal.common.ability.program.ProgramNodeStep;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrecisionProgramExecutionBridgeTest {
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");

    @Test
    void replaysDataDependenciesAndSelectedFlowThroughSharedVm() {
        var compiled = PrecisionProgramCompilation.compile(branchGraph());
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var transaction = new ProgramActionTransaction();
        var staged = new ArrayList<Integer>();
        var caster = new Object();
        var view = new FakeView(caster);
        view.health.put(caster, 25.0);

        var replay = PrecisionProgramExecutionBridge.replay(
                compiled.program(),
                Map.of(3, true),
                List.of(2, 3),
                100L,
                view,
                transaction,
                (nodeId, _) -> {
                    staged.add(nodeId);
                    return () -> ProgramActionTransaction.Undo.NONE;
                }
        );

        assertTrue(replay.valid());
        assertEquals(PrecisionGraph.Diagnostic.OK, replay.diagnostic());
        assertEquals(List.of(3), staged);
        assertEquals(1, transaction.size());
    }

    @Test
    void rejectsPlannerAndVmFlowDivergence() {
        var compiled = PrecisionProgramCompilation.compile(branchGraph());
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var transaction = new ProgramActionTransaction();
        var caster = new Object();
        var view = new FakeView(caster);
        view.health.put(caster, 25.0);

        var replay = PrecisionProgramExecutionBridge.replay(
                compiled.program(),
                Map.of(3, true),
                List.of(3, 2),
                100L,
                view,
                transaction,
                (_, _) -> () -> ProgramActionTransaction.Undo.NONE
        );

        assertFalse(replay.valid());
        assertEquals(PrecisionGraph.Diagnostic.ADAPTER_ERROR, replay.diagnostic());
        assertEquals(2, replay.nodeId());
        assertEquals(0, transaction.size());
    }

    @Test
    void rejectsMissingNativeActionBeforeTransactionCommit() {
        var compiled = PrecisionProgramCompilation.compile(branchGraph());
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var transaction = new ProgramActionTransaction();
        var caster = new Object();
        var view = new FakeView(caster);
        view.health.put(caster, 25.0);

        var replay = PrecisionProgramExecutionBridge.replay(
                compiled.program(),
                Map.of(3, true),
                List.of(2, 3),
                100L,
                view,
                transaction,
                (_, _) -> null
        );

        assertFalse(replay.valid());
        assertEquals(PrecisionGraph.Diagnostic.ADAPTER_ERROR, replay.diagnostic());
        assertEquals(3, replay.nodeId());
        assertEquals(0, transaction.size());
        assertEquals(ProgramActionTransaction.State.OPEN, transaction.state());
    }

    @Test
    void computesNativeCasterAndCollectionChainWithoutPlannerValues() {
        var compiled = PrecisionProgramCompilation.compile(collectionGraph());
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var transaction = new ProgramActionTransaction();
        var caster = new Object();
        var view = new FakeView(caster);
        var subjects = new ArrayList<Object>();

        var replay = PrecisionProgramExecutionBridge.replay(
                compiled.program(),
                Map.of(4, true),
                List.of(4),
                100L,
                view,
                transaction,
                (_, inputs) -> {
                    var value = inputs.requireCompatible(
                            "subjects", ProgramValueTypes.ENTITY_SET).value();
                    subjects.addAll((List<?>) value);
                    return () -> ProgramActionTransaction.Undo.NONE;
                }
        );

        assertTrue(replay.valid());
        assertEquals(List.of(caster), subjects);
        assertEquals(1, transaction.size());
    }

    @Test
    void computesNativeWorldFiltersSelectionAndBranchWithoutPlannerValues() {
        var compiled = PrecisionProgramCompilation.compile(worldQueryGraph());
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var transaction = new ProgramActionTransaction();
        var caster = new Object();
        var selected = new FakeEntity("selected");
        var dead = new FakeEntity("dead");
        var distant = new FakeEntity("distant");
        var healthy = new FakeEntity("healthy");
        var view = new FakeView(caster);
        view.nearby = List.of(dead, distant, healthy, selected);
        view.alive.addAll(List.of(distant, healthy, selected));
        view.distance.put(dead, 1.0);
        view.distance.put(distant, 100.0);
        view.distance.put(healthy, 4.0);
        view.distance.put(selected, 9.0);
        view.health.put(healthy, 80.0);
        view.health.put(selected, 40.0);
        view.types.put(selected, 6);

        var replay = PrecisionProgramExecutionBridge.replay(
                compiled.program(),
                Map.of(8, true),
                List.of(7, 8),
                100L,
                view,
                transaction,
                (_, _) -> () -> ProgramActionTransaction.Undo.NONE
        );

        assertTrue(replay.valid());
        assertEquals(1, transaction.size());
    }

    @Test
    void computesNativeDistanceAndStatusBranchesWithoutPlannerValues() {
        var caster = new Object();
        var view = new FakeView(caster);
        view.distance.put(caster, 9.0);
        view.status.add(caster);

        assertTrue(replaySingleBranch(
                branchGraph(PrecisionGraph.NodeKind.DISTANCE_BRANCH, 5.0), view).valid());
        assertTrue(replaySingleBranch(
                branchGraph(PrecisionGraph.NodeKind.STATUS_EFFECT_BRANCH, 0.0), view).valid());
    }

    @Test
    void executesCommonSpatialLogicAndPrecisionActionInOneGraph() {
        var caster = new Object();
        var graph = new ProgramGraph(
                List.of(
                        genericNode(1, PrecisionProgramNodeIds.ON_CAST),
                        precisionNode(2, PrecisionGraph.NodeKind.CASTER, 0.0),
                        genericNode(3, CommonProgramNodeIds.ENTITY_POSITION),
                        genericNode(4, CommonProgramNodeIds.WORLD_POSITION_COMPONENTS),
                        floatNode(5, 0.0),
                        genericNode(6, CommonProgramNodeIds.FLOAT_GREATER),
                        genericNode(7, CommonProgramNodeIds.BRANCH),
                        precisionNode(8, PrecisionGraph.NodeKind.END_INTRUSION, 0.0)
                ),
                List.of(
                        genericEdge(1, "flow", 7, "flow"),
                        genericEdge(2, "entity", 3, "entity"),
                        genericEdge(3, "position", 4, "position"),
                        genericEdge(4, "y", 6, "left"),
                        genericEdge(5, "value", 6, "right"),
                        genericEdge(6, "result", 7, "condition"),
                        genericEdge(7, "true", 8, "flow")
                )
        );
        var compiled = PrecisionProgramCompilation.compile(graph);
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var transaction = new ProgramActionTransaction();
        var resolver = new FakeTargetResolver(caster);

        var replay = PrecisionProgramExecutionBridge.replay(
                compiled.program(),
                Map.of(8, true),
                List.of(8),
                100L,
                new FakeView(caster),
                resolver,
                transaction,
                (_, _) -> () -> ProgramActionTransaction.Undo.NONE
        );

        assertTrue(replay.valid(), () -> replay.toString());
        assertEquals(1, transaction.size());
    }

    @Test
    void nativelyExecutesMixedGraphWithoutLegacyValuesOrFlowTrace() {
        var caster = new Object();
        var graph = new ProgramGraph(
                List.of(
                        genericNode(1, PrecisionProgramNodeIds.ON_CAST),
                        precisionNode(2, PrecisionGraph.NodeKind.CASTER, 0.0),
                        genericNode(3, CommonProgramNodeIds.ENTITY_POSITION),
                        genericNode(4, CommonProgramNodeIds.WORLD_POSITION_COMPONENTS),
                        floatNode(5, 0.0),
                        genericNode(6, CommonProgramNodeIds.FLOAT_GREATER),
                        genericNode(7, CommonProgramNodeIds.BRANCH),
                        precisionNode(8, PrecisionGraph.NodeKind.END_INTRUSION, 0.0)
                ),
                List.of(
                        genericEdge(1, "flow", 7, "flow"),
                        genericEdge(2, "entity", 3, "entity"),
                        genericEdge(3, "position", 4, "position"),
                        genericEdge(4, "y", 6, "left"),
                        genericEdge(5, "value", 6, "right"),
                        genericEdge(6, "result", 7, "condition"),
                        genericEdge(7, "true", 8, "flow")
                )
        );
        var compiled = PrecisionProgramCompilation.compile(graph);
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var transaction = new ProgramActionTransaction();
        var visited = new ArrayList<Integer>();

        var result = PrecisionProgramExecutionBridge.executeNative(
                compiled.program(),
                100L,
                new FakeView(caster),
                new FakeTargetResolver(caster),
                transaction,
                (context, kind, _, _) -> {
                    assertEquals(PrecisionGraph.NodeKind.END_INTRUSION, kind);
                    visited.add(context.nodeId());
                    context.attachment(ProgramExecutionFrame.class).orElseThrow().stage(
                            context, () -> ProgramActionTransaction.Undo.NONE);
                    return ProgramNodeStep.next("flow");
                }
        );

        assertTrue(result.valid(), () -> result.toString());
        assertEquals(List.of(8), visited);
        assertEquals(1, transaction.size());
    }

    private static PrecisionGraph branchGraph() {
        return branchGraph(PrecisionGraph.NodeKind.HEALTH_RATIO_BRANCH, 50.0);
    }

    private static PrecisionGraph branchGraph(
            PrecisionGraph.NodeKind branch,
            double parameter
    ) {
        return new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(1, PrecisionGraph.NodeKind.CASTER, 0.0, 0.0, 0.0),
                        new PrecisionGraph.Node(
                                2,
                                branch,
                                parameter,
                                100.0,
                                0.0
                        ),
                        new PrecisionGraph.Node(
                                3,
                                PrecisionGraph.NodeKind.END_INTRUSION,
                                0.0,
                                200.0,
                                0.0
                        ),
                        new PrecisionGraph.Node(
                                4,
                                PrecisionGraph.NodeKind.END_INTRUSION,
                                0.0,
                                200.0,
                                100.0
                        )
                ),
                List.of(
                        new PrecisionGraph.Edge(1, 0, 2, 0),
                        new PrecisionGraph.Edge(
                                2,
                                0,
                                3,
                                PrecisionGraph.NodeKind.END_INTRUSION.flowInputPort()
                        ),
                        new PrecisionGraph.Edge(
                                2,
                                1,
                                4,
                                PrecisionGraph.NodeKind.END_INTRUSION.flowInputPort()
                        )
                )
        );
    }

    private static PrecisionProgramExecutionBridge.ReplayResult replaySingleBranch(
            PrecisionGraph graph,
            PrecisionProgramRuntimeView view
    ) {
        var compiled = PrecisionProgramCompilation.compile(graph);
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        return PrecisionProgramExecutionBridge.replay(
                compiled.program(),
                Map.of(3, true),
                List.of(2, 3),
                100L,
                view,
                new ProgramActionTransaction(),
                (_, _) -> () -> ProgramActionTransaction.Undo.NONE
        );
    }

    private static PrecisionGraph collectionGraph() {
        return new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(1, PrecisionGraph.NodeKind.CASTER, 0.0, 0.0, 0.0),
                        new PrecisionGraph.Node(
                                2,
                                PrecisionGraph.NodeKind.ENTITY_TO_SET,
                                0.0,
                                100.0,
                                0.0
                        ),
                        new PrecisionGraph.Node(
                                3,
                                PrecisionGraph.NodeKind.LIMIT,
                                1.0,
                                200.0,
                                0.0
                        ),
                        new PrecisionGraph.Node(
                                4,
                                PrecisionGraph.NodeKind.MENTAL_STUPOR,
                                0.0,
                                300.0,
                                0.0
                        )
                ),
                List.of(
                        new PrecisionGraph.Edge(1, 0, 2, 0),
                        new PrecisionGraph.Edge(2, 0, 3, 0),
                        new PrecisionGraph.Edge(3, 0, 4, 0)
                )
        );
    }

    private static ProgramGraph.Node genericNode(int id, Identifier type) {
        return new ProgramGraph.Node(id, type, 1, new JsonObject());
    }

    private static ProgramGraph.Node precisionNode(
            int id,
            PrecisionGraph.NodeKind kind,
            double parameter
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("parameter", parameter);
        return new ProgramGraph.Node(id, PrecisionProgramNodeIds.id(kind), 1, configuration);
    }

    private static ProgramGraph.Node floatNode(int id, double value) {
        var configuration = new JsonObject();
        configuration.addProperty("value", value);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.FLOAT_CONSTANT, 1, configuration);
    }

    private static ProgramGraph.Edge genericEdge(
            int from,
            String output,
            int to,
            String input
    ) {
        return new ProgramGraph.Edge(
                new ProgramGraph.Endpoint(from, output),
                new ProgramGraph.Endpoint(to, input)
        );
    }

    private static PrecisionGraph worldQueryGraph() {
        return new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(1, PrecisionGraph.NodeKind.NEARBY_ALL_ENTITIES,
                                10.0, 0.0, 0.0),
                        new PrecisionGraph.Node(2, PrecisionGraph.NodeKind.ALIVE,
                                0.0, 100.0, 0.0),
                        new PrecisionGraph.Node(3, PrecisionGraph.NodeKind.DISTANCE,
                                5.0, 200.0, 0.0),
                        new PrecisionGraph.Node(4, PrecisionGraph.NodeKind.HEALTH_BELOW,
                                50.0, 300.0, 0.0),
                        new PrecisionGraph.Node(5, PrecisionGraph.NodeKind.SORT_BY_DISTANCE,
                                0.0, 400.0, 0.0),
                        new PrecisionGraph.Node(6, PrecisionGraph.NodeKind.NEAREST,
                                0.0, 500.0, 0.0),
                        new PrecisionGraph.Node(7, PrecisionGraph.NodeKind.ENTITY_TYPE_BRANCH,
                                6.0, 600.0, 0.0),
                        new PrecisionGraph.Node(8, PrecisionGraph.NodeKind.END_INTRUSION,
                                0.0, 700.0, 0.0),
                        new PrecisionGraph.Node(9, PrecisionGraph.NodeKind.END_INTRUSION,
                                0.0, 700.0, 100.0)
                ),
                List.of(
                        new PrecisionGraph.Edge(1, 0, 2, 0),
                        new PrecisionGraph.Edge(2, 0, 3, 0),
                        new PrecisionGraph.Edge(3, 0, 4, 0),
                        new PrecisionGraph.Edge(4, 0, 5, 0),
                        new PrecisionGraph.Edge(5, 0, 6, 0),
                        new PrecisionGraph.Edge(6, 0, 7, 0),
                        new PrecisionGraph.Edge(7, 0, 8,
                                PrecisionGraph.NodeKind.END_INTRUSION.flowInputPort()),
                        new PrecisionGraph.Edge(7, 1, 9,
                                PrecisionGraph.NodeKind.END_INTRUSION.flowInputPort())
                )
        );
    }

    private record FakeEntity(String key) {
    }

    private static final class FakeTargetResolver implements ProgramTargetResolver {
        private final Object caster;

        private FakeTargetResolver(Object caster) {
            this.caster = caster;
        }

        @Override
        public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
            return entityReference == caster
                    ? Optional.of(new ProgramWorldPosition(OVERWORLD, 0.0, 64.0, 0.0))
                    : Optional.empty();
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

    private static final class FakeView implements PrecisionProgramRuntimeView {
        private final Object caster;
        private List<?> nearby = List.of();
        private final Set<Object> alive = new HashSet<>();
        private final Set<Object> allies = new HashSet<>();
        private final Set<Object> targeted = new HashSet<>();
        private final Set<Object> status = new HashSet<>();
        private final Map<Object, Double> distance = new HashMap<>();
        private final Map<Object, Double> health = new HashMap<>();
        private final Map<Object, Integer> types = new HashMap<>();

        private FakeView(Object caster) {
            this.caster = caster;
        }

        @Override
        public Object caster() {
            return caster;
        }

        @Override
        public List<?> nearbyLiving(double range) {
            return nearby;
        }

        @Override
        public List<?> nearbyEntities(double range) {
            return nearby;
        }

        @Override
        public List<?> nearbyItems(double range) {
            return nearby;
        }

        @Override
        public List<?> nearbyProjectiles(double range) {
            return nearby;
        }

        @Override
        public boolean alive(Object value) {
            return alive.contains(value);
        }

        @Override
        public double distanceSqr(Object value) {
            return distance.getOrDefault(value, Double.POSITIVE_INFINITY);
        }

        @Override
        public boolean withinDistance(Object value, double range) {
            return distanceSqr(value) <= range * range;
        }

        @Override
        public boolean ally(Object value) {
            return allies.contains(value);
        }

        @Override
        public boolean typeMatches(int type, Object value) {
            return types.getOrDefault(value, -1) == type;
        }

        @Override
        public double healthPercent(Object value) {
            return health.getOrDefault(value, Double.NaN);
        }

        @Override
        public double sortableHealthPercent(Object value) {
            return healthPercent(value);
        }

        @Override
        public boolean hasTarget(Object value) {
            return targeted.contains(value);
        }

        @Override
        public boolean hasStatusEffect(Object value) {
            return status.contains(value);
        }

        @Override
        public boolean visibleFrom(Object observer, Object value) {
            return true;
        }

        @Override
        public String stableKey(Object value) {
            return value instanceof FakeEntity entity ? entity.key() : String.valueOf(value);
        }

        @Override
        public int randomIndex(int bound) {
            return 0;
        }
    }
}
