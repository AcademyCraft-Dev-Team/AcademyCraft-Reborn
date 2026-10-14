package org.academy.internal.common.ability.electromaster.program;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.electromaster.MagneticStructureControl;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.LegacyMotionUnits;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.ability.electromaster.magnet.MagneticStructureService;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.ability.program.ProgramVmDiagnostic;
import org.academy.internal.common.ability.program.ProgramVmResult;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P31 electromaster magnetic structure node family executed through the real compiler, VM and
 * execution bridge.
 *
 * <p>Reality level: catalog, schemas, compiler, program VM, bridge executors, value types,
 * action accounting and the shared participation/launch calibration constants are the production
 * implementations; the electromaster runtime is a scripted implementation of the real
 * {@link ElectromasterProgramRuntime} interface because a dedicated world fixture is out of scope
 * for this unit test.</p>
 */
class ElectromasterProgramStructureNodesTest {
    private static final StructureRef STRUCTURE = new StructureRef(
            UUID.fromString("11111111-2222-3333-4444-555555555555"),
            Identifier.parse("minecraft:overworld"),
            Identifier.parse("academy:native"),
            0L);

    @Test
    void catalogExposesTheFullStructureFamilyWithExplicitAvailability() {
        var definition = AbilityProgramDefinitions.require(
                ElectromasterProgramNodeCatalog.ELECTROMASTER);
        var catalog = definition.editorCatalog();
        for (var id : List.of(
                ElectromasterProgramNodeIds.STRUCTURE_EXTRACT,
                ElectromasterProgramNodeIds.STRUCTURE_CONTROL,
                ElectromasterProgramNodeIds.STRUCTURE_MOVE,
                ElectromasterProgramNodeIds.STRUCTURE_ROTATE,
                ElectromasterProgramNodeIds.STRUCTURE_LAUNCH,
                ElectromasterProgramNodeIds.STRUCTURE_RELEASE,
                ElectromasterProgramNodeIds.STRUCTURE_STATICIZE
        )) {
            assertNotNull(ElectromasterProgramNodeCatalog.INSTANCE.find(id), id.toString());
            assertNotNull(definition.executors().find(id), id.toString());
            assertNotNull(catalog.entry(id), id.toString());
            assertEquals(Set.of(ElectromasterProgramCapabilities.STRUCTURE),
                    ElectromasterProgramNodeCatalog.INSTANCE.find(id).scope().requiredCapabilities(),
                    id.toString());
        }
        var extract = catalog.entry(ElectromasterProgramNodeIds.STRUCTURE_EXTRACT)
                .defaultConfiguration().getAsJsonObject();
        assertEquals(0, extract.get("budget").getAsInt());
        assertFalse(extract.get("force_magnetize").getAsBoolean());
        var move = catalog.entry(ElectromasterProgramNodeIds.STRUCTURE_MOVE)
                .defaultConfiguration().getAsJsonObject();
        assertEquals(1.0f, move.get("duration_seconds").getAsFloat());
        var launch = catalog.entry(ElectromasterProgramNodeIds.STRUCTURE_LAUNCH)
                .defaultConfiguration().getAsJsonObject();
        assertEquals(1.0f, launch.get("power").getAsFloat());
        var invalidExtract = extract.deepCopy();
        invalidExtract.addProperty("budget", 300);
        assertNull(catalog.schema(ElectromasterProgramNodeIds.STRUCTURE_EXTRACT, invalidExtract));
        assertTrue(catalog.entry(ElectromasterProgramNodeIds.STRUCTURE_ROTATE).visible());
        assertTrue(catalog.entry(ElectromasterProgramNodeIds.STRUCTURE_STATICIZE).visible());
    }

