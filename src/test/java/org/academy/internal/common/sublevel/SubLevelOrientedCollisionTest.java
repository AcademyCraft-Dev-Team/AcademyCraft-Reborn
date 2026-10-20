package org.academy.internal.common.sublevel;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubLevelOrientedCollisionTest {
    private static StructurePose yaw(double degrees, Vec3 origin) {
        double half = Math.toRadians(degrees) * 0.5;
        return new StructurePose(origin, 0.0, Math.sin(half), 0.0, Math.cos(half));
    }

    private static SubLevelOrientedCollision.Box unitBox(StructurePose pose) {
        return SubLevelOrientedCollision.Box.of(new AABB(0, 0, 0, 1, 1, 1), pose);
    }

    @Test
    void landingOnFlatTopStopsAtRealSurface() {
        var box = unitBox(StructurePose.identity(Vec3.ZERO));
        var entity = new AABB(0.25, 1.05, 0.25, 0.75, 1.85, 0.75);
        var clip = SubLevelOrientedCollision.clip(box, entity, Direction.Axis.Y, -0.5);
        assertTrue(clip.blocked());
        assertEquals(-0.05, clip.movement(), 1.0E-4);
        assertTrue(clip.normal().y > 0.0);
    }

    @Test
    void rotatedCornerInsideWorldAabbRemainsWalkable() {
        var box = unitBox(yaw(45.0, Vec3.ZERO));
        // This corridor overlaps the rotated cube's world AABB but stays outside the diamond.
        var entity = new AABB(0.0, 0.1, 0.5, 0.3, 1.7, 0.7);
        assertTrue(box.bounds().intersects(entity), "test must start inside the world AABB");
        var clip = SubLevelOrientedCollision.clip(box, entity, Direction.Axis.X, 0.15);
        assertFalse(clip.blocked(), "AABB-only collision would create an invisible wall here");
        assertEquals(0.15, clip.movement(), 1.0E-9);
    }

    @Test
    void rotatedFaceStopsBeforeWorldAabbHull() {
        var box = unitBox(yaw(45.0, Vec3.ZERO));
        var entity = new AABB(1.4, 0.1, 0.3, 1.9, 1.7, 0.7);
        var clip = SubLevelOrientedCollision.clip(box, entity, Direction.Axis.X, -0.5);
        assertTrue(clip.blocked());
        double finalMinX = 1.4 + clip.movement();
        assertTrue(finalMinX < 1.414, "must enter the empty part of the world AABB");
        assertTrue(finalMinX > 1.05, "must stop at the rotated face, not pass through");
    }

    @Test
    void hollowGapBetweenCellsIsNotBlocked() {
        var first = SubLevelOrientedCollision.Box.of(new AABB(0, 0, 0, 1, 1, 3),
                StructurePose.identity(Vec3.ZERO));
        var second = SubLevelOrientedCollision.Box.of(new AABB(2, 0, 0, 3, 1, 3),
                StructurePose.identity(Vec3.ZERO));
        var entity = new AABB(1.2, 0.1, -0.3, 1.8, 1.7, 0.3);
        assertFalse(SubLevelOrientedCollision.clip(first, entity, Direction.Axis.Z, 0.5).blocked());
        assertFalse(SubLevelOrientedCollision.clip(second, entity, Direction.Axis.Z, 0.5).blocked());
    }

    @Test
    void shallowOverlapProducesBoundedDepenetration() {
        var box = unitBox(StructurePose.identity(Vec3.ZERO));
        var entity = new AABB(0.25, 0.9, 0.25, 0.75, 1.7, 0.75);
        var push = SubLevelOrientedCollision.depenetration(box, entity);
        assertTrue(push.length() > 0.05);
        assertTrue(push.y > 0.05);
        assertTrue(push.length() <= 0.55 + 1.0E-9);
        assertEquals(0.0, push.x, 1.0E-9);
        assertEquals(0.0, push.z, 1.0E-9);
    }

    @Test
    void almostParallelAxesDoNotEraseARealOverlap() {
        var box = unitBox(yaw(0.001, Vec3.ZERO));
        var entity = new AABB(0.25, 0.9, 0.25, 0.75, 1.7, 0.75);
        var push = SubLevelOrientedCollision.depenetration(box, entity);
        assertEquals(0.1, push.y, 2.0E-6);
        assertEquals(0.0, push.x, 1.0E-9);
        assertEquals(0.0, push.z, 1.0E-9);
        assertEquals(Vec3.ZERO, SubLevelOrientedCollision.depenetration(box, entity.move(push)));
    }

    @Test
    void existingOverlapAllowsEscapeAndSlidingButRejectsInwardMovement() {
        var box = unitBox(StructurePose.identity(Vec3.ZERO));
        var entity = new AABB(0.95, 0.2, 0.2, 1.55, 2.0, 0.8);
        assertFalse(SubLevelOrientedCollision.clip(box, entity, Direction.Axis.X, 0.1).blocked());
        assertFalse(SubLevelOrientedCollision.clip(box, entity, Direction.Axis.Z, 0.1).blocked());
        assertEquals(0.0, SubLevelOrientedCollision.clip(box, entity, Direction.Axis.X, -0.1).movement());
    }

    @Test
    void rotatedContactReportsTheSurfaceNormalInsteadOfTheMovementAxis() {
        var box = unitBox(yaw(45.0, Vec3.ZERO));
        var entity = new AABB(1.4, 0.1, 0.3, 1.9, 1.7, 0.7);
        var clip = SubLevelOrientedCollision.clip(box, entity, Direction.Axis.X, -0.5);
        assertTrue(clip.blocked());
        assertEquals(Math.sqrt(0.5), clip.normal().x, 1.0E-9);
        assertEquals(Math.sqrt(0.5), clip.normal().z, 1.0E-9);
    }

    @Test
    void supportFollowsWorldUpAfterQuarterTurnAndDoesNotStickToTheSide() {
        var rotation = new org.joml.Quaterniond().rotationZ(Math.PI / 2.0);
        var pose = new StructurePose(Vec3.ZERO, rotation);
        var boxes = java.util.List.of(new AABB(0, 0, 0, 1, 1, 1));
        assertTrue(SubLevelOrientedCollision.supports(boxes, pose,
                new AABB(-0.7, 1.0, 0.2, -0.3, 2.8, 0.8), 0.1));
        assertFalse(SubLevelOrientedCollision.supports(boxes, pose,
                new AABB(-1.3, 0.5, 0.2, -0.8, 2.3, 0.8), 0.1));
    }

    @Test
    void unsupportedAirBesideAnEdgeIsNotCarried() {
        assertFalse(SubLevelOrientedCollision.supports(java.util.List.of(new AABB(0, 0, 0, 1, 1, 1)),
                StructurePose.identity(Vec3.ZERO), new AABB(1.01, 1, 0.2, 1.61, 2.8, 0.8), 0.35));
    }

    @Test
    void supportRequiresTouchingTopFace() {
        var pose = StructurePose.identity(Vec3.ZERO);
        var boxes = java.util.List.of(new AABB(0, 0, 0, 1, 1, 1));
        assertTrue(SubLevelOrientedCollision.supports(boxes, pose,
                new AABB(0.25, 1.0, 0.25, 0.75, 1.8, 0.75), 0.1));
        assertTrue(SubLevelOrientedCollision.supports(boxes, pose,
                new AABB(0.25, 1.08, 0.25, 0.75, 1.88, 0.75), 0.1));
        assertFalse(SubLevelOrientedCollision.supports(boxes, pose,
                new AABB(0.25, 1.3, 0.25, 0.75, 2.1, 0.75), 0.1));
    }
}
