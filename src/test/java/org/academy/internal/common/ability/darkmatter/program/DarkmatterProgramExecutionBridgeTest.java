package org.academy.internal.common.ability.darkmatter.program;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DarkmatterProgramExecutionBridgeTest {
    @Test
    void definitionExposesCategoryNodesAndCapabilityBounds() {
        var definition = AbilityProgramDefinitions.require(
                DarkmatterProgramNodeCatalog.DARKMATTER);
        var catalog = definition.editorCatalog();

        for (var id : DarkmatterProgramNodeCatalog.INSTANCE.types().keySet()) {
            assertSame(DarkmatterProgramNodeCatalog.INSTANCE.find(id),
                    definition.nodeLookup().find(id));
            assertNotNull(definition.executors().find(id), id.toString());
            var entry = catalog.entry(id);
            assertNotNull(entry, id.toString());
            assertTrue(entry.categoryRestricted(), id.toString());
            assertEquals(DarkmatterProgramNodeCatalog.DARKMATTER,
                    entry.exclusiveCategory().orElseThrow());
        }
        assertEquals(Set.of(DarkmatterProgramCapabilities.DISASSEMBLE_BLOCK),
                DarkmatterProgramNodeCatalog.INSTANCE
                        .find(DarkmatterProgramNodeIds.DISASSEMBLE_BLOCK)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(DarkmatterProgramCapabilities.DARKMATTER_CUT),
                DarkmatterProgramNodeCatalog.INSTANCE
                        .find(DarkmatterProgramNodeIds.DARKMATTER_CUT)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(DarkmatterProgramCapabilities.DISASSEMBLE_ENTITY),
                DarkmatterProgramNodeCatalog.INSTANCE
                        .find(DarkmatterProgramNodeIds.DISASSEMBLE_ENTITY)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(DarkmatterProgramCapabilities.CREATE_BEETLE),
                DarkmatterProgramNodeCatalog.INSTANCE
                        .find(DarkmatterProgramNodeIds.CREATE_BEETLE)
                        .scope().requiredCapabilities());

        var invalid = new JsonObject();
        invalid.addProperty("power", 3);
        assertNull(catalog.schema(DarkmatterProgramNodeIds.DARKMATTER_CUT, invalid));
    }

    @Test
    void disassembleBlockReceivesTypedBlockPosition() {
        var graph = new ProgramGraph(
                List.of(
                        blockPositionNode(1, 8, 70, 3),
                        powerNode(2, DarkmatterProgramNodeIds.DISASSEMBLE_BLOCK, 0)
                ),
                List.of(edge(1, "position", 2, "block"))
        );
        var compiled = AbilityProgramDefinitions.require(
                        DarkmatterProgramNodeCatalog.DARKMATTER)
                .compile(graph, Set.of(DarkmatterProgramCapabilities.DISASSEMBLE_BLOCK));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        assertEquals(2, compiled.program().entryNodeId());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = DarkmatterProgramExecutionBridge.execute(
                compiled.program(), 20L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(1, transaction.size());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of("disassemble:minecraft:overworld:8,70,3:0.0"),
                runtime.applied);
        transaction.release();
    }

    @Test
    void openDarkmatterCutRootStagesAndCommitsTypedDirection() {
        var graph = new ProgramGraph(
                List.of(
                        directionNode(1, 0.0, 0.0, 1.0),
                        powerNode(2, DarkmatterProgramNodeIds.DARKMATTER_CUT, 2)
                ),
                List.of(edge(1, "direction", 2, "direction"))
        );
        var compiled = AbilityProgramDefinitions.require(
                        DarkmatterProgramNodeCatalog.DARKMATTER)
                .compile(graph, Set.of(DarkmatterProgramCapabilities.DARKMATTER_CUT));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = DarkmatterProgramExecutionBridge.execute(
                compiled.program(), 40L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of("cut:0.0,0.0,1.0:2.0"), runtime.applied);
        transaction.release();
    }

    @Test
    void disassembleEntityReceivesSelectedEntity() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, DarkmatterProgramNodeIds.LOOK_TARGET, new JsonObject()),
                        powerNode(2, DarkmatterProgramNodeIds.DISASSEMBLE_ENTITY, 1)
                ),
                List.of(edge(1, "entity", 2, "entity"))
        );
        var compiled = AbilityProgramDefinitions.require(
                        DarkmatterProgramNodeCatalog.DARKMATTER)
                .compile(graph, Set.of(DarkmatterProgramCapabilities.DISASSEMBLE_ENTITY));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = DarkmatterProgramExecutionBridge.execute(
                compiled.program(), 60L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of("disassemble_entity:look_target:1.0"), runtime.applied);
        transaction.release();
    }

    @Test
    void createBeetleReceivesTypedWorldPosition() {
        var graph = new ProgramGraph(
                List.of(
                        worldPositionNode(1, 4.5, 70.0, -2.5),
                        powerNode(2, DarkmatterProgramNodeIds.CREATE_BEETLE, 2)
                ),
                List.of(edge(1, "position", 2, "position"))
        );
        var compiled = AbilityProgramDefinitions.require(
                        DarkmatterProgramNodeCatalog.DARKMATTER)
                .compile(graph, Set.of(DarkmatterProgramCapabilities.CREATE_BEETLE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = DarkmatterProgramExecutionBridge.execute(
                compiled.program(), 80L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of("create:minecraft:overworld:4.5,70.0,-2.5:2.0"),
                runtime.applied);
        transaction.release();
    }

    private static ProgramGraph.Node node(
            int id,
            Identifier type,
            JsonObject configuration
    ) {
        var nodeType = AbilityProgramDefinitions.require(
                DarkmatterProgramNodeCatalog.DARKMATTER).nodeLookup().find(type);
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

    private static ProgramGraph.Node blockPositionNode(
            int id,
            int x,
            int y,
            int z
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("dimension", "minecraft:overworld");
        configuration.addProperty("x", x);
        configuration.addProperty("y", y);
        configuration.addProperty("z", z);
        return node(id, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT, configuration);
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

    private static final class FakeRuntime implements DarkmatterProgramRuntime {
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
        public ProgramActionTransaction.ProgramAction disassembleBlock(
                ProgramBlockPosition block,
                float power
        ) {
            return action("disassemble:" + block.dimension() + ":" + block.x() + ","
                    + block.y() + "," + block.z() + ":" + power);
        }

        @Override
        public ProgramActionTransaction.ProgramAction darkmatterCut(
                ProgramDirection direction,
                float power
        ) {
            return action("cut:" + direction.x() + "," + direction.y() + ","
                    + direction.z() + ":" + power);
        }

        @Override
        public ProgramActionTransaction.ProgramAction disassembleEntity(
                Object entity,
                float power
        ) {
            return action("disassemble_entity:" + entity + ":" + power);
        }

        @Override
        public ProgramActionTransaction.ProgramAction createBeetle(
                ProgramWorldPosition position,
                float power
        ) {
            return action("create:" + position.dimension() + ":" + position.x() + ","
                    + position.y() + "," + position.z() + ":" + power);
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
