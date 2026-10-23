package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaptureSourceSnapshotTest {
    @Test
    void digestIsIndependentOfInputOrderButSensitiveToStateAndPosition() {
        var first = new CaptureSourceSnapshot.Cell(new BlockPos(1, 2, 3), "minecraft:stone");
        var second = new CaptureSourceSnapshot.Cell(new BlockPos(0, 2, 3), "minecraft:dirt");
        var ordered = CaptureSourceSnapshot.of(List.of(first, second));
        var reordered = CaptureSourceSnapshot.of(List.of(second, first));

        assertEquals(ordered.digest(), reordered.digest());
        assertNotEquals(ordered.digest(), CaptureSourceSnapshot.of(List.of(first,
                new CaptureSourceSnapshot.Cell(new BlockPos(0, 2, 3), "minecraft:grass_block"))).digest());
        assertNotEquals(ordered.digest(), CaptureSourceSnapshot.of(List.of(first,
                new CaptureSourceSnapshot.Cell(new BlockPos(0, 3, 3), "minecraft:dirt"))).digest());
        assertEquals(64, ordered.digest().length());
    }

    @Test
    void localizeRebasesOntoTheCaptureOriginAndKeepsTheDigestComparable() {
        var snapshot = CaptureSourceSnapshot.of(List.of(
                new CaptureSourceSnapshot.Cell(new BlockPos(-4, 60, 9), "minecraft:stone"),
                new CaptureSourceSnapshot.Cell(new BlockPos(-3, 61, 9), "minecraft:oak_planks")));

        assertEquals(new BlockPos(-4, 60, 9), snapshot.origin());
        var local = snapshot.localize(snapshot.origin());
        assertEquals(new BlockPos(0, 0, 0), local.cells().getFirst().position());
        assertEquals(new BlockPos(1, 1, 0), local.cells().getLast().position());
        assertEquals("minecraft:stone", local.cells().getFirst().state());
        assertThrows(IllegalArgumentException.class, () -> local.localize(new BlockPos(1, 0, 0)));
    }

    @Test
    void boundedSnapshotsRejectDuplicatesEmptySelectionsAndOversizedStateIds() {
        var cell = new CaptureSourceSnapshot.Cell(BlockPos.ZERO, "minecraft:stone");
        assertThrows(IllegalArgumentException.class, () -> CaptureSourceSnapshot.of(List.of()));
        assertThrows(IllegalArgumentException.class, () -> CaptureSourceSnapshot.of(List.of(cell, cell)));
        assertThrows(IllegalArgumentException.class, () -> new CaptureSourceSnapshot.Cell(BlockPos.ZERO, " "));
        assertThrows(IllegalArgumentException.class, () -> new CaptureSourceSnapshot.Cell(BlockPos.ZERO,
                "x".repeat(CaptureSourceSnapshot.MAX_STATE_LENGTH + 1)));
        var many = new java.util.ArrayList<CaptureSourceSnapshot.Cell>();
        for (int index = 0; index <= CaptureSourceSnapshot.MAX_CELLS; index++) {
            many.add(new CaptureSourceSnapshot.Cell(new BlockPos(index, 0, 0), "minecraft:stone"));
        }
        assertThrows(IllegalArgumentException.class, () -> CaptureSourceSnapshot.of(many));
    }
}
