package org.academy.internal.common.ability.accelerator.program;

import com.google.gson.JsonObject;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramDiagnosticCode;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramEditorDocument;
import org.academy.internal.common.ability.program.ProgramVmResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceleratorProgramExecutionBridgeTest {
    @Test
    void editorEnrichesLegacyShockwaveConfigurationWithNewControls() {
        var legacy = new JsonObject();
        legacy.addProperty("strength", 0);
        var program = new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                "legacy shockwave",
                AcceleratorProgramNodeCatalog.ACCELERATOR,
                new ProgramGraph(List.of(node(
                        0, AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE, legacy)), List.of()),
                ProgramEditorLayout.EMPTY
        );

        var document = new ProgramEditorDocument(
                program,
                AbilityProgramDefinitions.require(AcceleratorProgramNodeCatalog.ACCELERATOR),
                Set.of(AcceleratorProgramCapabilities.KINETIC_SHOCKWAVE)
        );
        var configuration = document.program().graph().nodes().getFirst()
                .configuration().getAsJsonObject();

        assertEquals(Set.of("power", "destroy_blocks", "radius"),
                configuration.keySet());
        assertFalse(configuration.get("destroy_blocks").getAsBoolean());
        assertEquals(1.0f, configuration.get("power").getAsFloat());
        assertEquals(8, configuration.get("radius").getAsInt());
    }

    @Test
    void definitionExposesMarkedVectorNodesAndBoundedStrengthDefaults() {
        var definition = AbilityProgramDefinitions.require(
                AcceleratorProgramNodeCatalog.ACCELERATOR);
        var catalog = definition.editorCatalog();

        for (var id : AcceleratorProgramNodeCatalog.INSTANCE.types().keySet()) {
            assertSame(AcceleratorProgramNodeCatalog.INSTANCE.find(id),
                    definition.nodeLookup().find(id));
            assertNotNull(definition.executors().find(id), id.toString());
            var entry = catalog.entry(id);
            assertNotNull(entry, id.toString());
            assertTrue(entry.categoryRestricted(), id.toString());
            assertEquals(AcceleratorProgramNodeCatalog.ACCELERATOR,
                    entry.exclusiveCategory().orElseThrow());
        }
        assertFalse(catalog.entry(CommonProgramNodeIds.ENTITY_LOOK_DIRECTION)
                .categoryRestricted());
        assertEquals(AcceleratorProgramStrength.STANDARD,
                ((AcceleratorProgramNodeCatalog.StrengthConfiguration)
                        AcceleratorProgramNodeCatalog.INSTANCE
                                .find(AcceleratorProgramNodeIds.APPLY_VECTOR)
                                .configurationCodec()
                                .parse(com.mojang.serialization.JsonOps.INSTANCE,
                                        catalog.entry(AcceleratorProgramNodeIds.APPLY_VECTOR)
                                                .defaultConfiguration())
                                .result()
                                .orElseThrow()).tier());
        var shockwave = (AcceleratorProgramNodeCatalog.ShockwaveConfiguration)
                AcceleratorProgramNodeCatalog.INSTANCE
                        .find(AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE)
                        .configurationCodec()
                        .parse(com.mojang.serialization.JsonOps.INSTANCE,
                                catalog.entry(AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE)
                                        .defaultConfiguration())
                        .result()
                        .orElseThrow();
        assertEquals(1.0f, shockwave.power());
        assertFalse(shockwave.destroyBlocks());
        assertEquals(8, shockwave.radius());

        var invalid = new JsonObject();
        invalid.addProperty("strength", 3);
        assertNull(catalog.schema(AcceleratorProgramNodeIds.APPLY_VECTOR, invalid));
        var invalidShockwave = catalog.entry(AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE)
                .defaultConfiguration().getAsJsonObject().deepCopy();
        invalidShockwave.addProperty("power", -0.01f);
        assertNull(catalog.schema(AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE, invalidShockwave));
        invalidShockwave.addProperty("power", 1.0f);
        invalidShockwave.addProperty("radius", 0);
        assertNotNull(catalog.schema(AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE, invalidShockwave));
        invalidShockwave.addProperty("radius", -1);
        assertNull(catalog.schema(AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE, invalidShockwave));
        assertEquals(Set.of(AcceleratorProgramCapabilities.APPLY_VECTOR),
                AcceleratorProgramNodeCatalog.INSTANCE
                        .find(AcceleratorProgramNodeIds.APPLY_VECTOR)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(AcceleratorProgramCapabilities.KINETIC_IMPACT),
                AcceleratorProgramNodeCatalog.INSTANCE
                        .find(AcceleratorProgramNodeIds.KINETIC_IMPACT)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(AcceleratorProgramCapabilities.KINETIC_SHOCKWAVE),
                AcceleratorProgramNodeCatalog.INSTANCE
                        .find(AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(AcceleratorProgramCapabilities.REDIRECT_PROJECTILE),
                AcceleratorProgramNodeCatalog.INSTANCE
                        .find(AcceleratorProgramNodeIds.REDIRECT_PROJECTILE)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(AcceleratorProgramCapabilities.DISPLACE_ENTITY),
                AcceleratorProgramNodeCatalog.INSTANCE
                        .find(AcceleratorProgramNodeIds.DISPLACE_ENTITY)
                        .scope().requiredCapabilities());
        assertEquals(Set.of(AcceleratorProgramCapabilities.DISPLACE_BLOCK),
                AcceleratorProgramNodeCatalog.INSTANCE
                        .find(AcceleratorProgramNodeIds.DISPLACE_BLOCK)
                        .scope().requiredCapabilities());
    }

    @Test
    void vectorAndImpactActionsStageThenCommitInFlowOrder() {
        var graph = new ProgramGraph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                AcceleratorProgramNodeCatalog.ACCELERATOR), new JsonObject()),
                        node(1, AcceleratorProgramNodeIds.CASTER, new JsonObject()),
                        directionNode(2, 1.0, 0.0, 0.0),
                        strengthNode(3, AcceleratorProgramNodeIds.APPLY_VECTOR, 0),
                        strengthNode(4, AcceleratorProgramNodeIds.KINETIC_IMPACT, 2)
                ),
                List.of(
                        edge(0, "flow", 3, "flow"),
                        edge(3, "flow", 4, "flow"),
                        edge(1, "entity", 3, "entity"),
                        edge(1, "entity", 4, "entity"),
                        edge(2, "direction", 3, "direction"),
                        edge(2, "direction", 4, "direction")
                )
        );
        var definition = AbilityProgramDefinitions.require(
                AcceleratorProgramNodeCatalog.ACCELERATOR);
        var denied = definition.compile(graph, Set.of());
        assertFalse(denied.valid());
        assertTrue(denied.diagnostics().stream().allMatch(diagnostic ->
                diagnostic.code() == ProgramDiagnosticCode.CAPABILITY_MISSING));
        var compiled = definition.compile(graph, Set.of(
                AcceleratorProgramCapabilities.APPLY_VECTOR,
                AcceleratorProgramCapabilities.KINETIC_IMPACT
        ));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var runtime = new FakeRuntime(List.of());
        var transaction = new ProgramActionTransaction();

        var result = AcceleratorProgramExecutionBridge.execute(
                compiled.program(), 40L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(2, transaction.size());
        assertTrue(runtime.applied.isEmpty());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of(
                "vector:caster:CONTROLLED:1.0,0.0,0.0",
                "impact:caster:MAXIMUM:1.0,0.0,0.0"
        ), runtime.applied);
        transaction.release();
    }

    @Test
    void incomingProjectileCollectionDrivesAFlowLoop() {
        var foreach = CommonProgramNodeIds.collection("entity", "foreach");
        var graph = new ProgramGraph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                AcceleratorProgramNodeCatalog.ACCELERATOR), new JsonObject()),
                        node(1, AcceleratorProgramNodeIds.INCOMING_PROJECTILES, new JsonObject()),
                        directionNode(2, 0.0, 0.0, 1.0),
                        node(3, foreach, new JsonObject()),
                        node(4, AcceleratorProgramNodeIds.REDIRECT_PROJECTILE, new JsonObject())
                ),
                List.of(
                        edge(0, "flow", 3, "flow"),
                        edge(1, "entities", 3, "values"),
                        edge(3, "body", 4, "flow"),
                        edge(3, "value", 4, "projectile"),
                        edge(2, "direction", 4, "direction"),
                        edge(4, "flow", 3, "flow")
                )
        );
        var definition = AbilityProgramDefinitions.require(
                AcceleratorProgramNodeCatalog.ACCELERATOR);
        var compiled = definition.compile(
                graph,
                Set.of(AcceleratorProgramCapabilities.REDIRECT_PROJECTILE)
        );
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var runtime = new FakeRuntime(List.of("arrow", "fireball"));
        var transaction = new ProgramActionTransaction();

        var result = AcceleratorProgramExecutionBridge.execute(
                compiled.program(), 80L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(2, transaction.size());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of(
                "redirect:arrow:0.0,0.0,1.0",
                "redirect:fireball:0.0,0.0,1.0"
        ), runtime.applied);
        transaction.release();
    }

    @Test
    void shockwaveAndDisplacementNodesReceiveTypedSpatialTargets() {
        var graph = new ProgramGraph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                AcceleratorProgramNodeCatalog.ACCELERATOR), new JsonObject()),
                        node(1, AcceleratorProgramNodeIds.CASTER, new JsonObject()),
                        directionNode(2, 0.0, 1.0, 0.0),
                        worldPositionNode(3, 4.5, 65.0, -2.5),
                        blockPositionNode(4, 1, 64, 1),
                        blockPositionNode(5, 2, 64, 1),
                        shockwaveNode(6, 1.5f, true, 4),
                        strengthNode(7, AcceleratorProgramNodeIds.DISPLACE_ENTITY, 1),
                        strengthNode(8, AcceleratorProgramNodeIds.DISPLACE_BLOCK, 0)
                ),
                List.of(
                        edge(0, "flow", 6, "flow"),
                        edge(6, "flow", 7, "flow"),
                        edge(7, "flow", 8, "flow"),
                        edge(3, "position", 6, "position"),
                        edge(2, "direction", 6, "direction"),
                        edge(1, "entity", 7, "entity"),
                        edge(3, "position", 7, "destination"),
                        edge(4, "position", 8, "block"),
                        edge(5, "position", 8, "destination")
                )
        );
        var definition = AbilityProgramDefinitions.require(
                AcceleratorProgramNodeCatalog.ACCELERATOR);
        var compiled = definition.compile(graph, Set.of(
                AcceleratorProgramCapabilities.APPLY_VECTOR,
                AcceleratorProgramCapabilities.KINETIC_IMPACT
        ));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var runtime = new FakeRuntime(List.of());
        var transaction = new ProgramActionTransaction();

        var result = AcceleratorProgramExecutionBridge.execute(
                compiled.program(), 120L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(3, transaction.size());
        assertTrue(transaction.commit().successful());
        assertEquals(List.of(
                "shockwave:4.5,65.0,-2.5:1.5:true:4:0.0,1.0,0.0",
                "displace_entity:caster:4.5,65.0,-2.5:STANDARD",
                "displace_block:1,64,1:2,64,1:CONTROLLED"
        ), runtime.applied);
        transaction.release();
    }

    @Test
    void connectedShockwaveDataAcceptsOpenActionFlowRoot() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, AcceleratorProgramNodeIds.LOOK_TARGET, new JsonObject()),
                        node(2, CommonProgramNodeIds.ENTITY_POSITION, new JsonObject()),
                        node(3, AcceleratorProgramNodeIds.CASTER, new JsonObject()),
                        node(4, CommonProgramNodeIds.ENTITY_LOOK_DIRECTION, new JsonObject()),
                        strengthNode(5, AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE, 1)
                ),
                List.of(
                        edge(1, "entity", 2, "entity"),
                        edge(2, "position", 5, "position"),
                        edge(3, "entity", 4, "entity"),
                        edge(4, "direction", 5, "direction")
                )
        );
        var definition = AbilityProgramDefinitions.require(
                AcceleratorProgramNodeCatalog.ACCELERATOR);

        var compiled = definition.compile(graph, Set.of(
                AcceleratorProgramCapabilities.KINETIC_SHOCKWAVE));

        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        assertEquals(5, compiled.program().entryNodeId());
    }

    @Test
    void visualKineticImpactGraphAcceptsOpenActionFlowRoot() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, AcceleratorProgramNodeIds.LOOK_TARGET, new JsonObject()),
                        node(2, AcceleratorProgramNodeIds.CASTER, new JsonObject()),
                        node(3, CommonProgramNodeIds.ENTITY_LOOK_DIRECTION, new JsonObject()),
                        strengthNode(4, AcceleratorProgramNodeIds.KINETIC_IMPACT, 1)
                ),
                List.of(
                        edge(1, "entity", 4, "entity"),
                        edge(2, "entity", 3, "entity"),
                        edge(3, "direction", 4, "direction")
                )
        );
        var definition = AbilityProgramDefinitions.require(
                AcceleratorProgramNodeCatalog.ACCELERATOR);

        var compiled = definition.compile(graph, Set.of(
                AcceleratorProgramCapabilities.KINETIC_IMPACT));

        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        assertEquals(4, compiled.program().entryNodeId());
    }

    private static ProgramGraph.Node node(
            int id,
            net.minecraft.resources.Identifier type,
            JsonObject configuration
    ) {
        var nodeType = AbilityProgramDefinitions.require(
                AcceleratorProgramNodeCatalog.ACCELERATOR).nodeLookup().find(type);
        assertNotNull(nodeType, type.toString());
        return new ProgramGraph.Node(id, type, nodeType.schemaVersion(), configuration);
    }

    private static ProgramGraph.Node strengthNode(
            int id,
            net.minecraft.resources.Identifier type,
            int strength
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("strength", strength);
        return node(id, type, configuration);
    }

    private static ProgramGraph.Node shockwaveNode(
            int id,
            float power,
            boolean destroyBlocks,
            int radius
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("power", power);
        configuration.addProperty("destroy_blocks", destroyBlocks);
        configuration.addProperty("radius", radius);
        return node(id, AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE, configuration);
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

    private static final class FakeRuntime implements AcceleratorProgramRuntime {
        private final List<?> projectiles;
        private final List<String> applied = new ArrayList<>();

        private FakeRuntime(List<?> projectiles) {
            this.projectiles = projectiles;
        }

        @Override
        public Object caster() {
            return "caster";
        }

        @Override
        public Optional<Object> lookTarget() {
            return Optional.of("look_target");
        }

        @Override
        public List<?> incomingProjectiles() {
            return projectiles;
        }

        @Override
        public ProgramActionTransaction.ProgramAction applyVector(
                Object entity,
                ProgramDirection direction,
                AcceleratorProgramStrength strength
        ) {
            return action("vector:" + entity + ":" + strength + ":" + directionKey(direction));
        }

        @Override
        public ProgramActionTransaction.ProgramAction kineticImpact(
                Object entity,
                ProgramDirection direction,
                AcceleratorProgramStrength strength
        ) {
            return action("impact:" + entity + ":" + strength + ":" + directionKey(direction));
        }

        @Override
        public ProgramActionTransaction.ProgramAction kineticShockwave(
                ProgramWorldPosition position,
                ProgramDirection direction,
                float power,
                boolean destroyBlocks,
                int radius
        ) {
            return action("shockwave:" + positionKey(position) + ":" + power
                    + ":" + destroyBlocks + ":" + radius
                    + ":" + directionKey(direction));
        }

        @Override
        public ProgramActionTransaction.ProgramAction redirectProjectile(
                Object projectile,
                ProgramDirection direction
        ) {
            return action("redirect:" + projectile + ":" + directionKey(direction));
        }

        @Override
        public ProgramActionTransaction.ProgramAction displaceEntity(
                Object entity,
                ProgramWorldPosition destination,
                AcceleratorProgramStrength strength
        ) {
            return action("displace_entity:" + entity + ":" + positionKey(destination)
                    + ":" + strength);
        }

        @Override
        public ProgramActionTransaction.ProgramAction displaceBlock(
                ProgramBlockPosition block,
                ProgramBlockPosition destination,
                AcceleratorProgramStrength strength
        ) {
            return action("displace_block:" + blockKey(block) + ":" + blockKey(destination)
                    + ":" + strength);
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

        private static String directionKey(ProgramDirection direction) {
            return direction.x() + "," + direction.y() + "," + direction.z();
        }

        private static String positionKey(ProgramWorldPosition position) {
            return position.x() + "," + position.y() + "," + position.z();
        }

        private static String blockKey(ProgramBlockPosition position) {
            return position.x() + "," + position.y() + "," + position.z();
        }
    }
}
