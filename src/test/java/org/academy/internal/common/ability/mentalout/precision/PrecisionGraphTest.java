package org.academy.internal.common.ability.mentalout.precision;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PrecisionGraphTest {
    private static void writeLegacyNode(
            DataOutputStream data,
            int id,
            PrecisionGraph.NodeKind kind,
            double parameter
    ) throws Exception {
        data.writeInt(id);
        data.writeByte(kind.ordinal());
        data.writeDouble(parameter);
        data.writeFloat(0.0f);
        data.writeFloat(0.0f);
    }

    private static void writeStableNode(
            DataOutputStream data,
            int id,
            PrecisionGraph.NodeKind kind,
            double parameter
    ) throws Exception {
        data.writeInt(id);
        data.writeByte(kind.wireId());
        data.writeDouble(parameter);
        data.writeFloat(0.0f);
        data.writeFloat(0.0f);
    }

    private static PrecisionGraph simpleGraph() {
        var nodes = new ArrayList<PrecisionGraph.Node>();
        nodes.add(node(3, PrecisionGraph.NodeKind.MENTAL_STUPOR));
        nodes.add(node(7, PrecisionGraph.NodeKind.ROSTER));
        return new PrecisionGraph(nodes, List.of(new PrecisionGraph.Edge(7, 0, 3, 0)));
    }

    private static PrecisionGraph.Node node(int id, PrecisionGraph.NodeKind kind) {
        return node(id, kind, kind.defaultParameter());
    }

    private static PrecisionGraph.Node node(int id, PrecisionGraph.NodeKind kind, double parameter) {
        return new PrecisionGraph.Node(id, kind, parameter, 0.0, 0.0);
    }

    @Test
    void rangeNodesDefaultToThirtyTwoBlocks() {
        assertEquals(32.0, PrecisionGraph.NodeKind.NEARBY_ENTITIES.defaultParameter());
        assertEquals(32.0, PrecisionGraph.NodeKind.NEARBY_ALL_ENTITIES.defaultParameter());
        assertEquals(32.0, PrecisionGraph.NodeKind.NEARBY_ITEMS.defaultParameter());
        assertEquals(32.0, PrecisionGraph.NodeKind.NEARBY_PROJECTILES.defaultParameter());
        assertEquals(32.0, PrecisionGraph.NodeKind.DISTANCE.defaultParameter());
        assertEquals(1.0, PrecisionGraph.NodeKind.POSITION_OFFSET.defaultParameter());
    }

    @Test
    void entityTypeFilterIncludesNonLivingCategories() {
        assertTrue(PrecisionGraph.NodeKind.TYPE_FILTER.isParameterValid(4.0));
        assertTrue(PrecisionGraph.NodeKind.TYPE_FILTER.isParameterValid(5.0));
        assertTrue(PrecisionGraph.NodeKind.TYPE_FILTER.isParameterValid(6.0));
        assertTrue(PrecisionGraph.NodeKind.TYPE_FILTER.isParameterValid(7.0));
        assertEquals(false, PrecisionGraph.NodeKind.TYPE_FILTER.isParameterValid(8.0));
    }

    @Test
    void newPositionNodesUseStableWireIdsAndTypedPorts() {
        var graph = new PrecisionGraph(
                List.of(
                        node(1, PrecisionGraph.NodeKind.CASTER),
                        node(2, PrecisionGraph.NodeKind.ROSTER),
                        node(3, PrecisionGraph.NodeKind.ENTITY_POSITION),
                        node(4, PrecisionGraph.NodeKind.PATH_TO)
                ),
                List.of(
                        new PrecisionGraph.Edge(1, 0, 3, 0),
                        new PrecisionGraph.Edge(2, 0, 4, 0),
                        new PrecisionGraph.Edge(3, 0, 4, 1)
                )
        );

        var decoded = PrecisionGraphCodec.decode(PrecisionGraphCodec.encode(graph));

        assertTrue(decoded.valid());
        assertEquals(PrecisionGraph.NodeKind.ENTITY_POSITION, decoded.graph().nodes().get(2).kind());
        assertEquals(48, PrecisionGraph.NodeKind.ENTITY_POSITION.wireId());
        assertEquals(49, PrecisionGraph.NodeKind.DIRECTION_BETWEEN.wireId());
        assertEquals(50, PrecisionGraph.NodeKind.POSITION_OFFSET.wireId());
        assertTrue(PrecisionGraph.isPortCompatible(
                PrecisionGraph.PortType.DIRECTION, PrecisionGraph.PortType.DIRECTION));
        assertEquals(false, PrecisionGraph.isPortCompatible(
                PrecisionGraph.PortType.ENTITY, PrecisionGraph.PortType.DIRECTION));
    }

    @Test
    void validatesAndCompilesTypedAcyclicGraph() {
        var graph = simpleGraph();

        var validation = graph.validate();
        var compiled = CompiledPrecisionProgram.compile(graph);

        assertTrue(validation.valid());
        assertTrue(compiled.valid());
        assertEquals(List.of(7, 3), compiled.program().order().stream()
                .map(PrecisionGraph.Node::id)
                .toList());
        assertNotNull(compiled.program().input(3, 0));
    }

    @Test
    void rejectsTypeMismatchMissingInputAndCycles() {
        var typeMismatch = new PrecisionGraph(
                List.of(
                        node(1, PrecisionGraph.NodeKind.CASTER),
                        node(2, PrecisionGraph.NodeKind.MENTAL_STUPOR)
                ),
                List.of(new PrecisionGraph.Edge(1, 0, 2, 0))
        );
        var missingInput = new PrecisionGraph(
                List.of(node(1, PrecisionGraph.NodeKind.MENTAL_STUPOR)),
                List.of()
        );
        var cycle = new PrecisionGraph(
                List.of(
                        node(1, PrecisionGraph.NodeKind.ROSTER),
                        node(2, PrecisionGraph.NodeKind.ALIVE),
                        node(3, PrecisionGraph.NodeKind.DISTANCE, 16.0),
                        node(4, PrecisionGraph.NodeKind.MENTAL_STUPOR)
                ),
                List.of(
                        new PrecisionGraph.Edge(2, 0, 3, 0),
                        new PrecisionGraph.Edge(3, 0, 2, 0),
                        new PrecisionGraph.Edge(1, 0, 4, 0)
                )
        );

        var typeValidation = typeMismatch.validate();
        var inputValidation = missingInput.validate();
        var cycleValidation = cycle.validate();
        assertEquals(PrecisionGraph.Diagnostic.TYPE_MISMATCH, typeValidation.diagnostic());
        assertEquals(2, typeValidation.nodeId());
        assertEquals(0, typeValidation.port());
        assertEquals(PrecisionGraph.Diagnostic.MISSING_INPUT, inputValidation.diagnostic());
        assertEquals(1, inputValidation.nodeId());
        assertEquals(0, inputValidation.port());
        assertEquals(PrecisionGraph.Diagnostic.CYCLE, cycleValidation.diagnostic());
        assertEquals(2, cycleValidation.nodeId());
    }

    @Test
    void rejectsNonFiniteValuesAndConfiguredLimits() {
        var nonFinitePosition = new PrecisionGraph(
                List.of(new PrecisionGraph.Node(
                        1,
                        PrecisionGraph.NodeKind.END_INTRUSION,
                        0.0,
                        Double.NaN,
                        0.0
                )),
                List.of()
        );
        var nonFiniteParameter = new PrecisionGraph(
                List.of(new PrecisionGraph.Node(
                        1,
                        PrecisionGraph.NodeKind.NEARBY_ENTITIES,
                        Double.POSITIVE_INFINITY,
                        0.0,
                        0.0
                )),
                List.of()
        );
        var tooManyNodes = new PrecisionGraph(
                IntStream.rangeClosed(0, PrecisionGraph.MAX_NODES)
                        .mapToObj(id -> node(id, PrecisionGraph.NodeKind.END_INTRUSION))
                        .toList(),
                List.of()
        );
        var repeatedEdge = new PrecisionGraph.Edge(1, 0, 2, 0);
        var tooManyEdges = new PrecisionGraph(
                List.of(
                        node(1, PrecisionGraph.NodeKind.ROSTER),
                        node(2, PrecisionGraph.NodeKind.MENTAL_STUPOR)
                ),
                Collections.nCopies(PrecisionGraph.MAX_EDGES + 1, repeatedEdge)
        );

        assertEquals(PrecisionGraph.Diagnostic.NON_FINITE_VALUE, nonFinitePosition.validate().diagnostic());
        assertEquals(PrecisionGraph.Diagnostic.NON_FINITE_VALUE, nonFiniteParameter.validate().diagnostic());
        assertEquals(PrecisionGraph.Diagnostic.TOO_MANY_NODES, tooManyNodes.validate().diagnostic());
        assertEquals(PrecisionGraph.Diagnostic.TOO_MANY_EDGES, tooManyEdges.validate().diagnostic());
    }

    @Test
    void normalizationAndEncodingAreDeterministic() {
        var first = simpleGraph();
        var second = new PrecisionGraph(
                List.of(first.nodes().get(1), first.nodes().get(0)),
                List.of(first.edges().getFirst())
        );

        var firstBytes = PrecisionGraphCodec.encode(first);
        var secondBytes = PrecisionGraphCodec.encode(second);
        var decoded = PrecisionGraphCodec.decode(firstBytes);

        assertArrayEquals(firstBytes, secondBytes);
        assertTrue(decoded.valid());
        assertEquals(first.validate().normalized(), decoded.graph());
    }

    @Test
    void codecRejectsMalformedTrailingAndOversizedPayloads() {
        var encoded = PrecisionGraphCodec.encode(simpleGraph());
        var trailing = Arrays.copyOf(encoded, encoded.length + 1);
        var oversized = new byte[PrecisionGraph.MAX_ENCODED_BYTES + 1];

        assertEquals(
                PrecisionGraph.Diagnostic.MALFORMED,
                PrecisionGraphCodec.decode(new byte[]{2}).diagnostic()
        );
        assertEquals(
                PrecisionGraph.Diagnostic.MALFORMED,
                PrecisionGraphCodec.decode(trailing).diagnostic()
        );
        assertEquals(
                PrecisionGraph.Diagnostic.TOO_LARGE,
                PrecisionGraphCodec.decode(oversized).diagnostic()
        );
    }

    @Test
    void duplicateInputPortsAndEdgesAreRejected() {
        var nodes = List.of(
                node(1, PrecisionGraph.NodeKind.ROSTER),
                node(2, PrecisionGraph.NodeKind.ROSTER),
                node(3, PrecisionGraph.NodeKind.MENTAL_STUPOR)
        );
        var edge = new PrecisionGraph.Edge(1, 0, 3, 0);
        var duplicateInput = new PrecisionGraph(
                nodes,
                List.of(edge, new PrecisionGraph.Edge(2, 0, 3, 0))
        );
        var duplicateEdge = new PrecisionGraph(nodes, List.of(edge, edge));

        assertEquals(PrecisionGraph.Diagnostic.MULTIPLE_INPUTS, duplicateInput.validate().diagnostic());
        assertEquals(PrecisionGraph.Diagnostic.DUPLICATE_EDGE, duplicateEdge.validate().diagnostic());
    }

    @Test
    void catalogUsesStableUniqueWireIds() {
        assertEquals(55, PrecisionGraph.NodeKind.values().length);
        assertEquals(55, Arrays.stream(PrecisionGraph.NodeKind.values())
                .map(PrecisionGraph.NodeKind::wireId)
                .distinct()
                .count());
        assertEquals(0, PrecisionGraph.NodeKind.CASTER.wireId());
        assertEquals(31, PrecisionGraph.NodeKind.REMOVE_CONTROL.wireId());
        assertEquals(42, PrecisionGraph.NodeKind.VISIBLE_FROM.wireId());
        assertEquals(43, PrecisionGraph.NodeKind.SIGHT_POSITION.wireId());
        assertEquals(44, PrecisionGraph.NodeKind.GUARD_MODE.wireId());
        assertEquals(51, PrecisionGraph.NodeKind.HEALTH_RATIO_BRANCH.wireId());
        assertEquals(54, PrecisionGraph.NodeKind.STATUS_EFFECT_BRANCH.wireId());
    }

    @Test
    void oneConditionalBranchFormsAnAcyclicTwoWayFlow() {
        var graph = new PrecisionGraph(
                List.of(
                        node(1, PrecisionGraph.NodeKind.CASTER),
                        node(2, PrecisionGraph.NodeKind.ROSTER),
                        node(3, PrecisionGraph.NodeKind.HEALTH_RATIO_BRANCH, 50.0),
                        node(4, PrecisionGraph.NodeKind.MENTAL_STUPOR),
                        node(5, PrecisionGraph.NodeKind.IMPRESSION_MANIPULATION)
                ),
                List.of(
                        new PrecisionGraph.Edge(1, 0, 3, 0),
                        new PrecisionGraph.Edge(2, 0, 4, 0),
                        new PrecisionGraph.Edge(2, 0, 5, 0),
                        new PrecisionGraph.Edge(3, 0, 4, 1),
                        new PrecisionGraph.Edge(3, 1, 5, 1)
                )
        );

        var validation = graph.validate();
        var compiled = CompiledPrecisionProgram.compile(graph);

        assertTrue(validation.valid());
        assertTrue(compiled.valid());
        assertEquals(List.of(3, 4, 5), validation.actionOrder());
        assertEquals(4, compiled.program().flowTarget(3, 0));
        assertEquals(5, compiled.program().flowTarget(3, 1));
        assertEquals(2, PrecisionGraph.NodeKind.HEALTH_RATIO_BRANCH.flowOutputCount());
    }

    @Test
    void actionsMustFormOneUnbranchedFlowChain() {
        var nodes = List.of(
                node(1, PrecisionGraph.NodeKind.ROSTER),
                node(2, PrecisionGraph.NodeKind.MENTAL_STUPOR),
                node(3, PrecisionGraph.NodeKind.IMPRESSION_MANIPULATION),
                node(4, PrecisionGraph.NodeKind.REMOVE_CONTROL)
        );
        var dataEdges = List.of(
                new PrecisionGraph.Edge(1, 0, 2, 0),
                new PrecisionGraph.Edge(1, 0, 3, 0),
                new PrecisionGraph.Edge(1, 0, 4, 0)
        );
        var disconnected = new PrecisionGraph(nodes, dataEdges);
        var branchedEdges = new ArrayList<>(dataEdges);
        branchedEdges.add(new PrecisionGraph.Edge(2, 0, 3, 1));
        branchedEdges.add(new PrecisionGraph.Edge(2, 0, 4, 1));

        assertEquals(PrecisionGraph.Diagnostic.DISCONNECTED_FLOW, disconnected.validate().diagnostic());
        assertEquals(PrecisionGraph.Diagnostic.BRANCHED_FLOW,
                new PrecisionGraph(nodes, branchedEdges).validate().diagnostic());
    }

    @Test
    void actionFlowIsAnOpenChain() {
        var nodes = List.of(
                node(1, PrecisionGraph.NodeKind.ROSTER),
                node(2, PrecisionGraph.NodeKind.MENTAL_STUPOR),
                node(3, PrecisionGraph.NodeKind.IMPRESSION_MANIPULATION)
        );
        var openChain = new PrecisionGraph(nodes, List.of(
                new PrecisionGraph.Edge(1, 0, 2, 0),
                new PrecisionGraph.Edge(1, 0, 3, 0),
                new PrecisionGraph.Edge(2, 0, 3, 1)
        ));

        assertTrue(openChain.validate().valid());
        assertTrue(openChain.flowPosition(2).isOpenInput());
        assertTrue(openChain.flowPosition(3).isOpenOutput());
    }

    @Test
    void closedActionFlowIsRejected() {
        var closedLoop = new PrecisionGraph(
                List.of(
                        node(1, PrecisionGraph.NodeKind.ROSTER),
                        node(2, PrecisionGraph.NodeKind.MENTAL_STUPOR),
                        node(3, PrecisionGraph.NodeKind.IMPRESSION_MANIPULATION)
                ),
                List.of(
                        new PrecisionGraph.Edge(1, 0, 2, 0),
                        new PrecisionGraph.Edge(1, 0, 3, 0),
                        new PrecisionGraph.Edge(2, 0, 3, 1),
                        new PrecisionGraph.Edge(3, 0, 2, 1)
                )
        );

        assertEquals(PrecisionGraph.Diagnostic.FLOW_CYCLE, closedLoop.validate().diagnostic());
    }

    @Test
    void legacyMigrationPreservesStableActionOrder() {
        var legacy = new PrecisionGraph(
                List.of(
                        node(7, PrecisionGraph.NodeKind.ROSTER),
                        node(9, PrecisionGraph.NodeKind.IMPRESSION_MANIPULATION),
                        node(3, PrecisionGraph.NodeKind.MENTAL_STUPOR)
                ),
                List.of(
                        new PrecisionGraph.Edge(7, 0, 9, 0),
                        new PrecisionGraph.Edge(7, 0, 3, 0)
                )
        );

        var migrated = PrecisionGraph.migrateLegacy(legacy);

        assertTrue(migrated.valid());
        assertEquals(List.of(3, 9), migrated.graph().validate().actionOrder());
        assertTrue(migrated.graph().edges().contains(new PrecisionGraph.Edge(3, 0, 9, 1)));
    }

    @Test
    void codecReadsVersionOneOrdinalsAndWritesStableVersionTwoIds() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(output)) {
            data.writeByte(1);
            data.writeByte(2);
            writeLegacyNode(data, 1, PrecisionGraph.NodeKind.ROSTER, 0.0);
            writeLegacyNode(data, 2, PrecisionGraph.NodeKind.MENTAL_STUPOR, 0.0);
            data.writeByte(1);
            data.writeInt(1);
            data.writeByte(0);
            data.writeInt(2);
            data.writeByte(0);
        }

        var decoded = PrecisionGraphCodec.decode(output.toByteArray());

        assertTrue(decoded.valid());
        assertEquals(PrecisionGraph.NodeKind.MENTAL_STUPOR, decoded.graph().nodes().get(1).kind());
        assertEquals(4, PrecisionGraphCodec.encode(decoded.graph())[0]);
    }

    @Test
    void durationParametersAndEntityDestinationsAreValidated() {
        assertTrue(PrecisionGraph.NodeKind.PATH_TO.isParameterValid(0.0));
        assertTrue(PrecisionGraph.NodeKind.PATH_TO.isParameterValid(1.0));
        assertTrue(PrecisionGraph.NodeKind.PATH_TO.isParameterValid(3600.0));
        assertFalse(PrecisionGraph.NodeKind.PATH_TO.isParameterValid(0.5));
        assertFalse(PrecisionGraph.NodeKind.PATH_TO.isParameterValid(3601.0));

        var graph = new PrecisionGraph(
                List.of(
                        node(1, PrecisionGraph.NodeKind.ROSTER),
                        node(2, PrecisionGraph.NodeKind.CASTER),
                        node(3, PrecisionGraph.NodeKind.PATH_TO)
                ),
                List.of(
                        new PrecisionGraph.Edge(1, 0, 3, 0),
                        new PrecisionGraph.Edge(2, 0, 3, 1)
                )
        );

        assertTrue(graph.validate().valid());
        assertTrue(PrecisionGraph.isPortCompatible(
                PrecisionGraph.PortType.ENTITY,
                PrecisionGraph.PortType.DESTINATION
        ));
        assertEquals(Long.MAX_VALUE, PrecisionOperationRuntime.actionExpiresAt(40L, 0.0));
        assertEquals(60L, PrecisionOperationRuntime.actionExpiresAt(40L, 1.0));
        assertEquals(72_040L, PrecisionOperationRuntime.actionExpiresAt(40L, 3600.0));
    }

    @Test
    void codecReadsVersionTwoAndWritesVersionFour() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(output)) {
            data.writeByte(2);
            data.writeByte(2);
            writeStableNode(data, 1, PrecisionGraph.NodeKind.ROSTER, 0.0);
            writeStableNode(data, 2, PrecisionGraph.NodeKind.MENTAL_STUPOR, 0.0);
            data.writeByte(1);
            data.writeInt(1);
            data.writeByte(0);
            data.writeInt(2);
            data.writeByte(0);
        }

        var decoded = PrecisionGraphCodec.decode(output.toByteArray());

        assertTrue(decoded.valid());
        assertEquals(0.0, decoded.graph().nodes().get(1).parameter());
        assertEquals(4, PrecisionGraphCodec.encode(decoded.graph())[0]);
    }
}
