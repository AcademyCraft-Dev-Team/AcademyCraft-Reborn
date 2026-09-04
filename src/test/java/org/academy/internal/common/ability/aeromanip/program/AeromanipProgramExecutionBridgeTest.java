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
        assertEquals(AeromanipProgramNodeCatalog.ChargeAcceleration.STANDARD,
                laminar.chargeAcceleration());
        assertEquals(AeromanipProgramNodeCatalog.BladePlaneMode.DISABLED,
                laminar.planeMode());
        assertFalse(catalog.schema(AeromanipProgramNodeIds.LAMINAR_CUT,
                catalog.entry(AeromanipProgramNodeIds.LAMINAR_CUT).defaultConfiguration())
                .input("origin").orElseThrow().required());
        var directedPlane = catalog.entry(AeromanipProgramNodeIds.LAMINAR_CUT)
                .defaultConfiguration().getAsJsonObject().deepCopy();
        directedPlane.addProperty("plane_mode", "direction");
        assertTrue(catalog.schema(AeromanipProgramNodeIds.LAMINAR_CUT, directedPlane)
                .input("plane_direction").orElseThrow().required());
        directedPlane.addProperty("plane_mode", "random");
        assertTrue(catalog.schema(AeromanipProgramNodeIds.LAMINAR_CUT, directedPlane)
                .input("plane_direction").isEmpty());
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
        configuration.addProperty("charge_acceleration", "instant");
        configuration.addProperty("plane_mode", "direction");
        var graph = new ProgramGraph(
                List.of(
                        directionNode(1, 0.0, 0.0, 1.0),
                        worldPositionNode(3, 2.0, 65.0, 4.0),
                        directionNode(4, 0.0, 1.0, 0.0),
                        node(2, AeromanipProgramNodeIds.LAMINAR_CUT, configuration)
                ),
                List.of(
                        edge(1, "direction", 2, "direction"),
                        edge(3, "position", 2, "origin"),
                        edge(4, "direction", 2, "plane_direction")
                )
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
        assertEquals(List.of(
                "cut:2.0,65.0,4.0:0.0,0.0,1.0:2.0:FULL:2.0:DIRECTION:0.0,1.0,0.0"),
                runtime.applied);
        transaction.release();
    }

    @Test
    void laminarChargeAccelerationTradesCostForDelay() {
        assertEquals(24L, AeromanipProgramExecutionBridge.chargeDelayTicks(
                AeromanipProgramNodeCatalog.ChargeTier.FULL,
                AeromanipProgramNodeCatalog.ChargeAcceleration.STANDARD));
        assertEquals(12L, AeromanipProgramExecutionBridge.chargeDelayTicks(
                AeromanipProgramNodeCatalog.ChargeTier.FULL,
                AeromanipProgramNodeCatalog.ChargeAcceleration.ACCELERATED));
        assertEquals(0L, AeromanipProgramExecutionBridge.chargeDelayTicks(
                AeromanipProgramNodeCatalog.ChargeTier.FULL,
                AeromanipProgramNodeCatalog.ChargeAcceleration.INSTANT));
        assertEquals(1.0f,
                AeromanipProgramNodeCatalog.ChargeAcceleration.STANDARD.costMultiplier());
        assertEquals(1.5f,
                AeromanipProgramNodeCatalog.ChargeAcceleration.ACCELERATED.costMultiplier());
        assertEquals(2.0f,
                AeromanipProgramNodeCatalog.ChargeAcceleration.INSTANT.costMultiplier());
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

    @Test
    void convergingAirflowDerivesPerTargetDirectionsTowardCenter() {
        var configuration = new JsonObject();
        configuration.addProperty("power", 1.0f);
        configuration.addProperty("maximum_targets", 2);
        var graph = new ProgramGraph(
                List.of(
                        worldPositionNode(1, 0.0, 64.0, 2.0),
                        floatNode(2, 8.0),
                        node(3, CommonProgramNodeIds.ENTITIES_AROUND, new JsonObject()),
                        node(4, AeromanipProgramNodeIds.CONVERGING_AIRFLOW, configuration)
                ),
                List.of(
                        edge(1, "position", 3, "center"),
                        edge(2, "value", 3, "radius"),
                        edge(3, "entities", 4, "entities"),
                        edge(1, "position", 4, "center")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        AeromanipProgramNodeCatalog.AEROMANIP)
                .compile(graph, Set.of(AeromanipProgramCapabilities.CONVERGING_AIRFLOW));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = AeromanipProgramExecutionBridge.execute(
                compiled.program(), 80L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(2, transaction.size());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of(
                "push:first:0.0,0.0,1.0:1.0",
                "push:second:-0.7071067811865475,0.0,0.7071067811865475:1.0"
        ), runtime.applied);
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

    private static ProgramGraph.Node worldPositionNode(
            int id,
            double x,
            double y,
            double z
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("dimension", "minecraft:overworld");
        configuration.addProperty("x", x);
        configuration.addProperty("y", y);
        configuration.addProperty("z", z);
        return node(id, CommonProgramNodeIds.WORLD_POSITION_CONSTANT, configuration);
    }

    private static ProgramGraph.Node floatNode(int id, double value) {
        var configuration = new JsonObject();
        configuration.addProperty("value", value);
        return node(id, CommonProgramNodeIds.FLOAT_CONSTANT, configuration);
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
                ProgramWorldPosition origin,
                ProgramDirection direction,
                float power,
                AeromanipChargeTier chargeTier,
                float chargeCostMultiplier,
                ProgramDirection planeDirection,
                AeromanipProgramNodeCatalog.BladePlaneMode planeMode
        ) {
            return action("cut:" + origin.x() + "," + origin.y() + "," + origin.z()
                    + ":" + vector(direction) + ":" + power + ":" + chargeTier
                    + ":" + chargeCostMultiplier + ":" + planeMode + ":"
                    + (planeDirection == null ? "none" : vector(planeDirection)));
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
            return switch (String.valueOf(entityReference)) {
                case "first" -> Optional.of(new ProgramWorldPosition(
                        Identifier.parse("minecraft:overworld"), 0.0, 64.0, 0.0));
                case "second" -> Optional.of(new ProgramWorldPosition(
                        Identifier.parse("minecraft:overworld"), 2.0, 64.0, 0.0));
                default -> Optional.empty();
            };
        }

        @Override
        public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
            return Optional.empty();
        }

        @Override
        public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
            return List.of("first", "second", "third");
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
