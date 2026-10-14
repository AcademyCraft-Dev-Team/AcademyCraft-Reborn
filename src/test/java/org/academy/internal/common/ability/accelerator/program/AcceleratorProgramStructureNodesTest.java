package org.academy.internal.common.ability.accelerator.program;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramVmResult;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P30 vector-operation node family executed through the real compiler, VM and execution bridge.
 *
 * <p>Reality level: catalog, schemas, compiler, program VM, bridge executors, value types and
 * action accounting are the production implementations; the accelerator runtime is a scripted
 * implementation of the real {@link AcceleratorProgramRuntime} interface because a dedicated
 * world fixture is out of scope for this unit test.</p>
 */
class AcceleratorProgramStructureNodesTest {
    private static final StructureRef STRUCTURE = new StructureRef(
            UUID.fromString("11111111-2222-3333-4444-555555555555"),
            Identifier.parse("minecraft:overworld"),
            Identifier.parse("academy:native"),
            0L);

    @Test
    void catalogExposesTheFullStructureFamilyWithExplicitAvailability() {
        var definition = AbilityProgramDefinitions.require(
                AcceleratorProgramNodeCatalog.ACCELERATOR);
        var catalog = definition.editorCatalog();
        for (var id : List.of(
                AcceleratorProgramNodeIds.STRUCTURE_EXTRACT,
                AcceleratorProgramNodeIds.STRUCTURE_CONTROL,
                AcceleratorProgramNodeIds.STRUCTURE_MOVE,
                AcceleratorProgramNodeIds.STRUCTURE_ROTATE,
                AcceleratorProgramNodeIds.STRUCTURE_LAUNCH,
                AcceleratorProgramNodeIds.STRUCTURE_RELEASE,
                AcceleratorProgramNodeIds.STRUCTURE_STATICIZE
        )) {
            assertNotNull(AcceleratorProgramNodeCatalog.INSTANCE.find(id), id.toString());
            assertNotNull(definition.executors().find(id), id.toString());
            assertNotNull(catalog.entry(id), id.toString());
            assertEquals(Set.of(AcceleratorProgramCapabilities.STRUCTURE),
                    AcceleratorProgramNodeCatalog.INSTANCE.find(id).scope().requiredCapabilities(),
                    id.toString());
        }
        var extract = catalog.entry(AcceleratorProgramNodeIds.STRUCTURE_EXTRACT)
                .defaultConfiguration().getAsJsonObject();
        assertEquals(1, extract.get("tier").getAsInt());
        assertEquals(0, extract.get("budget").getAsInt());
        var move = catalog.entry(AcceleratorProgramNodeIds.STRUCTURE_MOVE)
                .defaultConfiguration().getAsJsonObject();
        assertEquals(1.0f, move.get("duration_seconds").getAsFloat());
        var launch = catalog.entry(AcceleratorProgramNodeIds.STRUCTURE_LAUNCH)
                .defaultConfiguration().getAsJsonObject();
        assertEquals(AcceleratorProgramStrength.STANDARD.wireId(), launch.get("strength").getAsInt());
        var invalidExtract = extract.deepCopy();
        invalidExtract.addProperty("tier", 3);
        assertNull(catalog.schema(AcceleratorProgramNodeIds.STRUCTURE_EXTRACT, invalidExtract));
        var rotate = catalog.entry(AcceleratorProgramNodeIds.STRUCTURE_ROTATE);
        assertTrue(rotate.visible());
        assertTrue(catalog.entry(AcceleratorProgramNodeIds.STRUCTURE_STATICIZE).visible());
    }

