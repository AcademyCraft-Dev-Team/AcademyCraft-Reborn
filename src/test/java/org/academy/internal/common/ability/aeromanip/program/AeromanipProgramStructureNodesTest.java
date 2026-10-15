package org.academy.internal.common.ability.aeromanip.program;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.aeromanip.JetStructureControl;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramVmDiagnostic;
import org.academy.internal.common.ability.program.ProgramVmResult;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P32 aeromanip structure node family executed through the real compiler, VM and execution
 * bridge, plus the legacy {@code launch_block_structure} compatibility readback.
 *
 * <p>Reality level: catalog, schemas, compiler, program VM, bridge executors, value types and the
 * shared jet force/normalization constants are the production implementations; the aeromanip
 * runtime is a scripted implementation of the real {@link AeromanipProgramRuntime} interface
 * because a dedicated world fixture is out of scope for this unit test.</p>
 */
class AeromanipProgramStructureNodesTest {
    private static final StructureRef STRUCTURE = new StructureRef(
            UUID.fromString("11111111-2222-3333-4444-555555555555"),
            Identifier.parse("minecraft:overworld"),
            Identifier.parse("academy:native"),
            0L);
    private static final double MASS = 1.0;
    private static final float POWER = 1.0f;

    @Test
    void catalogExposesTheFullStructureFamilyWithExplicitAvailability() {
        var definition = AbilityProgramDefinitions.require(
                AeromanipProgramNodeCatalog.AEROMANIP);
        var catalog = definition.editorCatalog();
        for (var id : List.of(
                AeromanipProgramNodeIds.STRUCTURE_EXTRACT,
                AeromanipProgramNodeIds.STRUCTURE_CONTROL,
                AeromanipProgramNodeIds.STRUCTURE_MOVE,
                AeromanipProgramNodeIds.STRUCTURE_ROTATE,
                AeromanipProgramNodeIds.STRUCTURE_LAUNCH,
                AeromanipProgramNodeIds.STRUCTURE_RELEASE,
                AeromanipProgramNodeIds.STRUCTURE_STATICIZE
        )) {
            assertNotNull(AeromanipProgramNodeCatalog.INSTANCE.find(id), id.toString());
            assertNotNull(definition.executors().find(id), id.toString());
            assertNotNull(catalog.entry(id), id.toString());
            assertEquals(Set.of(AeromanipProgramCapabilities.STRUCTURE),
                    AeromanipProgramNodeCatalog.INSTANCE.find(id).scope().requiredCapabilities(),
                    id.toString());
        }
        var extract = catalog.entry(AeromanipProgramNodeIds.STRUCTURE_EXTRACT)
                .defaultConfiguration().getAsJsonObject();
        assertEquals(2, extract.get("radius").getAsInt());
        assertEquals(0, extract.get("budget").getAsInt());
        var move = catalog.entry(AeromanipProgramNodeIds.STRUCTURE_MOVE)
                .defaultConfiguration().getAsJsonObject();
        assertEquals(1.0f, move.get("duration_seconds").getAsFloat());
        var launch = catalog.entry(AeromanipProgramNodeIds.STRUCTURE_LAUNCH)
                .defaultConfiguration().getAsJsonObject();
        assertEquals(1.0f, launch.get("power").getAsFloat());
        assertEquals(8.0f, launch.get("duration_seconds").getAsFloat());
        assertTrue(launch.get("restore_when_settled").getAsBoolean());
        var invalidExtract = extract.deepCopy();
        invalidExtract.addProperty("radius", 9);
        assertNull(catalog.schema(AeromanipProgramNodeIds.STRUCTURE_EXTRACT, invalidExtract));
        invalidExtract.addProperty("radius", 2);
        invalidExtract.addProperty("budget", 300);
        assertNull(catalog.schema(AeromanipProgramNodeIds.STRUCTURE_EXTRACT, invalidExtract));
        assertTrue(catalog.entry(AeromanipProgramNodeIds.STRUCTURE_ROTATE).visible());
        assertTrue(catalog.entry(AeromanipProgramNodeIds.STRUCTURE_STATICIZE).visible());
    }

