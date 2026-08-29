package org.academy.internal.common.ability.aeromanip.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramVmResult;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AeromanipProgramExecutionBridgeTest {
    @Test
    void definitionExposesCategoryNodesAndCapabilityBounds() {
        var definition = AbilityProgramDefinitions.require(
                AeromanipProgramNodeCatalog.AEROMANIP);
        var catalog = definition.editorCatalog();

        for (var id : AeromanipProgramNodeCatalog.INSTANCE.types().keySet()) {
            assertSame(AeromanipProgramNodeCatalog.INSTANCE.find(id),
                    definition.nodeLookup().find(id));
            assertNotNull(definition.executors().find(id), id.toString());
            var entry = catalog.entry(id);
            assertNotNull(entry, id.toString());
            assertTrue(entry.categoryRestricted(), id.toString());
            assertEquals(AeromanipProgramNodeCatalog.AEROMANIP,
                    entry.exclusiveCategory().orElseThrow());
        }
        assertEquals(Set.of(AeromanipProgramCapabilities.AIRFLOW_PUSH),
                AeromanipProgramNodeCatalog.INSTANCE
                        .find(AeromanipProgramNodeIds.AIRFLOW_PUSH)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(AeromanipProgramCapabilities.LAMINAR_CUT),
                AeromanipProgramNodeCatalog.INSTANCE
                        .find(AeromanipProgramNodeIds.LAMINAR_CUT)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(AeromanipProgramCapabilities.HIGH_SPEED_JET),
                AeromanipProgramNodeCatalog.INSTANCE
                        .find(AeromanipProgramNodeIds.PLACE_TEMPORARY_JET_NOZZLE)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(AeromanipProgramCapabilities.HIGH_SPEED_JET),
                AeromanipProgramNodeCatalog.INSTANCE
                        .find(AeromanipProgramNodeIds.FIRE_JETS)
                        .scope().requiredCapabilities());

        var laminar = (AeromanipProgramNodeCatalog.LaminarCutConfiguration)
                AeromanipProgramNodeCatalog.INSTANCE
                        .find(AeromanipProgramNodeIds.LAMINAR_CUT)
                        .configurationCodec()
                        .parse(JsonOps.INSTANCE,
                                catalog.entry(AeromanipProgramNodeIds.LAMINAR_CUT)
                                        .defaultConfiguration())
                        .result().orElseThrow();
        assertEquals(AeromanipProgramNodeCatalog.ChargeTier.INSTANT,
                laminar.chargeTier());
        var fireJets = (AeromanipProgramNodeCatalog.JetActivationConfiguration)
                AeromanipProgramNodeCatalog.INSTANCE
                        .find(AeromanipProgramNodeIds.FIRE_JETS)
                        .configurationCodec()
                        .parse(JsonOps.INSTANCE,
                                catalog.entry(AeromanipProgramNodeIds.FIRE_JETS)
                                        .defaultConfiguration())
                        .result().orElseThrow();
        assertEquals(8, fireJets.duration());

        var blockNozzle = new JsonObject();
        blockNozzle.addProperty("target_type", "block");
        var blockSchema = catalog.schema(
                AeromanipProgramNodeIds.PLACE_TEMPORARY_JET_NOZZLE, blockNozzle);
        assertNotNull(blockSchema);
        assertTrue(blockSchema.input("block").isPresent());
        assertTrue(blockSchema.input("entity").isEmpty());

        var invalid = new JsonObject();
        invalid.addProperty("power", 3);
        assertNull(catalog.schema(AeromanipProgramNodeIds.AIRFLOW_PUSH, invalid));
        var invalidDuration = new JsonObject();
        invalidDuration.addProperty("duration", 61);
        assertNull(catalog.schema(AeromanipProgramNodeIds.FIRE_JETS, invalidDuration));
    }

    @Test
    void airflowPushReceivesSelectedEntityAndTypedDirection() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, AeromanipProgramNodeIds.LOOK_TARGET, new JsonObject()),
                        directionNode(2, 1.0, 0.0, 0.0),
                        powerNode(3, AeromanipProgramNodeIds.AIRFLOW_PUSH, 1)
                ),
                List.of(
                        edge(1, "entity", 3, "entity"),
                        edge(2, "direction", 3, "direction")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        AeromanipProgramNodeCatalog.AEROMANIP)
                .compile(graph, Set.of(AeromanipProgramCapabilities.AIRFLOW_PUSH));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        assertEquals(3, compiled.program().entryNodeId());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = AeromanipProgramExecutionBridge.execute(
                compiled.program(), 20L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(1, transaction.size());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of("push:look_target:1.0,0.0,0.0:1.0"),
                runtime.applied);
        transaction.release();
    }

    @Test
    void openLaminarCutRootStagesAndCommitsTypedDirection() {
        var configuration = new JsonObject();
        configuration.addProperty("power", 2);
        configuration.addProperty("charge_tier", "full");
        var graph = new ProgramGraph(
                List.of(
                        directionNode(1, 0.0, 0.0, 1.0),
                        node(2, AeromanipProgramNodeIds.LAMINAR_CUT, configuration)
                ),
                List.of(edge(1, "direction", 2, "direction"))
        );
        var compiled = AbilityProgramDefinitions.require(
                        AeromanipProgramNodeCatalog.AEROMANIP)
                .compile(graph, Set.of(AeromanipProgramCapabilities.LAMINAR_CUT));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = AeromanipProgramExecutionBridge.execute(
                compiled.program(), 40L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of("cut:0.0,0.0,1.0:2.0:FULL"), runtime.applied);
        transaction.release();
    }

    @Test
    void temporaryEntityNozzleCanFlowDirectlyIntoConfiguredJetActivation() {
        var nozzleConfiguration = new JsonObject();
        nozzleConfiguration.addProperty("target_type", "entity");
        var activationConfiguration = new JsonObject();
        activationConfiguration.addProperty("duration", 12);
        var graph = new ProgramGraph(
                List.of(
                        node(1, AeromanipProgramNodeIds.LOOK_TARGET, new JsonObject()),
                        directionNode(2, 0.0, 1.0, 0.0),
                        node(3, AeromanipProgramNodeIds.PLACE_TEMPORARY_JET_NOZZLE,
                                nozzleConfiguration),
                        node(4, AeromanipProgramNodeIds.FIRE_JETS,
                                activationConfiguration)
                ),
                List.of(
                        edge(1, "entity", 3, "entity"),
                        edge(2, "direction", 3, "direction"),
                        edge(3, "flow", 4, "flow")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        AeromanipProgramNodeCatalog.AEROMANIP)
                .compile(graph, Set.of(AeromanipProgramCapabilities.HIGH_SPEED_JET));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = AeromanipProgramExecutionBridge.execute(
                compiled.program(), 60L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(2, transaction.size());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of(
                "nozzle:ENTITY:look_target:0.0,1.0,0.0",
                "fire:12"), runtime.applied);
        transaction.release();
    }

    private static ProgramGraph.Node node(
            int id,
            Identifier type,
            JsonObject configuration
    ) {
        var nodeType = AbilityProgramDefinitions.require(
                AeromanipProgramNodeCatalog.AEROMANIP).nodeLookup().find(type);
        assertNotNull(nodeType, type.toString());
        return new ProgramGraph.Node(id, type, nodeType.schemaVersion(), configuration);
    }

    private static ProgramGraph.Node powerNode(
            int id,
            Identifier type,
            int power
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("power", power);
        return node(id, type, configuration);
    }

    private static ProgramGraph.Node directionNode(
            int id,
            double x,
            double y,
            double z
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("x", x);
        configuration.addProperty("y", y);
        configuration.addProperty("z", z);
        return node(id, CommonProgramNodeIds.DIRECTION_CONSTANT, configuration);
    }

    private static ProgramGraph.Edge edge(
            int fromNode,
            String fromPort,
            int toNode,
            String toPort
    ) {
        return new ProgramGraph.Edge(
                new ProgramGraph.Endpoint(fromNode, fromPort),
                new ProgramGraph.Endpoint(toNode, toPort)
        );
    }

    private static final class FakeRuntime implements AeromanipProgramRuntime {
        private final List<String> applied = new ArrayList<>();

        @Override
        public Object caster() {
            return "caster";
        }

        @Override
        public Optional<Object> lookTarget() {
            return Optional.of("look_target");
        }

        @Override
        public ProgramActionTransaction.ProgramAction airflowPush(
                Object entity,
                ProgramDirection direction,
                float power
        ) {
            return action("push:" + entity + ":" + vector(direction) + ":" + power);
        }

        @Override
        public ProgramActionTransaction.ProgramAction laminarCut(
                ProgramDirection direction,
                float power,
                AeromanipChargeTier chargeTier
        ) {
            return action("cut:" + vector(direction) + ":" + power + ":" + chargeTier);
        }

        @Override
        public ProgramActionTransaction.ProgramAction placeTemporaryJetNozzle(
                Object target,
                ProgramDirection direction,
                AeromanipProgramNodeCatalog.NozzleTargetType targetType
        ) {
            return action("nozzle:" + targetType + ":" + target + ":" + vector(direction));
        }

        @Override
        public ProgramActionTransaction.ProgramAction fireJets(int durationSeconds) {
            return action("fire:" + durationSeconds);
        }

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

        private ProgramActionTransaction.ProgramAction action(String description) {
            return () -> {
                applied.add(description);
                return () -> applied.remove(description);
            };
        }

        private static String vector(ProgramDirection value) {
            return value.x() + "," + value.y() + "," + value.z();
        }
    }
}
