package org.academy.api.common.structure.upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureControlRequestsTest {
    private static final UUID CONTROL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final StructureOrientation.Limits ANGULAR_LIMITS = new StructureOrientation.Limits(
            5.0, 20.0, 500.0, 200, 0.01, 0.05, 0.0, 1_024);
    private static final StructureExtraction.Limits EXTRACTION_LIMITS = new StructureExtraction.Limits(
            10.0, 50.0, 2_000.0, 0.5, 1_024);

    @Test
    void angularRequestsCannotSmuggleTranslationOrExceedTheirOwnLifetimeAndSpeedBudgets() {
        assertThrows(IllegalArgumentException.class, () -> new StructureOrientation.RotateRequest(
                CONTROL, StructurePose.identity(new Vec3(1, 0, 0)), ANGULAR_LIMITS));
        assertThrows(IllegalArgumentException.class, () -> spin(new Vec3(0, 1, 0), 5.01, 100));
        assertThrows(IllegalArgumentException.class, () -> spin(new Vec3(0, 1, 0), 2.0, 201));
        assertThrows(IllegalArgumentException.class, () -> spin(new Vec3(0, 1, 0), 2.0, 0));
    }

    @Test
    void signedAxesRemainUsableEvenWhenNaiveLengthSquaredWouldOverflowOrUnderflow() {
        var large = spin(new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 0), -2.0, 100);
        var small = spin(new Vec3(0, -Double.MIN_VALUE, 0), 2.0, 100);

        assertEquals(1.0, large.axis().length(), 1.0e-12);
        assertTrue(large.axis().x > 0 && large.axis().y < 0);
        assertEquals(-2.0, large.angularSpeed());
        assertEquals(new Vec3(0, -1, 0), small.axis());
        assertEquals(StructureOrientation.AxisFrame.LOCAL, large.frame());
    }

    @Test
    void invalidAxesAndNonfiniteControlInputsNeverReachAProvider() {
        assertThrows(IllegalArgumentException.class, () -> spin(Vec3.ZERO, 2.0, 100));
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> spin(new Vec3(value, 1, 0), 2.0, 100));
            assertThrows(IllegalArgumentException.class, () -> spin(new Vec3(0, 1, 0), value, 100));
            assertThrows(IllegalArgumentException.class, () -> extraction(value));
            assertThrows(IllegalArgumentException.class, () -> new StructureExtraction.Limits(
                    10.0, 50.0, 2_000.0, value, 1_024));
        }
    }

    @Test
    void planCannotChangeWhenCallerReusesListsOrMutableBlockPositions() {
        var blocked = new BlockPos.MutableBlockPos(3, 4, 5);
        var blockers = new ArrayList<BlockPos>();
        blockers.add(blocked);
        var path = new ArrayList<Vec3>();
        path.add(new Vec3(0, 10, 0));
        var plan = plan(path, blockers);

        blocked.set(7, 8, 9);
        blockers.clear();
        path.clear();

        assertEquals(List.of(new BlockPos(3, 4, 5)), plan.blockedCells());
        assertEquals(List.of(new Vec3(0, 10, 0)), plan.worldPath());
        assertThrows(UnsupportedOperationException.class, () -> plan.worldPath().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.blockedCells().clear());
    }

    @Test
    void previewCollectionsRemainBoundedEvenBeforeServerAuthorization() {
        assertThrows(IllegalArgumentException.class, () -> plan(
                Collections.nCopies(StructureExtraction.MAX_PATH_POINTS + 1, Vec3.ZERO), List.of()));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(),
                Collections.nCopies(StructureExtraction.MAX_BLOCKED_CELLS + 1, BlockPos.ZERO)));
        assertThrows(IllegalArgumentException.class, () -> plan(
                List.of(new Vec3(30_000_001.0, 0, 0)), List.of()));
    }

    @Test
    void resetPlansCannotClaimAMissingBaselineAndSpinCannotClaimAFixedTarget() {
        assertThrows(IllegalArgumentException.class, () -> new StructureOrientation.Plan(
                StructureOrientation.Kind.RESET, StructureOrientation.BaselineSource.NOT_APPLICABLE,
                Optional.of(StructurePose.identity(Vec3.ZERO)), ANGULAR_LIMITS, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new StructureOrientation.Plan(
                StructureOrientation.Kind.SPIN, StructureOrientation.BaselineSource.NOT_APPLICABLE,
                Optional.of(StructurePose.identity(Vec3.ZERO)), ANGULAR_LIMITS, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new StructureOrientation.Plan(
                StructureOrientation.Kind.ROTATE_TO, StructureOrientation.BaselineSource.NOT_APPLICABLE,
                Optional.empty(), ANGULAR_LIMITS, 1, 1));
    }

    @Test
    void precapturePlanningRejectsNonPhysicalEmptyOversizedAndOverflowingSourceSelections() {
        assertThrows(IllegalArgumentException.class, () -> capture(new StructureCaptureRequest(
                List.of(BlockPos.ZERO), 1, 1, StructureCaptureRequest.ALLOW, false)));
        assertThrows(IllegalArgumentException.class, () -> capture(new StructureCaptureRequest(
                List.of(), 1, 1, StructureCaptureRequest.ALLOW, true)));
        assertThrows(IllegalArgumentException.class, () -> capture(new StructureCaptureRequest(
                List.of(BlockPos.ZERO, new BlockPos(1, 0, 0)), 1, 2, StructureCaptureRequest.ALLOW, true)));
        assertThrows(IllegalArgumentException.class, () -> capture(new StructureCaptureRequest(
                List.of(new BlockPos(Integer.MIN_VALUE, 0, 0), new BlockPos(Integer.MAX_VALUE, 0, 0)),
                2, 24, StructureCaptureRequest.ALLOW, true)));
        assertThrows(IllegalArgumentException.class, () -> capture(new StructureCaptureRequest(
                List.of(BlockPos.ZERO, new BlockPos(0, 2, 0)), 2, 2, StructureCaptureRequest.ALLOW, true)));
    }

    @Test
    void precapturePreviewDoesNotNeedAReferenceAndKeepsSourceAndPathImmutable() {
        var source = new BlockPos.MutableBlockPos(1, 2, 3);
        var blocked = new BlockPos.MutableBlockPos(1, 6, 3);
        var request = capture(new StructureCaptureRequest(List.of(source), 1, 1,
                StructureCaptureRequest.ALLOW, true));
        var path = new ArrayList<>(List.of(new Vec3(1.5, 5.5, 3.5)));
        var preview = new StructureExtraction.CapturePlan(UUID.randomUUID(), UUID.randomUUID(),
                Identifier.parse("minecraft:overworld"), "a".repeat(64), 1, 200,
                request, path, List.of(blocked));
        source.set(9, 9, 9);
        blocked.set(8, 8, 8);
        path.clear();
        assertEquals(List.of(new BlockPos(1, 2, 3)), preview.request().capture().positions());
        assertEquals(List.of(new BlockPos(1, 6, 3)), preview.blockedCells());
        assertEquals(List.of(new Vec3(1.5, 5.5, 3.5)), preview.worldPath());
        assertThrows(IllegalArgumentException.class, () -> new StructureExtraction.CapturePlan(
                UUID.randomUUID(), UUID.randomUUID(), Identifier.parse("minecraft:overworld"),
                "not-a-content-digest", 1, 200, request, List.of(), List.of()));
    }

    @Test
    void captureBindingOnlyCarriesServerRecordIdsAndRequiresEveryIdentity() {
        var planId = UUID.randomUUID();
        var operationId = UUID.randomUUID();
        var binding = new StructureExtraction.BindCaptureRequest(CONTROL, planId, operationId);
        assertEquals(operationId, binding.captureOperationId());
        assertThrows(NullPointerException.class,
                () -> new StructureExtraction.BindCaptureRequest(CONTROL, planId, null));
        assertThrows(NullPointerException.class,
                () -> new StructureExtraction.BindCaptureRequest(CONTROL, null, operationId));
        assertThrows(NullPointerException.class,
                () -> new StructureExtraction.BindCaptureRequest(null, planId, operationId));
    }

    @Test
    void precaptureLiftAndHoverRetainIndependentFiniteBudgets() {
        var source = new StructureCaptureRequest(List.of(BlockPos.ZERO), 1, 1,
                StructureCaptureRequest.ALLOW, true);
        assertThrows(IllegalArgumentException.class, () -> new StructureExtraction.CaptureRequest(
                CONTROL, source, Double.NaN, 200, 200, EXTRACTION_LIMITS));
        assertThrows(IllegalArgumentException.class, () -> new StructureExtraction.CaptureRequest(
                CONTROL, source, 3, 0, 200, EXTRACTION_LIMITS));
        assertThrows(IllegalArgumentException.class, () -> new StructureExtraction.CaptureRequest(
                CONTROL, source, 3, 200, StructureExtraction.MAX_DURATION_TICKS + 1, EXTRACTION_LIMITS));
        var request = new StructureExtraction.CaptureRequest(CONTROL, source, 7, 300, 200, EXTRACTION_LIMITS);
        assertEquals(300, request.extractionTimeoutTicks());
        assertEquals(200, request.hoverTicks());
    }

    private static StructureExtraction.CaptureRequest capture(StructureCaptureRequest source) {
        return new StructureExtraction.CaptureRequest(CONTROL, source, 3, 200, 200, EXTRACTION_LIMITS);
    }

    private static StructureOrientation.SpinRequest spin(Vec3 axis, double speed, int duration) {
        return new StructureOrientation.SpinRequest(CONTROL, axis, StructureOrientation.AxisFrame.LOCAL,
                speed, duration, StructureOrientation.StopMode.BRAKE, ANGULAR_LIMITS);
    }

    private static StructureExtraction.PlanRequest extraction(double height) {
        return new StructureExtraction.PlanRequest(CONTROL, StructureExtraction.SourceKind.NEW_CAPTURE,
                StructurePose.identity(Vec3.ZERO), new Vec3(0.5, 0.5, 0.5), height,
                200, 200, EXTRACTION_LIMITS);
    }

    private static StructureExtraction.Plan plan(List<Vec3> path, List<BlockPos> blocked) {
        var reference = new StructureRef(UUID.randomUUID(), Identifier.parse("minecraft:overworld"),
                Identifier.parse("academy:sublevel"), 1);
        return new StructureExtraction.Plan(UUID.randomUUID(), reference, UUID.randomUUID(),
                1, 1, 1, 200, extraction(3.0), path, blocked);
    }
}