    @Test
    void legacyLaunchNodeKeepsItsPortsAndConfigurationReadback() {
        var definition = AbilityProgramDefinitions.require(
                AeromanipProgramNodeCatalog.AEROMANIP);
        var legacy = AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE;
        assertNotNull(definition.nodeLookup().find(legacy));
        assertNotNull(definition.executors().find(legacy));
        assertEquals(1, definition.nodeLookup().find(legacy).schemaVersion(),
                "the legacy node must not be bumped to a new schema version");
        var schema = definition.editorCatalog().schema(legacy, new JsonObject());
        assertTrue(schema.input("block").orElseThrow().required());
        assertTrue(schema.input("direction").orElseThrow().required());
        assertTrue(schema.input("flow").orElseThrow().required());
        assertTrue(schema.output("flow").isPresent());

        var configuration = new JsonObject();
        configuration.addProperty("power", 1.5f);
        configuration.addProperty("radius", 4);
        configuration.addProperty("duration", 6);
        configuration.addProperty("restore_when_settled", false);
        var parsed = AeromanipProgramNodeCatalog.BlockStructureLaunchConfiguration.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, configuration)
                .result().orElseThrow();
        assertEquals(1.5f, parsed.power());
        assertEquals(4, parsed.structureRadius());
        assertEquals(6, parsed.duration());
        assertFalse(parsed.restoreWhenSettled());