    @Test
    void createControlMoveReleaseGraphRunsThroughTheRealBridge() {
        var runtime = new ScriptedRuntime();
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                ElectromasterProgramNodeCatalog.ELECTROMASTER), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, ElectromasterProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(64, true)),
                        node(3, ElectromasterProgramNodeIds.STRUCTURE_CONTROL, new JsonObject()),
                        node(4, ElectromasterProgramNodeIds.STRUCTURE_MOVE, moveConfiguration(1.0f)),
                        node(6, ElectromasterProgramNodeIds.STRUCTURE_RELEASE, new JsonObject()),
                        node(7, ElectromasterProgramNodeIds.STRUCTURE_RELEASE, new JsonObject()),
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
                        ElectromasterProgramNodeCatalog.ELECTROMASTER)
                .compile(graph, Set.of(ElectromasterProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var transaction = new ProgramActionTransaction();

        var result = ElectromasterProgramExecutionBridge.execute(
                compiled.program(), 400L, runtime, transaction);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status(),
                () -> String.valueOf(result.diagnostic()));
        assertEquals(List.of(
                "extract:BlockPos{x=10, y=64, z=10}:64:true",
                "control:" + STRUCTURE,
                "move:" + STRUCTURE + ":target=12.5,70.0,10.5:anchor=null:1.0s:participation=0.75",
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
                                ElectromasterProgramNodeCatalog.ELECTROMASTER), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, ElectromasterProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(64, true)),
                        node(3, ElectromasterProgramNodeIds.STRUCTURE_CONTROL, new JsonObject()),
                        node(4, ElectromasterProgramNodeIds.STRUCTURE_MOVE, moveConfiguration(1.0f)),
                        node(5, ElectromasterProgramNodeIds.STRUCTURE_ROTATE, new JsonObject()),
                        node(8, ElectromasterProgramNodeIds.STRUCTURE_RELEASE, new JsonObject())
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
                        ElectromasterProgramNodeCatalog.ELECTROMASTER)
                .compile(graph, Set.of(ElectromasterProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());

        var result = ElectromasterProgramExecutionBridge.execute(
                compiled.program(), 410L, runtime, new ProgramActionTransaction());

        assertEquals(ProgramVmResult.Status.FAILED, result.status());
        assertEquals(5, result.nodeId());
        assertEquals(ProgramVmDiagnostic.ACTION_UNAVAILABLE, result.diagnostic());
        assertEquals("unavailable:finite_torque_control_unavailable", result.detail());
        assertEquals(List.of(
                "extract:BlockPos{x=10, y=64, z=10}:64:true",
                "control:" + STRUCTURE,
                "move:" + STRUCTURE + ":target=null:anchor=null:1.0s:participation=0.75",
                "rotate:" + STRUCTURE + ":unavailable"
        ), runtime.applied);
        assertTrue(runtime.structureRetained, "an unavailable rotate must retain the structure");
        assertFalse(runtime.controlBroken, "an unavailable rotate must not break the active control");
    }

    @Test
    void unavailableSableControlFailsAtTheControlNodeInsteadOfStarvingTheLaunch() {
        var runtime = new ScriptedRuntime();
        runtime.controlUnavailable = true;
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                ElectromasterProgramNodeCatalog.ELECTROMASTER), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, ElectromasterProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(0, true)),
                        node(3, ElectromasterProgramNodeIds.STRUCTURE_CONTROL, new JsonObject()),
                        node(4, ElectromasterProgramNodeIds.STRUCTURE_LAUNCH, powerConfiguration(1.0f)),
                        directionNode(6, 0.0, 1.0, 0.0)
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(2, "flow", 3, "flow"),
                        edge(3, "flow", 4, "flow"),
                        edge(1, "position", 2, "host"),
                        edge(2, "structure", 3, "structure"),
                        edge(3, "control", 4, "control"),
                        edge(6, "direction", 4, "direction")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        ElectromasterProgramNodeCatalog.ELECTROMASTER)
                .compile(graph, Set.of(ElectromasterProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());

        var result = ElectromasterProgramExecutionBridge.execute(
                compiled.program(), 420L, runtime, new ProgramActionTransaction());

        assertEquals(ProgramVmResult.Status.FAILED, result.status());
        assertEquals(3, result.nodeId(),
                "the rejected control, not the downstream launch, must own the failure");
        assertEquals(ProgramVmDiagnostic.ACTION_UNAVAILABLE, result.diagnostic());
        assertEquals("unavailable:sable_program_control", result.detail());
        assertEquals(List.of(
                "extract:BlockPos{x=10, y=64, z=10}:0:true",
                "control:" + STRUCTURE + ":unavailable"
        ), runtime.applied);
    }

    @Test
    void sableDirectControlChainExtractsControlsLaunchesAndReleasesIdempotently() {
        var runtime = new ScriptedRuntime();
        runtime.sableDirect = true;
        runtime.heavyMass = 4_096.0;
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                ElectromasterProgramNodeCatalog.ELECTROMASTER), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, ElectromasterProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(0, true)),
                        node(3, ElectromasterProgramNodeIds.STRUCTURE_CONTROL, new JsonObject()),
                        node(4, ElectromasterProgramNodeIds.STRUCTURE_LAUNCH, powerConfiguration(1.0f)),
                        node(5, ElectromasterProgramNodeIds.STRUCTURE_RELEASE, new JsonObject()),
                        node(6, ElectromasterProgramNodeIds.STRUCTURE_RELEASE, new JsonObject()),
                        directionNode(7, 0.0, 1.0, 0.0)
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(2, "flow", 3, "flow"),
                        edge(3, "flow", 4, "flow"),
                        edge(4, "flow", 5, "flow"),
                        edge(5, "flow", 6, "flow"),
                        edge(1, "position", 2, "host"),
                        edge(2, "structure", 3, "structure"),
                        edge(3, "control", 4, "control"),
                        edge(7, "direction", 4, "direction"),
                        edge(3, "control", 5, "control"),
                        edge(3, "control", 6, "control")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        ElectromasterProgramNodeCatalog.ELECTROMASTER)
                .compile(graph, Set.of(ElectromasterProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());

        var result = ElectromasterProgramExecutionBridge.execute(
                compiled.program(), 430L, runtime, new ProgramActionTransaction());

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status(),
                () -> String.valueOf(result.diagnostic()));
        assertEquals(List.of(
                "extract:BlockPos{x=10, y=64, z=10}:0:true",
                "control:" + STRUCTURE + ":sable-direct",
                "launch:" + STRUCTURE + ":0.0,1.0,0.0:1.0:speed=" + expectedLaunchSpeed(1.0f),
                "release:" + STRUCTURE + ":released",
                "release:" + STRUCTURE + ":noop"
        ), runtime.applied);
        assertTrue(runtime.massGateBypassed,
                "a heavy Sable body must bypass the magnetic traction mass gate");
        assertEquals(expectedLaunchSpeed(1.0f), runtime.launchSpeed, 1.0e-9,
                "Sable direct launch speed must keep the legacy magnetic calibration");
        assertFalse(runtime.trajectoryDamageSettled,
                "launch must not settle preset-trajectory damage");
        assertTrue(runtime.structureRetained, "release must retain the structure");
        assertFalse(runtime.controlBroken, "release must not break the active control");
    }

    @Test
    void nonMagneticExtractWithoutTheOverlayFailsAtTheExtractNodeAndKeepsTheSource() {
        var runtime = new ScriptedRuntime();
        runtime.rejectExtraction = true;
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                ElectromasterProgramNodeCatalog.ELECTROMASTER), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, ElectromasterProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(0, false))
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(1, "position", 2, "host")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        ElectromasterProgramNodeCatalog.ELECTROMASTER)
                .compile(graph, Set.of(ElectromasterProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());

        var result = ElectromasterProgramExecutionBridge.execute(
                compiled.program(), 450L, runtime, new ProgramActionTransaction());

        assertEquals(ProgramVmResult.Status.FAILED, result.status());
        assertEquals(2, result.nodeId());
        assertEquals(ProgramVmDiagnostic.ACTION_REJECTED, result.diagnostic());
        assertEquals("rejected:not_magnetic", result.detail());
        assertEquals(List.of("extract-rejected:not_magnetic"), runtime.applied);
        assertTrue(runtime.sourceRetained,
                "a rejected magnetic extraction must keep the source blocks");
        assertFalse(runtime.publishedReference,
                "a rejected extraction must not publish a structure reference");
    }

    @Test
    void launchAndStaticizeGraphReportsBoundedLaunchAndNoPresetDamage() {
        var runtime = new ScriptedRuntime();
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                ElectromasterProgramNodeCatalog.ELECTROMASTER), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, ElectromasterProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(0, true)),
                        node(3, ElectromasterProgramNodeIds.STRUCTURE_CONTROL, new JsonObject()),
                        node(4, ElectromasterProgramNodeIds.STRUCTURE_LAUNCH, powerConfiguration(2.0f)),
                        node(5, ElectromasterProgramNodeIds.STRUCTURE_STATICIZE, new JsonObject()),
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
                        ElectromasterProgramNodeCatalog.ELECTROMASTER)
                .compile(graph, Set.of(ElectromasterProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());

        var result = ElectromasterProgramExecutionBridge.execute(
                compiled.program(), 500L, runtime, new ProgramActionTransaction());

        assertEquals(ProgramVmResult.Status.FAILED, result.status());
        assertEquals(5, result.nodeId());
        assertEquals(ProgramVmDiagnostic.ACTION_UNAVAILABLE, result.diagnostic());
        assertEquals("unavailable:safe_staticization_unavailable", result.detail());
        assertEquals(List.of(
                "extract:BlockPos{x=10, y=64, z=10}:0:true",
                "control:" + STRUCTURE,
                "launch:" + STRUCTURE + ":0.0,1.0,0.0:2.0:speed=" + expectedLaunchSpeed(2.0f),
                "staticize:" + STRUCTURE + ":unavailable"
        ), runtime.applied);
        assertFalse(runtime.trajectoryDamageSettled,
                "launch must not settle preset-trajectory damage");
        assertTrue(runtime.contentOwnerRetained,
                "unavailable staticization must preserve the unique content owner");
        assertEquals(expectedLaunchSpeed(2.0f), runtime.launchSpeed, 1.0e-9);
    }

    @Test
    void pendingExtractKeepsTheFlowAndPublishesNoStructureReference() {
        var runtime = new ScriptedRuntime();
        runtime.extractPending = true;
        var graph = graph(
                List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(
                                ElectromasterProgramNodeCatalog.ELECTROMASTER), new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, ElectromasterProgramNodeIds.STRUCTURE_EXTRACT, extractConfiguration(0, true))
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(1, "position", 2, "host")
                )
        );
        var compiled = AbilityProgramDefinitions.require(
                        ElectromasterProgramNodeCatalog.ELECTROMASTER)
                .compile(graph, Set.of(ElectromasterProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());

        var result = ElectromasterProgramExecutionBridge.execute(
                compiled.program(), 600L, runtime, new ProgramActionTransaction());

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status(),
                "a pending capture must not be reported as a failure");
        assertEquals(List.of("extract-pending:BlockPos{x=10, y=64, z=10}"), runtime.applied);
        assertFalse(runtime.publishedReference,
                "a pending capture must not publish a structure reference");
    }

    private static double expectedLaunchSpeed(float power) {
        return LegacyMotionUnits.velocityPerSecond(MagneticStructureService.LAUNCH_SPEED_PER_TICK)
                * ProgramPowerScale.interpolate(ProgramPowerScale.require(power), 0.5, 1.0, 1.5);
    }

    private static final class ScriptedRuntime implements ElectromasterProgramRuntime {
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
        private boolean rejectExtraction;
        private boolean controlUnavailable;
        private boolean publishedReference;
        private boolean controlActive;
        private boolean controlBroken;
        private boolean structureRetained = true;
        private boolean trajectoryDamageSettled;
        private boolean contentOwnerRetained = true;
        private boolean sourceRetained = true;
        private boolean sableDirect;
        private boolean massGateBypassed;
        private double heavyMass;
        private double launchSpeed;

        @Override
        public Object caster() {
            return "caster";
        }

        @Override
        public Optional<Object> lookTarget() {
            return Optional.empty();
        }

        @Override
        public ProgramActionTransaction.ProgramAction arcDischarge(Object entity, float power) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProgramActionTransaction.ProgramAction magneticMove(
                Object target,
                ProgramWorldPosition destination,
                float power,
                ElectromasterProgramNodeCatalog.EnergyTargetType targetType,
                ElectromasterProgramNodeCatalog.MagneticMode mode,
                boolean forceMagnetize
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ProgramBlockPosition> chargeableBlocksAround(
                ProgramWorldPosition center,
                double radius
        ) {
            return List.of();
        }

        @Override
        public List<?> magneticEntitiesAround(ProgramWorldPosition center, double radius) {
            return List.of();
        }

        @Override
        public OptionalDouble entityEnergyFraction(Object entity) {
            return OptionalDouble.empty();
        }

        @Override
        public OptionalDouble blockEnergyFraction(ProgramBlockPosition block) {
            return OptionalDouble.empty();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public StructureOperation extractStructure(ProgramBlockPosition host, int budget,
                                                   boolean forceMagnetize) {
            var seed = new BlockPos(host.x(), host.y(), host.z());
            if (extractPending) {
                applied.add("extract-pending:" + seed);
                return StructureOperation.result(
                        ProgramStructureOperationResult.pending("capture:PENDING:copying"));
            }
            if (rejectExtraction) {
                applied.add("extract-rejected:not_magnetic");
                sourceRetained = true;
                return StructureOperation.rejected("not_magnetic");
            }            applied.add("extract:" + seed + ":" + budget + ":" + forceMagnetize);
            publishedReference = true;
            return StructureOperation.structure(structure,
                    ProgramStructureOperationResult.completed("committed", STRUCTURE));
        }

        @Override
        public StructureOperation controlStructure(ProgramStructureReference target) {
            if (controlUnavailable) {
                applied.add("control:" + target.reference() + ":unavailable");
                return StructureOperation.unavailable("sable_program_control");
            }
            if (!sableDirect && heavyMass > MagneticStructureControl.massBudget(0)) {
                applied.add("control:" + target.reference() + ":too-heavy");
                return StructureOperation.rejected("sable_traction_too_heavy");
            }
            applied.add("control:" + target.reference() + (sableDirect ? ":sable-direct" : ""));
            controlActive = true;
            massGateBypassed = sableDirect && heavyMass > MagneticStructureControl.massBudget(0);
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
            var participation = MagneticStructureControl.magneticParticipation(3, 4);
            applied.add("move:" + token.reference() + ":target="
                    + (target == null ? "null" : target.x() + "," + target.y() + "," + target.z())
                    + ":anchor=" + (anchor == null ? "null" : anchor.localPosition())
                    + ":" + seconds + "s:participation=" + participation);
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
                float power
        ) {
            requireControl(token);
            launchSpeed = expectedLaunchSpeed(power);
            applied.add("launch:" + token.reference() + ":"
                    + direction.x() + "," + direction.y() + "," + direction.z()
                    + ":" + power + ":speed=" + launchSpeed);
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

    private static JsonObject extractConfiguration(int budget, boolean forceMagnetize) {
        var configuration = new JsonObject();
        configuration.addProperty("budget", budget);
        configuration.addProperty("force_magnetize", forceMagnetize);
        return configuration;
    }

    private static JsonObject moveConfiguration(float durationSeconds) {
        var configuration = new JsonObject();
        configuration.addProperty("duration_seconds", durationSeconds);
        return configuration;
    }

    private static JsonObject powerConfiguration(float power) {
        var configuration = new JsonObject();
        configuration.addProperty("power", power);
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
                ElectromasterProgramNodeCatalog.ELECTROMASTER).nodeLookup().find(type);
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
