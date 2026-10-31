package org.academy.internal.common.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.api.common.profiler.ZoneSlice;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.space.LoadedStructureProximity;
import org.academy.internal.common.structure.space.StructureBlockSummaries;
import org.academy.internal.common.structure.upgrade.control.OrientationBackendAccess;
import org.academy.internal.common.structure.upgrade.control.OrientationController;
import org.academy.internal.common.structure.upgrade.control.OrientationTargets;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramQueryProfilingTest {
    @Test
    void disabledQueriesReuseTheNoOpScopeAndDoNotChangeDistance() {
        AcademyProfiler.stopZoneCapture();
        assertSame(ProgramQueryProfiling.begin(ProgramQueryProfiling.Zone.MEASURE),
                ProgramQueryProfiling.begin(ProgramQueryProfiling.Zone.ENTITIES_AROUND));
        assertFalse(ProgramQueryProfiling.begin(ProgramQueryProfiling.Zone.MEASURE).enabled());
        assertEquals(4.5, LoadedStructureProximity.measure(body(), new Vec3(5.5, .5, .5)).distance());
    }

    @Test
    void enabledGeometryCountsActualBoxesWithoutChangingTheResult() {
        var body = body();
        var expected = LoadedStructureProximity.measure(body, new Vec3(5.5, .5, .5));
        AcademyProfiler.startZoneCapture();
        try {
            assertEquals(expected, LoadedStructureProximity.measure(body, new Vec3(5.5, .5, .5)));
        } finally {
            AcademyProfiler.stopZoneCapture();
        }
        var slice = slice(ProgramQueryProfiling.Zone.MEASURE);
        assertEquals(1L, slice.counters().get("calls"));
        assertEquals(2L, slice.counters().get("boxes"));
    }

    @Test
    void summaryCountersDistinguishUnreadBudgetRejectionFromMissingLoadedCells() {
        var reads = new AtomicInteger();
        AcademyProfiler.startZoneCapture();
        try {
            assertTrue(StructureBlockSummaries.scan(BlockPos.ZERO, new BlockPos(4096, 0, 0), 4096,
                    _ -> { fail("Over-budget query must not read"); return Optional.empty(); }).isEmpty());
            assertTrue(StructureBlockSummaries.scan(BlockPos.ZERO, new BlockPos(1, 0, 0), 2,
                    _ -> { reads.incrementAndGet(); return Optional.empty(); }).isEmpty());
            assertEquals(0, StructureBlockSummaries.scan(new BlockPos(1, 0, 0), BlockPos.ZERO, 1,
                    _ -> { fail("Empty bounds must not read"); return Optional.empty(); }).orElseThrow().scannedCells());
        } finally {
            AcademyProfiler.stopZoneCapture();
        }
        assertEquals(1, reads.get());
        var counts = slice(ProgramQueryProfiling.Zone.SUMMARY_SCAN).counters();
        assertEquals(3L, counts.get("calls"));
        assertEquals(1L, counts.get("budget_rejected"));
        assertEquals(1L, counts.get("missing_cell"));
        assertEquals(1L, counts.get("cells_read"));
        assertEquals(1L, counts.get("complete"));
    }

    @Test
    void queryExceptionsStillCloseTheirProfilingZone() {
        AcademyProfiler.startZoneCapture();
        try {
            assertThrows(IllegalArgumentException.class, () -> StructureBlockSummaries.scan(
                    BlockPos.ZERO, BlockPos.ZERO, 0, _ -> Optional.empty()));
            LoadedStructureProximity.measure(body(), Vec3.ZERO);
        } finally {
            AcademyProfiler.stopZoneCapture();
        }
        var zones = AcademyProfiler.snapshot().getZones().get(Thread.currentThread().getName());
        assertNotNull(zones);
        assertEquals(2, zones.childrenOf("root").stream()
                .filter(value -> value.name().startsWith("structure.query.")).count());
    }

    private static ZoneSlice slice(ProgramQueryProfiling.Zone zone) {
        var thread = AcademyProfiler.snapshot().getZones().get(Thread.currentThread().getName());
        assertNotNull(thread);
        return thread.topSlices(64, true).stream().filter(value -> value.name().equals(zone.label()))
                .findFirst().orElseThrow();
    }

    private static OrientationBackendAccess.Body body() {
        return new OrientationBackendAccess.Body(new StructureRef(UUID.randomUUID(), Identifier.parse("minecraft:overworld"),
                Identifier.parse("academy:native"), 1), UUID.randomUUID(), 1, 1,
                new OrientationController.State(StructurePose.identity(Vec3.ZERO), Vec3.ZERO, Vec3.ZERO, 1),
                Vec3.ZERO, Vec3.ZERO, List.of(new AABB(0, 0, 0, 1, 1, 1), new AABB(10, 0, 0, 11, 1, 1)),
                new OrientationTargets.Baselines(Optional.empty(), Optional.empty()), true);
    }
}
