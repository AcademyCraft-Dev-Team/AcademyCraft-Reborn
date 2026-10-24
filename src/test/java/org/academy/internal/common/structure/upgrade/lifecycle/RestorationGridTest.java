package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.upgrade.control.OrientationTargets;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RestorationGridTest {
    @Test
    void clearanceLiftPreservesEveryCellAndRejectsUnboundedDisplacement() {
        var cells = List.of(BlockPos.ZERO, new BlockPos(2, 1, 3));
        var alignment = RestorationGrid.align(new StructurePose(new Vec3(-30, 90, -50),
                new org.joml.Quaterniond().rotationY(Math.PI / 2)), cells);
        for (var cell : cells) assertEquals(alignment.target(cell).above(8), alignment.lifted(8).target(cell));
        assertEquals(alignment.rotation(), alignment.lifted(1).rotation());
        assertThrows(IllegalArgumentException.class, () -> alignment.lifted(-1));
        assertThrows(IllegalArgumentException.class, () -> alignment.lifted(9));
    }
    @Test
    void uprightBodyStaysAtItsCurrentSiteIncludingNegativeCoordinates() {
        var cells = List.of(BlockPos.ZERO, new BlockPos(2, 1, 3));
        var alignment = RestorationGrid.align(StructurePose.identity(new Vec3(-10.02, 64.01, -7.98)), cells);
        assertEquals(new BlockPos(-10, 64, -8), alignment.target(BlockPos.ZERO));
        assertEquals(new BlockPos(-8, 65, -5), alignment.target(cells.getLast()));
    }

    @Test
    void quarterTurnUsesRotatedCubeCentersNotLocalCorners() {
        var source = new StructurePose(new Vec3(10, 64, 20),
                OrientationTargets.axisAngle(new Vec3(0, 1, 0), -Math.PI / 2).rotation());
        var cells = List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(0, 0, 1));
        var alignment = RestorationGrid.align(source, cells);
        assertEquals(1, alignment.quarterTurns());
        assertEquals(new BlockPos(9, 64, 20), alignment.target(BlockPos.ZERO));
        assertEquals(new BlockPos(9, 64, 21), alignment.target(cells.get(1)));
        assertEquals(new BlockPos(8, 64, 20), alignment.target(cells.get(2)));
    }

    @Test
    void tiltedBodyRetainsCentroidAndDistinctCellsAfterUprighting() {
        var cells = List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(0, 1, 0));
        var source = new StructurePose(new Vec3(103.3, 70.3, -203.6),
                new org.joml.Quaterniond().rotateY(.62).rotateZ(.35));
        var alignment = RestorationGrid.align(source, cells);
        var center = new Vec3(5.0 / 6, 5.0 / 6, .5);
        assertTrue(source.localToWorldPoint(center).distanceTo(alignment.pose().localToWorldPoint(center)) <= Math.sqrt(.75) + 1e-9);
        assertEquals(3, cells.stream().map(alignment::target).distinct().count());
        assertEquals(new Vec3(0, 1, 0), alignment.pose().localToWorldDirection(new Vec3(0, 1, 0)));
    }
}
