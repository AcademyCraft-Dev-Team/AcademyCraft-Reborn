package org.academy.internal.client.ability.electromaster.mine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class OreScanCacheTest {
    private static final OreGeometry ORE = new OreGeometry(0x77CCCC, List.of(OreGeometry.CUBE));

    private static OreScanCache.Source source(java.util.function.Predicate<OreScanCache.CellPos> loaded,
                                               java.util.function.Function<OreScanCache.CellPos, OreGeometry> read) {
        return new OreScanCache.Source() {
            public boolean loaded(OreScanCache.CellPos cell) { return loaded.test(cell); }
            public boolean empty(OreScanCache.CellPos cell) { return false; }
            public OreGeometry read(int x, int y, int z) { return read.apply(new OreScanCache.CellPos(x, y, z)); }
        };
    }

    private static void tick(OreScanCache scan, OreScanCache.Source source, long tick) {
        var time = new AtomicLong();
        scan.scan(source, tick, 1_250_000, () -> time.getAndAdd(500));
    }

    @Test void publishesNearbyResultsBeforeTheOuterSweepAndInThreeDimensionalOrder() {
        var scan = new OreScanCache();
        scan.moveTo(-1, 65, 15, 64);
        var reads = new ArrayList<OreScanCache.CellPos>();
        var source = source(_ -> true, pos -> { reads.add(pos); return ORE; });
        tick(scan, source, 0);
        assertNotNull(scan.oreAt(-1, 65, 15));
        assertNotNull(scan.oreAt(-1, 69, 15));
        assertNotNull(scan.oreAt(0, 65, 16));
        int previous = -1;
        for (var pos : reads.subList(0, 512)) {
            int distance = (pos.x() + 1) * (pos.x() + 1) + (pos.y() - 65) * (pos.y() - 65)
                    + (pos.z() - 15) * (pos.z() - 15);
            assertTrue(distance >= previous, "Near scan must advance outwards, including the Y axis");
            previous = distance;
        }
        assertNull(scan.oreAt(60, 65, 15), "Near results must be visible while far results are still pending");
    }

    @Test void keepsOverTenThousandMatchesAndMakesOuterProgressWhileMoving() {
        var scan = new OreScanCache();
        var source = source(cell -> Math.abs(cell.x()) <= 16 && Math.abs(cell.y()) <= 16 && Math.abs(cell.z()) <= 16,
                _ -> ORE);
        for (int tick = 0; tick < 160; tick++) {
            scan.moveTo(tick % 2, 0, 0, 64);
            tick(scan, source, tick);
        }
        assertEquals(125 * 512, scan.cells().stream().mapToInt(OreScanCache.Cell::count).sum());
        assertNotNull(scan.oreAt(23, 23, 23));
    }

    @Test void movementRetainsKnownResultsAndPrioritizesTheNewNeighbourhood() {
        var scan = new OreScanCache();
        var source = source(_ -> true, _ -> ORE);
        scan.moveTo(0, 0, 0, 64);
        tick(scan, source, 0);
        scan.moveTo(32, -32, 0, 64);
        tick(scan, source, 1);
        assertNotNull(scan.oreAt(0, 0, 0));
        assertNotNull(scan.oreAt(32, -32, 0));
    }

    @Test void replacementAndChunkReloadRejectStaleMeshResults() {
        var scan = new OreScanCache();
        scan.moveTo(0, 0, 0, 64);
        scan.update(0, 0, 0, ORE);
        var cell = scan.cells().iterator().next();
        long version = cell.revision;
        var snapshot = cell.snapshot();
        scan.update(0, 0, 0, null);
        assertNotNull(snapshot[0], "Background snapshot must remain unchanged");
        assertFalse(cell.accept(version, OreMesh.build(snapshot)));
        assertNull(scan.oreAt(0, 0, 0));
        scan.invalidateChunk(0, 0);
        assertFalse(scan.contains(cell));
        scan.update(0, 0, 0, ORE);
        assertFalse(scan.contains(cell), "Old chunk results cannot replace a new cell at the same position");
    }

    @Test void unloadedPositionsAreNeverReadAndEvictionHandlesTheLastCell() {
        var scan = new OreScanCache();
        scan.moveTo(0, 0, 0, 64);
        scan.update(0, 0, 0, ORE);
        tick(scan, source(_ -> false, _ -> { fail("Must not read unloaded terrain"); return null; }), 0);
        assertTrue(scan.cells().isEmpty());
    }

    @Test void meshUsesFilledFacesAndRemovesSharedFacesOfSameOre() {
        var snapshot = new OreGeometry[512];
        snapshot[0] = ORE;
        assertEquals(24, OreMesh.build(snapshot).vertexCount());
        snapshot[1] = ORE;
        assertEquals(40, OreMesh.build(snapshot).surfaceVertexCount());
        assertEquals(44, OreMesh.build(snapshot).vertexCount(), "Retain one shared cap for exact range clipping");
        snapshot[1] = new OreGeometry(0xCC7777, List.of(OreGeometry.CUBE));
        assertEquals(48, OreMesh.build(snapshot).vertexCount());
        snapshot[1] = null;
        snapshot[0] = new OreGeometry(0x77CCCC, List.of(new OreGeometry.Box(0.25f, 0, 0.25f, 0.75f, 0.5f, 0.75f)));
        var buffer = java.nio.ByteBuffer.wrap(OreMesh.build(snapshot).vertices()).order(java.nio.ByteOrder.nativeOrder());
        assertEquals(0.25f, buffer.getFloat());
    }

    @Test void distantTeleportsDoNotOverflowRangeChecks() {
        var cell = new OreScanCache.CellPos(30_000_000, 64, 30_000_000);
        assertTrue(cell.distanceSquared(-30_000_000, 64, -30_000_000) > 80L * 80);
        assertFalse(cell.fullyInside(-30_000_000, 64, -30_000_000, 80));
        assertTrue(new OreScanCache.CellPos(0, 0, 0).fullyInside(0, 0, 0, 64));
        assertFalse(new OreScanCache.CellPos(64, 0, 0).fullyInside(0, 0, 0, 64));
    }
}
