package org.academy.internal.common.ability.meltdowner.program;

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

class MeltdownerProgramExecutionBridgeTest {
    @Test
    void definitionExposesCategoryNodesAndCapabilityBounds() {
        var definition = AbilityProgramDefinitions.require(
                MeltdownerProgramNodeCatalog.MELTDOWNER);
        var catalog = definition.editorCatalog();

        for (var id : MeltdownerProgramNodeCatalog.INSTANCE.types().keySet()) {
            assertSame(MeltdownerProgramNodeCatalog.INSTANCE.find(id),
                    definition.nodeLookup().find(id));
            assertNotNull(definition.executors().find(id), id.toString());
            var entry = catalog.entry(id);
            assertNotNull(entry, id.toString());
            assertTrue(entry.categoryRestricted(), id.toString());
            assertEquals(MeltdownerProgramNodeCatalog.MELTDOWNER,
                    entry.exclusiveCategory().orElseThrow());
        }
        assertEquals(Set.of(MeltdownerProgramCapabilities.ELECTRON_BEAM),
                MeltdownerProgramNodeCatalog.INSTANCE
                        .find(MeltdownerProgramNodeIds.ELECTRON_BEAM)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(MeltdownerProgramCapabilities.MINING_BEAM),
                MeltdownerProgramNodeCatalog.INSTANCE
                        .find(MeltdownerProgramNodeIds.MINING_BEAM)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(MeltdownerProgramCapabilities.ATOMIC_JET),
                MeltdownerProgramNodeCatalog.INSTANCE
                        .find(MeltdownerProgramNodeIds.ATOMIC_JET)
                        .scope().requiredCapabilities());

        var targetAim = new JsonObject();
        targetAim.addProperty("power", 1.0f);
        targetAim.addProperty("aim_mode", "target");
        targetAim.addProperty("destroy_blocks", false);
        var targetSchema = catalog.schema(MeltdownerProgramNodeIds.ELECTRON_BEAM, targetAim);
        assertNotNull(targetSchema);
        assertEquals(List.of("flow", "origin", "target_position"),
                targetSchema.inputs().stream().map(port -> port.name()).toList());

        var jet = new JsonObject();
        jet.addProperty("power", 1.0f);
        jet.addProperty("destroy_blocks", true);
        var jetSchema = catalog.schema(MeltdownerProgramNodeIds.ATOMIC_JET, jet);
        assertNotNull(jetSchema);
        assertEquals(List.of("flow", "entity", "direction"),
                jetSchema.inputs().stream().map(port -> port.name()).toList());

        var invalid = new JsonObject();
        invalid.addProperty("power", 3);
        assertNull(catalog.schema(MeltdownerProgramNodeIds.ELECTRON_BEAM, invalid));
    }

    @Test
    void openElectronBeamRootStagesAndCommitsTypedDirection() {
        var graph = new ProgramGraph(
                List.of(
                        directionNode(1, 0.0, 0.5, 1.0),
                        powerNode(2, MeltdownerProgramNodeIds.ELECTRON_BEAM, 2)
                ),
                List.of(edge(1, "direction", 2, "direction"))
        );
        var compiled = AbilityProgramDefinitions.require(
                        MeltdownerProgramNodeCatalog.MELTDOWNER)
                .compile(graph, Set.of(MeltdownerProgramCapabilities.ELECTRON_BEAM));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        assertEquals(2, compiled.program().entryNodeId());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = MeltdownerProgramExecutionBridge.execute(
                compiled.program(), 20L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(1, transaction.size());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of("electron:0.0,0.4472135954999579,0.8944271909999159:2.0"),
                runtime.applied);
        transaction.release();
    }

    @Test
    void miningBeamReceivesTypedBlockPosition() {
        var graph = new ProgramGraph(
                List.of(
                        blockPositionNode(1, 8, 70, 3),
                        powerNode(2, MeltdownerProgramNodeIds.MINING_BEAM, 0)
                ),
                List.of(edge(1, "position", 2, "block"))
        );
        var compiled = AbilityProgramDefinitions.require(
                        MeltdownerProgramNodeCatalog.MELTDOWNER)
                .compile(graph, Set.of(MeltdownerProgramCapabilities.MINING_BEAM));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var runtime = new FakeRuntime();
        var transaction = new ProgramActionTransaction();

        var result = MeltdownerProgramExecutionBridge.execute(
                compiled.program(), 40L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of("mining:minecraft:overworld:8,70,3:0.0"),
                runtime.applied);
        transaction.release();
    }

    private static ProgramGraph.Node node(
            int id,
            net.minecraft.resources.Identifier type,
            JsonObject configuration
    ) {
        var nodeType = AbilityProgramDefinitions.require(
                MeltdownerProgramNodeCatalog.MELTDOWNER).nodeLookup().find(type);
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

    private static final class FakeRuntime implements MeltdownerProgramRuntime {
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
        public ProgramActionTransaction.ProgramAction fireElectronBeam(
                ProgramWorldPosition origin,
                ProgramDirection direction,
                ProgramWorldPosition target,
                float power,
                boolean destroyBlocks
        ) {
            return action("electron:" + direction.x() + "," + direction.y() + ","
                    + direction.z() + ":" + power);
        }

        @Override
        public ProgramActionTransaction.ProgramAction fireMiningBeam(
                ProgramWorldPosition origin,
                ProgramDirection direction,
                ProgramWorldPosition target,
                ProgramBlockPosition legacyBlock,
                float power
        ) {
            return action("mining:" + legacyBlock.dimension() + ":" + legacyBlock.x()
                    + "," + legacyBlock.y() + "," + legacyBlock.z() + ":" + power);
        }

        @Override
        public ProgramActionTransaction.ProgramAction atomicJet(
                Object entity,
                ProgramDirection direction,
                float power,
                boolean destroyBlocks
        ) {
            return action("jet:" + entity + ":" + power + ":" + destroyBlocks);
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