    @Test
    void createMoveRotateReleaseGraphRunsThroughTheRealBridge() {
        var runtime = new ScriptedRuntime();
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                AcceleratorProgramNodeCatalog.ACCELERATOR), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, AcceleratorProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(1, 0)),
                        node(3, AcceleratorProgramNodeIds.STRUCTURE_CONTROL, new JsonObject()),
                        node(4, AcceleratorProgramNodeIds.STRUCTURE_MOVE, moveConfiguration(1.0f)),
                        node(5, AcceleratorProgramNodeIds.STRUCTURE_ROTATE, new JsonObject()),
                        node(6, AcceleratorProgramNodeIds.STRUCTURE_RELEASE, new JsonObject()),
                        node(7, AcceleratorProgramNodeIds.STRUCTURE_RELEASE, new JsonObject()),
                        worldPositionNode(8, 12.5, 70.0, 10.5)
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(2, "flow", 3, "flow"),
                        edge(3, "flow", 4, "flow"),
                        edge(4, "flow", 5, "flow"),
                        edge(5, "flow", 6, "flow"),
                        edge(6, "flow", 7, "flow"),
                        edge(1, "position", 2, "host"),
                        edge(2, "structure", 3, "structure"),
                        edge(3, "control", 4, "control"),
                        edge(8, "position", 4, "target"),
                        edge(3, "control", 5, "control"),
                        edge(3, "control", 6, "control"),
                        edge(3, "control", 7, "control")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        AcceleratorProgramNodeCatalog.ACCELERATOR)
                .compile(graph, Set.of(AcceleratorProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var transaction = new ProgramActionTransaction();

        var result = AcceleratorProgramExecutionBridge.execute(
                compiled.program(), 400L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status(),
                () -> String.valueOf(result.diagnostic()));
        assertEquals(List.of(
                "extract:BlockPos{x=10, y=64, z=10}:1:0",
                "control:" + STRUCTURE,
                "move:" + STRUCTURE + ":target=12.5,70.0,10.5:anchor=null:1.0s",
                "rotate:" + STRUCTURE + ":unavailable",
                "release:" + STRUCTURE + ":released",
                "release:" + STRUCTURE + ":noop"
        ), runtime.applied);
        assertTrue(runtime.structureRetained, "release must retain the structure");
        assertFalse(runtime.controlBroken, "rotate must not break the active control");
        assertEquals(0, transaction.size());
    }

    @Test
    void launchAndStaticizeGraphReportsBoundedImpulseAndUnavailableStaticization() {
        var runtime = new ScriptedRuntime();
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                AcceleratorProgramNodeCatalog.ACCELERATOR), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, AcceleratorProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(2, 64)),
                        node(3, AcceleratorProgramNodeIds.STRUCTURE_CONTROL, new JsonObject()),
                        node(4, AcceleratorProgramNodeIds.STRUCTURE_LAUNCH, strengthConfiguration(2)),
                        node(5, AcceleratorProgramNodeIds.STRUCTURE_STATICIZE, new JsonObject()),
                        directionNode(6, 0.0, 1.0, 0.0)
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(2, "flow", 3, "flow"),
                        edge(3, "flow", 4, "flow"),
                        edge(4, "flow", 5, "flow"),
                        edge(1, "position", 2, "host"),
                        edge(2, "structure", 3, "structure"),
                        edge(3, "control", 4, "control"),
                        edge(6, "direction", 4, "direction"),
                        edge(2, "structure", 5, "structure")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        AcceleratorProgramNodeCatalog.ACCELERATOR)
                .compile(graph, Set.of(AcceleratorProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());

        var result = AcceleratorProgramExecutionBridge.execute(
                compiled.program(), 500L, runtime, new ProgramActionTransaction());

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status(),
                () -> String.valueOf(result.diagnostic()));
        assertEquals(List.of(
                "extract:BlockPos{x=10, y=64, z=10}:2:64",
                "control:" + STRUCTURE,
                "launch:" + STRUCTURE + ":0.0,1.0,0.0:MAXIMUM:impulse=75.0",
                "staticize:" + STRUCTURE + ":unavailable"
        ), runtime.applied);
        assertTrue(runtime.trajectoryDamageSettled == false,
                "launch must not settle preset-trajectory damage");
        assertTrue(runtime.contentOwnerRetained,
                "unavailable staticization must preserve the unique content owner");
        assertEquals(75.0, runtime.launchImpulse, 1.0e-9);
    }

    @Test
    void pendingExtractPublishesNoStructureReference() {
        var runtime = new ScriptedRuntime();
        runtime.extractPending = true;
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                AcceleratorProgramNodeCatalog.ACCELERATOR), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, AcceleratorProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(0, 0))
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(1, "position", 2, "host")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        AcceleratorProgramNodeCatalog.ACCELERATOR)
                .compile(graph, Set.of(AcceleratorProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());

        var result = AcceleratorProgramExecutionBridge.execute(
                compiled.program(), 600L, runtime, new ProgramActionTransaction());

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(List.of("extract-pending:BlockPos{x=10, y=64, z=10}"), runtime.applied);
        assertFalse(runtime.publishedReference,
                "a pending capture must not publish a structure reference");
    }

    private static final class ScriptedRuntime implements AcceleratorProgramRuntime {
        private final List<String> applied = new ArrayList<>();
        private final ProgramStructureReference structure =
                new ProgramStructureReference(STRUCTURE, UUID.fromString(
                        "66666666-7777-8888-9999-000000000000"));
        private final ProgramStructureControl control = new ProgramStructureControl(
                STRUCTURE,
                UUID.fromString("66666666-7777-8888-9999-000000000000"),
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210"),
                UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"));
        private boolean extractPending;
        private boolean publishedReference;
        private boolean controlActive;
        private boolean controlBroken;
        private boolean structureRetained = true;
        private boolean trajectoryDamageSettled;
        private boolean contentOwnerRetained = true;
        private double launchImpulse;

        @Override
        public Object caster() {
            return "caster";
        }

        @Override
        public Optional<Object> lookTarget() {
            return Optional.empty();
        }

        @Override
        public List<?> incomingProjectiles() {
            return List.of();
        }

        @Override
        public ProgramActionTransaction.ProgramAction applyVector(
                Object entity,
                ProgramDirection direction,
                AcceleratorProgramStrength strength
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProgramActionTransaction.ProgramAction rewriteMotion(
                Object entity,
                ProgramVector motion,
                float power
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProgramActionTransaction.ProgramAction kineticImpact(
                Object entity,
                ProgramDirection direction,
                AcceleratorProgramStrength strength
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProgramActionTransaction.ProgramAction kineticShockwave(
                ProgramWorldPosition position,
                ProgramDirection direction,
                float power,
                boolean destroyBlocks,
                int radius
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProgramActionTransaction.ProgramAction redirectProjectile(
                Object projectile,
                ProgramDirection direction
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProgramActionTransaction.ProgramAction displaceEntity(
                Object entity,
                ProgramWorldPosition destination,
                AcceleratorProgramStrength strength
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProgramActionTransaction.ProgramAction displaceBlock(
                ProgramBlockPosition block,
                ProgramBlockPosition destination,
                AcceleratorProgramStrength strength
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StructureOperation extractStructure(ProgramBlockPosition host, int tier, int budget) {
            var seed = new BlockPos(host.x(), host.y(), host.z());
            if (extractPending) {
                applied.add("extract-pending:" + seed);
                return StructureOperation.result(
                        ProgramStructureOperationResult.pending("capture:PENDING:copying"));
            }
            applied.add("extract:" + seed + ":" + tier + ":" + budget);
            publishedReference = true;
            return StructureOperation.structure(structure,
                    ProgramStructureOperationResult.completed("committed", STRUCTURE));
        }

        @Override
        public StructureOperation controlStructure(ProgramStructureReference target) {
            applied.add("control:" + target.reference());
            controlActive = true;
            return StructureOperation.control(control,
                    ProgramStructureOperationResult.completed("control_acquired", STRUCTURE));
        }

        @Override
        public StructureOperation moveStructure(
                ProgramStructureControl token,
                ProgramWorldPosition target,
                ProgramStructureAnchor anchor,
                double seconds
        ) {
            requireControl(token);
            applied.add("move:" + token.reference() + ":target="
                    + (target == null ? "null" : target.x() + "," + target.y() + "," + target.z())
                    + ":anchor=" + (anchor == null ? "null" : anchor.localPosition())
                    + ":" + seconds + "s");
            return StructureOperation.result(
                    ProgramStructureOperationResult.completed("move", token.reference()));
        }

        @Override
        public StructureOperation rotateStructure(
                ProgramStructureControl token,
                ProgramStructurePose pose
        ) {
            if (!controlActive || !token.equals(control)) {
                controlBroken = true;
                return StructureOperation.rejected("control_token_stale");
            }
            applied.add("rotate:" + token.reference() + ":unavailable");
            return StructureOperation.unavailable("finite_torque_control_unavailable");
        }

        @Override
        public StructureOperation launchStructure(
                ProgramStructureControl token,
                ProgramDirection direction,
                AcceleratorProgramStrength strength
        ) {
            requireControl(token);
            launchImpulse = switch (strength) {
                case CONTROLLED -> 25.0;
                case STANDARD -> 50.0;
                case MAXIMUM -> 75.0;
            };
            applied.add("launch:" + token.reference() + ":"
                    + direction.x() + "," + direction.y() + "," + direction.z()
                    + ":" + strength + ":impulse=" + launchImpulse);
            return StructureOperation.result(
                    ProgramStructureOperationResult.completed("launched", token.reference()));
        }

        @Override
        public StructureOperation releaseStructure(ProgramStructureControl token) {
            if (!token.equals(control)) {
                controlBroken = true;
                return StructureOperation.rejected("control_token_stale");
            }
            if (!controlActive) {
                applied.add("release:" + token.reference() + ":noop");
                return StructureOperation.result(ProgramStructureOperationResult.completed("release_noop"));
            }
            controlActive = false;
            structureRetained = true;
            applied.add("release:" + token.reference() + ":released");
            return StructureOperation.result(
                    ProgramStructureOperationResult.completed("released", token.reference()));
        }

        @Override
        public StructureOperation staticizeStructure(
                ProgramStructureReference target,
                ProgramBlockPosition destination
        ) {
            applied.add("staticize:" + target.reference() + ":unavailable");
            contentOwnerRetained = true;
            return StructureOperation.unavailable("safe_staticization_unavailable");
        }

        private void requireControl(ProgramStructureControl token) {
            if (!controlActive || !token.equals(control)) {
                controlBroken = true;
                throw new IllegalStateException("Scripted control token is stale");
            }
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
    }

    private static JsonObject extractConfiguration(int tier, int budget) {
        var configuration = new JsonObject();
        configuration.addProperty("tier", tier);
        configuration.addProperty("budget", budget);
        return configuration;
    }

    private static JsonObject moveConfiguration(float durationSeconds) {
        var configuration = new JsonObject();
        configuration.addProperty("duration_seconds", durationSeconds);
        return configuration;
    }

    private static JsonObject strengthConfiguration(int strength) {
        var configuration = new JsonObject();
        configuration.addProperty("strength", strength);
        return configuration;
    }

    private static ProgramGraph graph(
            List<ProgramGraph.Node> nodes,
            List<ProgramGraph.Edge> edges
    ) {
        return new ProgramGraph(nodes, edges);
    }

    private static ProgramGraph.Node node(
            int id,
            Identifier type,
            JsonObject configuration
    ) {
        var nodeType = AbilityProgramDefinitions.require(
                AcceleratorProgramNodeCatalog.ACCELERATOR).nodeLookup().find(type);
        assertNotNull(nodeType, type.toString());
        return new ProgramGraph.Node(id, type, nodeType.schemaVersion(), configuration);
    }

    private static ProgramGraph.Node worldPositionNode(int id, double x, double y, double z) {
        var configuration = new JsonObject();
        configuration.addProperty("dimension", "minecraft:overworld");
        configuration.addProperty("x", x);
        configuration.addProperty("y", y);
        configuration.addProperty("z", z);
        return node(id, CommonProgramNodeIds.WORLD_POSITION_CONSTANT, configuration);
    }

    private static ProgramGraph.Node blockPositionNode(int id, int x, int y, int z) {
        var configuration = new JsonObject();
        configuration.addProperty("dimension", "minecraft:overworld");
        configuration.addProperty("x", x);
        configuration.addProperty("y", y);
        configuration.addProperty("z", z);
        return node(id, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT, configuration);
    }

    private static ProgramGraph.Node directionNode(int id, double x, double y, double z) {
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
}
