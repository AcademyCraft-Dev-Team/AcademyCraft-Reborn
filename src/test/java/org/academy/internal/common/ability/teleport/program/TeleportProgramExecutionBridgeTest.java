package org.academy.internal.common.ability.teleport.program;

import com.google.gson.JsonObject;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramVmResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportProgramExecutionBridgeTest {
    @Test
    void definitionExposesCategoryNodesAndCapabilityBounds() {
        var definition = AbilityProgramDefinitions.require(
                TeleportProgramNodeCatalog.TELEPORT);
        var catalog = definition.editorCatalog();

        for (var id : TeleportProgramNodeCatalog.INSTANCE.types().keySet()) {
            assertSame(TeleportProgramNodeCatalog.INSTANCE.find(id),
                    definition.nodeLookup().find(id));
            assertNotNull(definition.executors().find(id), id.toString());
            var entry = catalog.entry(id);
            assertNotNull(entry, id.toString());
            assertTrue(entry.categoryRestricted(), id.toString());
            assertEquals(TeleportProgramNodeCatalog.TELEPORT,
                    entry.exclusiveCategory().orElseThrow());
        }
        assertEquals(Set.of(TeleportProgramCapabilities.SELF_TELEPORT),
                TeleportProgramNodeCatalog.INSTANCE
                        .find(TeleportProgramNodeIds.SELF_TELEPORT)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(TeleportProgramCapabilities.ENTITY_TELEPORT),
                TeleportProgramNodeCatalog.INSTANCE
                        .find(TeleportProgramNodeIds.ENTITY_TELEPORT)
                        .scope().requiredCapabilities());

        var blockTeleport = new JsonObject();
        blockTeleport.addProperty("power", 1.0f);
        blockTeleport.addProperty("target_type", "block");
        var blockSchema = catalog.schema(
                TeleportProgramNodeIds.ENTITY_TELEPORT, blockTeleport);
        assertNotNull(blockSchema);
        assertEquals(List.of("flow", "block", "destination", "direction"),
                blockSchema.inputs().stream().map(port -> port.name()).toList());
        var safetySchema = catalog.schema(
                TeleportProgramNodeIds.SPACE_SAFETY, new JsonObject());
        assertNotNull(safetySchema);
        assertEquals(List.of("entity", "position"),
                safetySchema.inputs().stream().map(port -> port.name()).toList());

        var invalid = new JsonObject();
        invalid.addProperty("power", 3);
        assertNull(catalog.schema(TeleportProgramNodeIds.SELF_TELEPORT, invalid));
    }

    @Test
    void openSelfTeleportRootStagesAndCommitsTypedDestination() {
        var graph = new ProgramGraph(
                List.of(
                        worldPositionNode(1, 4.5, 65.0, -2.5),
                        powerNode(2, TeleportProgramNodeIds.SELF_TELEPORT, 2)
                ),
                List.of(edge(1, "position", 2, "destination"))
        );
        var compiled = AbilityProgramDefinitions.require(
                        TeleportProgramNodeCatalog.TELEPORT)
                .compile(graph, Set.of(TeleportProgramCapabilities.SELF_TELEPORT));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        assertEquals(2, compiled.program().entryNodeId());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = TeleportProgramExecutionBridge.execute(
                compiled.program(), 20L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(1, transaction.size());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of("self:4.5,65.0,-2.5:2.0"), runtime.applied);
        transaction.release();
    }

    @Test
    void entityTeleportReceivesSelectedEntityAndDestination() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, TeleportProgramNodeIds.LOOK_TARGET, new JsonObject()),
                        worldPositionNode(2, 8.0, 70.0, 3.0),
                        powerNode(3, TeleportProgramNodeIds.ENTITY_TELEPORT, 0)
                ),
                List.of(
                        edge(1, "entity", 3, "entity"),
                        edge(2, "position", 3, "destination")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        TeleportProgramNodeCatalog.TELEPORT)
                .compile(graph, Set.of(TeleportProgramCapabilities.ENTITY_TELEPORT));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = TeleportProgramExecutionBridge.execute(
                compiled.program(), 40L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of("entity:look_target:8.0,70.0,3.0:0.0"),
                runtime.applied);
        transaction.release();
    }

    private static ProgramGraph.Node node(
            int id,
            net.minecraft.resources.Identifier type,
            JsonObject configuration
    ) {
        var nodeType = AbilityProgramDefinitions.require(
                TeleportProgramNodeCatalog.TELEPORT).nodeLookup().find(type);
        assertNotNull(nodeType, type.toString());
        return new ProgramGraph.Node(id, type, nodeType.schemaVersion(), configuration);
    }

    private static ProgramGraph.Node powerNode(
            int id,
            net.minecraft.resources.Identifier type,
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

    private static final class FakeRuntime implements TeleportProgramRuntime {
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
        public ProgramActionTransaction.ProgramAction teleportSelf(
                ProgramWorldPosition destination,
                float power
        ) {
            return action("self:" + position(destination) + ":" + power);
        }

        @Override
        public ProgramActionTransaction.ProgramAction teleportEntity(
                Object target,
                Object destination,
                ProgramDirection direction,
                float power,
                TeleportProgramNodeCatalog.TargetType targetType
        ) {
            return action("entity:" + target + ":"
                    + position((ProgramWorldPosition) destination) + ":" + power);
        }

        @Override
        public boolean isSpaceSafe(Object entity, ProgramWorldPosition position) {
            return true;
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

        private static String position(ProgramWorldPosition value) {
            return value.x() + "," + value.y() + "," + value.z();
        }
    }
}