        assertEquals(AeromanipProgramNodeIds.STRUCTURE_LAUNCH,
                AeromanipProgramNodeCatalog.unifiedIdForLegacy(legacy),
                "the legacy id must map to the unified launch node");
        assertEquals(AeromanipProgramNodeIds.STRUCTURE_MOVE,
                AeromanipProgramNodeCatalog.unifiedIdForLegacy(
                        AeromanipProgramNodeIds.STRUCTURE_MOVE));
        var migratedExtract = AeromanipProgramNodeCatalog
                .legacyExtractConfiguration(configuration);
        assertEquals(4, migratedExtract.get("radius").getAsInt());
        assertEquals(0, migratedExtract.get("budget").getAsInt());
        var migratedLaunch = AeromanipProgramNodeCatalog
                .legacyLaunchConfiguration(configuration);
        assertEquals(1.5f, migratedLaunch.get("power").getAsFloat());
        assertEquals(6.0f, migratedLaunch.get("duration_seconds").getAsFloat());
        assertFalse(migratedLaunch.get("restore_when_settled").getAsBoolean());
    }

    @Test
    void createControlMoveReleaseGraphRunsThroughTheRealBridge() {
        var runtime = new ScriptedRuntime();
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                AeromanipProgramNodeCatalog.AEROMANIP), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, AeromanipProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(4, 64)),
                        node(3, AeromanipProgramNodeIds.STRUCTURE_CONTROL, new JsonObject()),
                        node(4, AeromanipProgramNodeIds.STRUCTURE_MOVE, moveConfiguration(1.0f)),
                        node(6, AeromanipProgramNodeIds.STRUCTURE_RELEASE, new JsonObject()),
                        node(7, AeromanipProgramNodeIds.STRUCTURE_RELEASE, new JsonObject()),
                        worldPositionNode(8, 12.5, 70.0, 10.5)
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(2, "flow", 3, "flow"),
                        edge(3, "flow", 4, "flow"),
                        edge(4, "flow", 6, "flow"),
                        edge(6, "flow", 7, "flow"),
                        edge(1, "position", 2, "host"),
                        edge(2, "structure", 3, "structure"),
                        edge(3, "control", 4, "control"),
                        edge(8, "position", 4, "target"),
                        edge(3, "control", 6, "control"),
                        edge(3, "control", 7, "control")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        AeromanipProgramNodeCatalog.AEROMANIP)
                .compile(graph, Set.of(AeromanipProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var transaction = new ProgramActionTransaction();

        var result = AeromanipProgramExecutionBridge.execute(
                compiled.program(), 400L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status(),
                () -> String.valueOf(result.diagnostic()));
        assertEquals(List.of(
                "extract:BlockPos{x=10, y=64, z=10}:4:64",
                "control:" + STRUCTURE,
                "move:" + STRUCTURE + ":target=12.5,70.0,10.5:anchor=null:1.0s:anchors=2"
                        + ":group=" + expectedSharedGroup() + ":cancelled=0.0",
                "release:" + STRUCTURE + ":released",
                "release:" + STRUCTURE + ":noop"
        ), runtime.applied);
        assertTrue(runtime.structureRetained, "release must retain the structure");
        assertFalse(runtime.controlBroken, "release must not break the active control");
        assertEquals(0, transaction.size());
    }

    @Test
    void unavailableRotateFailsAtTheRotateNodeAndPreservesTheControl() {
        var runtime = new ScriptedRuntime();
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                AeromanipProgramNodeCatalog.AEROMANIP), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, AeromanipProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(2, 0)),
                        node(3, AeromanipProgramNodeIds.STRUCTURE_CONTROL, new JsonObject()),
                        node(4, AeromanipProgramNodeIds.STRUCTURE_MOVE, moveConfiguration(1.0f)),
                        node(5, AeromanipProgramNodeIds.STRUCTURE_ROTATE, new JsonObject()),
                        node(8, AeromanipProgramNodeIds.STRUCTURE_RELEASE, new JsonObject())
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(2, "flow", 3, "flow"),
                        edge(3, "flow", 4, "flow"),
                        edge(4, "flow", 5, "flow"),
                        edge(5, "flow", 8, "flow"),
                        edge(1, "position", 2, "host"),
                        edge(2, "structure", 3, "structure"),
                        edge(3, "control", 4, "control"),
                        edge(3, "control", 5, "control"),
                        edge(3, "control", 8, "control")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        AeromanipProgramNodeCatalog.AEROMANIP)
                .compile(graph, Set.of(AeromanipProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());

        var result = AeromanipProgramExecutionBridge.execute(
                compiled.program(), 410L, runtime, new ProgramActionTransaction());

        assertEquals(ProgramVmResult.Status.FAILED, result.status());
        assertEquals(5, result.nodeId());
        assertEquals(ProgramVmDiagnostic.ACTION_UNAVAILABLE, result.diagnostic());
        assertEquals("unavailable:finite_torque_control_unavailable", result.detail());
        assertEquals(List.of(
                "extract:BlockPos{x=10, y=64, z=10}:2:0",
                "control:" + STRUCTURE,
                "move:" + STRUCTURE + ":target=null:anchor=null:1.0s:anchors=2"
                        + ":group=" + expectedSharedGroup() + ":cancelled=0.0",
                "rotate:" + STRUCTURE + ":unavailable"
        ), runtime.applied);
        assertTrue(runtime.structureRetained, "an unavailable rotate must retain the structure");
        assertFalse(runtime.controlBroken, "an unavailable rotate must not break the active control");
    }

    @Test
    void launchAndStaticizeGraphReportsBoundedLaunchAndNoPresetDamage() {
        var runtime = new ScriptedRuntime();
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                AeromanipProgramNodeCatalog.AEROMANIP), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, AeromanipProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(2, 64)),
                        node(3, AeromanipProgramNodeIds.STRUCTURE_CONTROL, new JsonObject()),
                        node(4, AeromanipProgramNodeIds.STRUCTURE_LAUNCH, launchConfiguration(2.0f, 8.0f)),
                        node(5, AeromanipProgramNodeIds.STRUCTURE_STATICIZE, new JsonObject()),
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
                        AeromanipProgramNodeCatalog.AEROMANIP)
                .compile(graph, Set.of(AeromanipProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());

        var result = AeromanipProgramExecutionBridge.execute(
                compiled.program(), 500L, runtime, new ProgramActionTransaction());

        assertEquals(ProgramVmResult.Status.FAILED, result.status());
        assertEquals(5, result.nodeId());
        assertEquals(ProgramVmDiagnostic.ACTION_UNAVAILABLE, result.diagnostic());
        assertEquals("unavailable:safe_staticization_unavailable", result.detail());
        assertEquals(List.of(
                "extract:BlockPos{x=10, y=64, z=10}:2:64",
                "control:" + STRUCTURE,
                "launch:" + STRUCTURE + ":0.0,1.0,0.0:2.0:8.0s:force=" + expectedLaunchForce(2.0f),
                "staticize:" + STRUCTURE + ":unavailable"
        ), runtime.applied);
        assertFalse(runtime.trajectoryDamageSettled,
                "launch must not settle preset-trajectory damage");
        assertTrue(runtime.contentOwnerRetained,
                "unavailable staticization must preserve the unique content owner");
        assertEquals(expectedLaunchForce(2.0f), runtime.launchForce, 1.0e-9);
    }

    @Test
    void pendingExtractKeepsTheFlowAndPublishesNoStructureReference() {
        var runtime = new ScriptedRuntime();
        runtime.extractPending = true;
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                AeromanipProgramNodeCatalog.AEROMANIP), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, AeromanipProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(2, 0))
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(1, "position", 2, "host")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        AeromanipProgramNodeCatalog.AEROMANIP)
                .compile(graph, Set.of(AeromanipProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());

        var result = AeromanipProgramExecutionBridge.execute(
                compiled.program(), 600L, runtime, new ProgramActionTransaction());

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status(),
                "a pending capture must not be reported as a failure");
        assertEquals(List.of("extract-pending:BlockPos{x=10, y=64, z=10}"), runtime.applied);
        assertFalse(runtime.publishedReference,
                "a pending capture must not publish a structure reference");
    }

    private static double expectedSharedGroup() {
        return JetStructureControl.combinedThrustForce(
                List.of(new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, 1.0)),
                MASS, POWER, 0).length();
    }

    private static double expectedLaunchForce(float power) {
        return JetStructureControl.limitedThrustForce(
                new Vec3(0.0, 1.0, 0.0), Vec3.ZERO, MASS, power, 0).length();
    }

    private static final class ScriptedRuntime implements AeromanipProgramRuntime {
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
        private double launchForce;

        @Override
        public Object caster() {
            return "caster";
        }

        @Override
        public Optional<Object> lookTarget() {
            return Optional.empty();
        }

        @Override
        public ProgramActionTransaction.ProgramAction airflowPush(
                Object entity,
                ProgramDirection direction,
                float power
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProgramActionTransaction.ProgramAction laminarCut(
                ProgramWorldPosition origin,
                ProgramDirection direction,
                float power,
                org.academy.internal.common.ability.aeromanip.charge.AeromanipChargeTier chargeTier,
                float chargeCostMultiplier,
                ProgramDirection planeDirection,
                AeromanipProgramNodeCatalog.BladePlaneMode planeMode
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProgramActionTransaction.ProgramAction placeTemporaryJetNozzle(
                Object target,
                ProgramDirection direction,
                AeromanipProgramNodeCatalog.NozzleTargetType targetType
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProgramActionTransaction.ProgramAction fireJets(int durationSeconds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int ownedJetNozzleCount(
                ProgramWorldPosition center,
                double radius,
                ProgramDirection direction
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProgramActionTransaction.ProgramAction launchBlockStructure(
                ProgramBlockPosition seed,
                ProgramDirection direction,
                AeromanipProgramNodeCatalog.BlockStructureLaunchConfiguration configuration
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StructureOperation extractStructure(
                ProgramBlockPosition host,
                int radius,
                int budget
        ) {
            var seed = new BlockPos(host.x(), host.y(), host.z());
            if (extractPending) {
                applied.add("extract-pending:" + seed);
                return StructureOperation.result(
                        ProgramStructureOperationResult.pending("capture:PENDING:copying"));
            }
            applied.add("extract:" + seed + ":" + radius + ":" + budget);
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
            var shared = JetStructureControl.combinedThrustForce(
                    List.of(new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, 1.0)),
                    MASS, POWER, 0).length();
            var cancelled = JetStructureControl.combinedThrustForce(
                    List.of(new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, -1.0)),
                    MASS, POWER, 0).length();
            applied.add("move:" + token.reference() + ":target="
                    + (target == null ? "null" : target.x() + "," + target.y() + "," + target.z())
                    + ":anchor=" + (anchor == null ? "null" : anchor.localPosition())
                    + ":" + seconds + "s:anchors=2"
                    + ":group=" + shared + ":cancelled=" + cancelled);
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
                float power,
                double seconds
        ) {
            requireControl(token);
            launchForce = JetStructureControl.limitedThrustForce(
                    new Vec3(direction.x(), direction.y(), direction.z()),
                    Vec3.ZERO, MASS, power, 0).length();
            applied.add("launch:" + token.reference() + ":"
                    + direction.x() + "," + direction.y() + "," + direction.z()
                    + ":" + power + ":" + seconds + "s:force=" + launchForce);
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

    private static JsonObject extractConfiguration(int radius, int budget) {
        var configuration = new JsonObject();
        configuration.addProperty("radius", radius);
        configuration.addProperty("budget", budget);
        return configuration;
    }

    private static JsonObject moveConfiguration(float durationSeconds) {
        var configuration = new JsonObject();
        configuration.addProperty("duration_seconds", durationSeconds);
        return configuration;
    }

    private static JsonObject launchConfiguration(float power, float durationSeconds) {
        var configuration = new JsonObject();
        configuration.addProperty("power", power);
        configuration.addProperty("duration_seconds", durationSeconds);
        configuration.addProperty("restore_when_settled", true);
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
                AeromanipProgramNodeCatalog.AEROMANIP).nodeLookup().find(type);
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
