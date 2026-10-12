package org.academy.internal.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStructureOrientedBoxTest {
    private static final AABB UNIT = new AABB(0, 0, 0, 1, 1, 1);

    @Test
    void faceEdgeAndCornerContactDoNotBlockPlacementButPenetrationDoes() {
        var box = BlockStructureOrientedBox.axisAligned(UNIT);
        for (var offset : List.of(new Vec3(1, 0, 0), new Vec3(1, 1, 0), new Vec3(1, 1, 1))) {
            assertFalse(box.intersects(BlockStructureOrientedBox.axisAligned(UNIT.move(offset))));
        }
        assertTrue(box.intersects(BlockStructureOrientedBox.axisAligned(UNIT.move(0.999, 0, 0))));
        assertFalse(box.intersects(BlockStructureOrientedBox.axisAligned(UNIT.move(1.001, 0, 0))));
    }

    @Test
    void emptyCornerOfRotatedBoundsRemainsPlaceable() {
        var rod = BlockStructureOrientedBox.transform(new AABB(-1, -0.1, -0.1, 1, 0.1, 0.1),
                new StructurePose(Vec3.ZERO, new Quaterniond().rotationZ(Math.PI / 4)));
        var emptyCorner = BlockStructureOrientedBox.axisAligned(new AABB(0.55, -0.65, -0.05, 0.65, -0.55, 0.05));
        assertTrue(rod.bounds().intersects(emptyCorner.bounds()), "Broad phase intentionally overlaps");
        assertFalse(rod.intersects(emptyCorner), "An enclosing AABB must not replace the rotated shape");
        assertFalse(emptyCorner.intersects(rod));
    }

    @Test
    void edgeCrossAxisSeparatesCuboidsEvenWhenAllSixFaceAxesOverlap() {
        // This oblique pair overlaps on all six face normals. An edge cross product is
        // essential; testing only either cuboid's three axes reports a false collision.
        var local = new AABB(-1, -0.17, -0.28, 1, 0.17, 0.28);
        var firstPose = new StructurePose(Vec3.ZERO, 0.168421636456902, -0.8107967067431502,
                -0.13409451869916894, -0.5442991022980651);
        var secondPose = new StructurePose(new Vec3(0.6294672007206827, -0.3995164870284498, -0.4723671118263155),
                -0.2806547054997137, 0.5242850590304681, -0.7376441212430763, 0.31974875066811015);
        var first = BlockStructureOrientedBox.transform(local, firstPose);
        var second = BlockStructureOrientedBox.transform(local, secondPose);
        assertTrue(first.bounds().intersects(second.bounds()));
        assertFalse(first.intersects(second));
        assertFalse(second.intersects(first));
        assertTrue(first.intersects(BlockStructureOrientedBox.transform(local,
                new StructurePose(Vec3.ZERO, secondPose.rotation()))));
    }

    @Test
    void rotatedNeighbourFacesStillOnlyTouchAtLargeWorldCoordinates() {
        var pose = new StructurePose(new Vec3(20_000_000.25, 74, -20_000_000.5),
                new Quaterniond().rotationXYZ(0.71, -1.17, 2.31));
        var first = BlockStructureOrientedBox.transform(UNIT, pose);
        assertFalse(first.intersects(BlockStructureOrientedBox.transform(UNIT.move(1, 0, 0), pose)));
        assertFalse(first.intersects(BlockStructureOrientedBox.transform(UNIT.move(0, -1, 0), pose)));
        assertTrue(first.intersects(BlockStructureOrientedBox.transform(UNIT.move(0, -0.99, 0), pose)));
    }

    @Test
    void stairVoidAndSolidPortionRemainDistinctAfterPitchAndRoll() {
        var pose = new StructurePose(new Vec3(-120, 70, 83), new Quaterniond().rotationXYZ(0.37, 1.26, -0.59));
        var stair = List.of(
                BlockStructureOrientedBox.transform(new AABB(0, 0, 0, 1, 0.5, 1), pose),
                BlockStructureOrientedBox.transform(new AABB(0, 0.5, 0.5, 1, 1, 1), pose));
        var voidBox = BlockStructureOrientedBox.transform(new AABB(0.1, 0.6, 0.1, 0.4, 0.9, 0.4), pose);
        var solidBox = BlockStructureOrientedBox.transform(new AABB(0.1, 0.6, 0.6, 0.4, 0.9, 0.9), pose);
        assertTrue(stair.stream().noneMatch(box -> box.intersects(voidBox)));
        assertTrue(stair.stream().anyMatch(box -> box.intersects(solidBox)));
    }

    @Test
    void rigidMotionPreservesOverlapAndSeparation() {
        var firstLocal = new AABB(-0.4, -0.1, -0.3, 0.4, 0.1, 0.3);
        var secondLocal = new AABB(-0.2, -0.2, -0.2, 0.2, 0.2, 0.2);
        for (var rotation : List.of(new Quaterniond(), new Quaterniond().rotationXYZ(-0.92, 1.13, 0.48),
                new Quaterniond().rotationXYZ(Math.PI / 2, Math.PI / 4, Math.PI / 3))) {
            var pose = new StructurePose(new Vec3(-18000.25, 150, 11700.75), rotation);
            var first = BlockStructureOrientedBox.transform(firstLocal, pose);
            assertTrue(first.intersects(BlockStructureOrientedBox.transform(secondLocal.move(0.3, 0, 0), pose)));
            assertFalse(first.intersects(BlockStructureOrientedBox.transform(secondLocal.move(0.7, 0, 0), pose)));
        }
    }

    @Test
    void quaternionSignDoesNotChangeCollisionGeometry() {
        var pose = new StructurePose(new Vec3(4, 5, 6), 0.17, -0.28, 0.39, 0.87);
        var opposite = new StructurePose(pose.worldOrigin(), -pose.rotationX(), -pose.rotationY(), -pose.rotationZ(), -pose.rotationW());
        assertTrue(BlockStructureOrientedBox.transform(UNIT, pose)
                .intersects(BlockStructureOrientedBox.transform(UNIT, opposite)));
    }

    @Test
    void degenerateBoxesDoNotCollideAndNonFiniteBoxesAreRejected() {
        var unit = BlockStructureOrientedBox.axisAligned(UNIT);
        assertFalse(unit.intersects(BlockStructureOrientedBox.axisAligned(new AABB(0.5, 0.5, 0.5, 0.5, 0.8, 0.8))));
        assertThrows(IllegalArgumentException.class, () -> BlockStructureOrientedBox.axisAligned(
                new AABB(0, 0, 0, Double.POSITIVE_INFINITY, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> BlockStructureOrientedBox.axisAligned(
                new AABB(Double.NaN, 0, 0, 1, 1, 1)));
    }
}
