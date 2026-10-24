package org.academy.api.common.structure.upgrade;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StructureCaptureExtractionsTest {
    @Test
    void foreignActorCannotCancelEvenWithTheCurrentControlId() {
        var host = new Object();
        var owner = UUID.randomUUID();
        var token = UUID.randomUUID();
        assertTrue(StructureCaptureExtractions.owns(host, owner, token, host, owner, token));
        assertFalse(StructureCaptureExtractions.owns(host, owner, token, host, UUID.randomUUID(), token));
    }

    @Test
    void staleTokenAndForeignHostCannotCancelTheOwnersSuccessor() {
        var host = new Object();
        var owner = UUID.randomUUID();
        var previous = UUID.randomUUID();
        var successor = UUID.randomUUID();
        assertFalse(StructureCaptureExtractions.owns(host, owner, successor, host, owner, previous));
        assertFalse(StructureCaptureExtractions.owns(host, owner, successor, new Object(), owner, successor));
        assertFalse(StructureCaptureExtractions.owns(null, owner, successor, null, owner, successor));
    }

    @Test
    void committingCaptureDoesNotCompleteExtraction() {
        var pending = new StructureOperation.Result<StructureExtraction.Snapshot>(
                StructureOperation.Status.RUNNING, "capture_pending", Optional.of(UUID.randomUUID()), Optional.empty());
        assertFalse(StructureCaptureExtractions.arrived(pending));
        assertFalse(StructureCaptureExtractions.failed(pending));
        assertFalse(StructureCaptureExtractions.arrived(result(StructureOperation.Status.RUNNING, StructureExtraction.Phase.EXTRACTING)));
    }

    @Test
    void stableArrivalAllowsNodeHandoffBeforeEntireHoldWindow() {
        assertTrue(StructureCaptureExtractions.arrived(result(StructureOperation.Status.RUNNING, StructureExtraction.Phase.HOVERING)));
        assertTrue(StructureCaptureExtractions.arrived(result(StructureOperation.Status.COMPLETED, StructureExtraction.Phase.RELEASED)));
        assertFalse(StructureCaptureExtractions.failed(result(StructureOperation.Status.RUNNING, StructureExtraction.Phase.HOVERING)));
    }

    @Test
    void blockedOrExpiredMotionCannotReturnACompletedStructure() {
        assertTrue(StructureCaptureExtractions.failed(result(StructureOperation.Status.BLOCKED, StructureExtraction.Phase.BLOCKED)));
        assertTrue(StructureCaptureExtractions.failed(result(StructureOperation.Status.COMPLETED, StructureExtraction.Phase.EXPIRED)));
        assertFalse(StructureCaptureExtractions.arrived(result(StructureOperation.Status.DENIED, StructureExtraction.Phase.HOVERING)));
    }

    @Test
    void terrainWaitDoesNotCountAsArrivalOrRetryCapture() {
        var pending = new StructureOperation.Result<StructureExtraction.Snapshot>(
                StructureOperation.Status.TERRAIN_PENDING, "terrain_pending", Optional.empty(), Optional.empty());
        assertFalse(StructureCaptureExtractions.failed(pending));
        assertFalse(StructureCaptureExtractions.arrived(pending));
    }

    @Test
    void earlyLaunchCannotPretendToHaveClearedTheSource() {
        var observed = result(StructureOperation.Status.COMPLETED, StructureExtraction.Phase.RELEASED);
        var launched = new StructureOperation.Result<>(observed.status(), "extraction_launched",
                observed.operationId(), observed.value());
        assertFalse(StructureCaptureExtractions.arrived(launched));
        assertTrue(StructureCaptureExtractions.failed(launched));
        var cancelled = result(StructureOperation.Status.COMPLETED, StructureExtraction.Phase.CANCELLED);
        assertFalse(StructureCaptureExtractions.arrived(cancelled));
        assertTrue(StructureCaptureExtractions.failed(cancelled));
    }

    private static StructureOperation.Result<StructureExtraction.Snapshot> result(
            StructureOperation.Status status, StructureExtraction.Phase phase) {
        var id = UUID.randomUUID();
        return new StructureOperation.Result<>(status,
                phase == StructureExtraction.Phase.RELEASED ? "extraction_hover_completed" : "observed",
                Optional.of(id), Optional.of(
                new StructureExtraction.Snapshot(id, UUID.randomUUID(), phase, 1, 0, 10, Optional.empty())));
    }
}
