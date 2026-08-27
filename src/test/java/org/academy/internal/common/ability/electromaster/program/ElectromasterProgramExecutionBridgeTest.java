package org.academy.internal.common.ability.electromaster.program;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramVmResult;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ElectromasterProgramExecutionBridgeTest {
    @Test
    void definitionExposesCategoryNodesAndCapabilityBounds() {
        var definition = AbilityProgramDefinitions.require(
                ElectromasterProgramNodeCatalog.ELECTROMASTER);
        var catalog = definition.editorCatalog();

        for (var id : ElectromasterProgramNodeCatalog.INSTANCE.types().keySet()) {
            assertSame(ElectromasterProgramNodeCatalog.INSTANCE.find(id),
                    definition.nodeLookup().find(id));
            assertNotNull(definition.executors().find(id), id.toString());
            var entry = catalog.entry(id);
            assertNotNull(entry, id.toString());
            assertTrue(entry.categoryRestricted(), id.toString());
            assertEquals(ElectromasterProgramNodeCatalog.ELECTROMASTER,
                    entry.exclusiveCategory().orElseThrow());
        }
        assertEquals(Set.of(ElectromasterProgramCapabilities.ARC_DISCHARGE),
                ElectromasterProgramNodeCatalog.INSTANCE
                        .find(ElectromasterProgramNodeIds.ARC_DISCHARGE)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(ElectromasterProgramCapabilities.MAGNETIC_MOVE),
                ElectromasterProgramNodeCatalog.INSTANCE
                        .find(ElectromasterProgramNodeIds.MAGNETIC_MOVE)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(ElectromasterProgramCapabilities.CURRENT_RECHARGE),
                ElectromasterProgramNodeCatalog.INSTANCE
                        .find(ElectromasterProgramNodeIds.CURRENT_RECHARGE)
                        .scope().requiredCapabilities());

        var magneticBlock = new JsonObject();
        magneticBlock.addProperty("power", 1.0f);
        magneticBlock.addProperty("target_type", "block");
        magneticBlock.addProperty("mode", "launch");
        var magneticSchema = catalog.schema(
                ElectromasterProgramNodeIds.MAGNETIC_MOVE, magneticBlock);
        assertNotNull(magneticSchema);
        assertEquals(List.of("flow", "block", "destination"),
                magneticSchema.inputs().stream().map(port -> port.name()).toList());

        var energyBlock = new JsonObject();
        energyBlock.addProperty("target_type", "block");
        energyBlock.addProperty("mode", "below");
        energyBlock.addProperty("percent", 50.0f);
        var energySchema = catalog.schema(
                ElectromasterProgramNodeIds.ENERGY_DETECTION, energyBlock);
        assertNotNull(energySchema);
        assertEquals(List.of("block"),
                energySchema.inputs().stream().map(port -> port.name()).toList());

        var invalid = new JsonObject();
        invalid.addProperty("power", 3);
        assertNull(catalog.schema(ElectromasterProgramNodeIds.ARC_DISCHARGE, invalid));
    }

    @Test
    void openArcRootStagesAndCommitsElectricalAction() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, ElectromasterProgramNodeIds.LOOK_TARGET, new JsonObject()),
                        powerNode(2, ElectromasterProgramNodeIds.ARC_DISCHARGE, 2)
                ),
                List.of(edge(1, "entity", 2, "entity"))
        );
        var compiled = AbilityProgramDefinitions.require(
                        ElectromasterProgramNodeCatalog.ELECTROMASTER)
                .compile(graph, Set.of(ElectromasterProgramCapabilities.ARC_DISCHARGE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        assertEquals(2, compiled.program().entryNodeId());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = ElectromasterProgramExecutionBridge.execute(
                compiled.program(), 20L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(1, transaction.size());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of("arc:look_target:2.0"), runtime.applied);
        transaction.release();
    }

    @Test
    void magneticMoveReceivesTypedDestination() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, ElectromasterProgramNodeIds.CASTER, new JsonObject()),
                        worldPositionNode(2, 4.5, 65.0, -2.5),
                        powerNode(3, ElectromasterProgramNodeIds.MAGNETIC_MOVE, 0)
                ),
                List.of(
                        edge(1, "entity", 3, "entity"),
                        edge(2, "position", 3, "destination")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        ElectromasterProgramNodeCatalog.ELECTROMASTER)
                .compile(graph, Set.of(ElectromasterProgramCapabilities.MAGNETIC_MOVE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = ElectromasterProgramExecutionBridge.execute(
                compiled.program(), 40L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of("magnetic:caster:4.5,65.0,-2.5:0.0"),
                runtime.applied);
        transaction.release();
    }

    private static ProgramGraph.Node node(
            int id,
            Identifier type,
            JsonObject configuration
    ) {
        var nodeType = AbilityProgramDefinitions.require(
                ElectromasterProgramNodeCatalog.ELECTROMASTER).nodeLookup().find(type);
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

    private static final class FakeRuntime implements ElectromasterProgramRuntime {
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
        public ProgramActionTransaction.ProgramAction arcDischarge(
                Object entity,
                float power
        ) {
            return action("arc:" + entity + ":" + power);
        }

        @Override
        public ProgramActionTransaction.ProgramAction magneticMove(
                Object target,
                ProgramWorldPosition destination,
                float power,
                ElectromasterProgramNodeCatalog.EnergyTargetType targetType,
                ElectromasterProgramNodeCatalog.MagneticMode mode
        ) {
            return action("magnetic:" + target + ":" + destination.x() + ","
                    + destination.y() + "," + destination.z() + ":" + power);
        }

        @Override
        public List<ProgramBlockPosition> chargeableBlocksAround(
                ProgramWorldPosition center,
                double radius
        ) {
            return List.of();
        }

        @Override
        public OptionalDouble entityEnergyFraction(Object entity) {
            return OptionalDouble.of(0.5);
        }

        @Override
        public OptionalDouble blockEnergyFraction(ProgramBlockPosition block) {
            return OptionalDouble.of(0.5);
        }

        @Override
        public int redstonePower(ProgramBlockPosition block) {
            return 0;
        }

        @Override
        public ProgramActionTransaction.ProgramAction currentRecharge(
                Object target,
                ElectromasterProgramNodeCatalog.EnergyTargetType targetType
        ) {
            return action("recharge:" + target + ":" + targetType);
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
    }
}
