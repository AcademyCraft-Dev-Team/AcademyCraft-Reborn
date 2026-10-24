package org.academy.internal.common.sublevel;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;
import org.joml.Quaterniond;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubLevelContactResolverTest {
    private static SubLevelContactResolver.Obstacle obstacle(AABB bounds) {
        var reference = new StructureRef(new UUID(0, 1), Identifier.parse("minecraft:overworld"),
                Identifier.parse("academy:sublevel"), 0);
        return new SubLevelContactResolver.Obstacle(
                SubLevelOrientedCollision.Box.of(bounds, StructurePose.identity(Vec3.ZERO)),
                reference, 1, 1, bounds.getCenter());
    }

    @Test
    void stepCandidatesIncludeTheApproachingSurface() {
        var entity = new AABB(0.1, 0, 0.2, 0.7, 1.8, 0.8);
        var step = obstacle(new AABB(1, 0, 0, 2, 0.5, 1));
        var result = SubLevelContactResolver.clipMovement(entity, new Vec3(0.8, 0, 0),
                List.of(step), List.of(), 0.6, true, 0, true);
        assertEquals(0.8, result.movement().x, 1.0E-6);
        assertEquals(0.5, result.movement().y, 1.0E-6);
    }

    @Test
    void structureStepCannotRaiseAnEntityThroughAHostCeiling() {
        var entity = new AABB(0.1, 0, 0.2, 0.7, 1.8, 0.8);
        var step = obstacle(new AABB(1, 0, 0, 2, 0.5, 1));
        var ceiling = Shapes.create(new AABB(0, 1.9, 0, 3, 2.9, 1));
        var result = SubLevelContactResolver.clipMovement(entity, new Vec3(0.8, 0, 0),
                List.of(step), List.of(ceiling), 0.6, true, 0, true);
        assertTrue(result.movement().x <= 0.3 + 1.0E-6);
        assertTrue(entity.maxY + result.movement().y <= 1.9 + 1.0E-6);
    }

    @Test
    void depenetrationCannotPushAnEntityThroughAHostWall() {
        var entity = new AABB(0.9, 0.2, 0.2, 1.5, 2, 0.8);
        var body = obstacle(new AABB(0, 0, 0, 1, 1, 1));
        var wall = Shapes.create(new AABB(1.55, 0, 0, 2.55, 3, 1));
        var result = SubLevelContactResolver.clipMovement(entity, Vec3.ZERO,
                List.of(body), List.of(wall), 0, true, 0, false);
        assertEquals(0.05, result.movement().x, 1.0E-6);
    }

    @Test
    void hostWallIsRecheckedAfterStructureChangesTheVerticalPath() {
        var entity = new AABB(0.1, 1.1, 0.2, 0.7, 2.9, 0.8);
        var floor = obstacle(new AABB(0, 0, 0, 3, 1, 1));
        var wall = Shapes.create(new AABB(1, 2, 0, 2, 3, 1));
        var result = SubLevelContactResolver.clipMovement(entity, new Vec3(0.8, -1.1, 0),
                List.of(floor), List.of(wall), 0, false, 0, false);
        assertEquals(-0.1, result.movement().y, 2.0E-6);
        assertTrue(result.movement().x <= 0.3 + 1.0E-6);
    }

    @Test
    void longShallowSlopeUsesTheNearbySurfaceInsteadOfItsDistantCorners() {
        double angle = Math.toRadians(5);
        var bounds = new AABB(-20, 0, -2, 20, 1, 2);
        var pose = new StructurePose(Vec3.ZERO, new Quaterniond().rotationZ(angle));
        var prototype = obstacle(bounds);
        var slope = new SubLevelContactResolver.Obstacle(SubLevelOrientedCollision.Box.of(bounds, pose),
                prototype.reference(), 1, 1, bounds.getCenter());
        double floor = 1 / Math.cos(angle) + 0.3 * Math.tan(angle);
        var actor = new AABB(-0.3, floor + 1.0E-6, -0.3, 0.3, floor + 1.800001, 0.3);
        for (int tick = 0; tick < 100; tick++) {
            var result = SubLevelContactResolver.clipMovement(actor, new Vec3(0.12, -0.0784, 0),
                    List.of(slope), List.of(Shapes.create(new AABB(-30, -10, -3, 30, -2, 3))),
                    0.6, true, 0, true);
            assertEquals(0.12, result.movement().x, 1.0E-6, "incline must not suppress walking at tick " + tick);
            assertEquals(0.12 * Math.tan(angle), result.movement().y, 3.0E-6,
                    "step must land back on the nearby plane without bouncing");
            actor = actor.move(result.movement());
        }
    }

    @Test
    void continuousWalkingCrossesAHostGroundAndSettledStructureSeam() {
        for (double offset : new double[]{-0.0015, 0, 0.0015}) {
            var ground = Shapes.create(new AABB(-8, -1, -2, 0, 0, 2));
            var structure = obstacle(new AABB(0, -1 + offset, -2, 12, offset, 2));
            var actor = new AABB(-2.3, 0, -0.3, -1.7, 1.8, 0.3);
            for (int tick = 0; tick < 80; tick++) {
                var result = SubLevelContactResolver.clipMovement(actor, new Vec3(0.1, -0.0784, 0),
                        List.of(structure), List.of(ground), 0.6, true, 0, true);
                assertEquals(0.1, result.movement().x, 1.0E-6, "ground seam blocked tick " + tick);
                actor = actor.move(result.movement());
                assertTrue(Math.abs(actor.minY) < 0.002, "ground seam must not produce a visible bounce");
            }
        }
    }

    @Test
    void stepFromHostGroundOntoPhysicalSlabLandsAtItsActualHeight() {
        var ground = Shapes.create(new AABB(-5, -1, -2, 5, 0, 2));
        var slab = obstacle(new AABB(0, 0, -1, 3, 0.5, 1));
        var actor = new AABB(-0.8, 0, -0.3, -0.2, 1.8, 0.3);
        var result = SubLevelContactResolver.clipMovement(actor, new Vec3(0.4, -0.0784, 0),
                List.of(slab), List.of(ground), 0.6, true, 0, true);
        assertEquals(0.4, result.movement().x, 1.0E-6);
        assertEquals(0.5, result.movement().y, 2.0E-6);
    }

    @Test
    void steppingBesideACliffDoesNotLeaveAPlayerSuspendedAtMaxStepHeight() {
        var ground = Shapes.create(new AABB(-5, -1, -2, 0, 0, 2));
        var corner = obstacle(new AABB(0, -0.1, -0.1, 0.1, 0.08, 0.1));
        var actor = new AABB(-0.7, 0, -0.3, -0.1, 1.8, 0.3);
        var result = SubLevelContactResolver.clipMovement(actor, new Vec3(1, -0.0784, 0),
                List.of(corner), List.of(ground), 0.6, true, 0, true);
        assertEquals(1, result.movement().x, 1.0E-6);
        assertEquals(0, result.movement().y, 2.0E-6);
    }

    @Test
    void structureOuterBoundsDoNotBlockWalkingOrJumpingThroughAnEmptyCorridor() {
        var sides = List.of(obstacle(new AABB(0, 0, 0, 1, 4, 8)),
                obstacle(new AABB(4, 0, 0, 5, 4, 8)));
        var actor = new AABB(2.2, 0, 0.2, 2.8, 1.8, 0.8);
        var floor = Shapes.create(new AABB(-2, -1, -2, 7, 0, 10));
        for (var movement : List.of(new Vec3(0, 0, 0.2), new Vec3(0, 0.42, 0.2),
                new Vec3(0.2, 0.42, 0), new Vec3(-0.2, 0.42, 0))) {
            var result = SubLevelContactResolver.clipMovement(actor, movement, sides,
                    List.of(floor), 0.6, true, 0, true);
            assertEquals(movement.x, result.movement().x, 1.0E-8);
            assertEquals(movement.y, result.movement().y, 1.0E-8);
            assertEquals(movement.z, result.movement().z, 1.0E-8);
        }
    }

    @Test
    void rotatedEmptyOuterCornerHasNeitherAnInvisibleWallNorAnInvisibleCeiling() {
        var local = new AABB(0, 0, 0, 1, 4, 1);
        var base = obstacle(local);
        var rotated = new SubLevelContactResolver.Obstacle(SubLevelOrientedCollision.Box.of(local,
                new StructurePose(Vec3.ZERO, new Quaterniond().rotationY(Math.PI / 4))),
                base.reference(), 1, 1, local.getCenter());
        var actor = new AABB(0, 0.1, 0.5, 0.3, 1.9, 0.7);
        assertTrue(rotated.box().bounds().intersects(actor));
        var requested = new Vec3(0.15, 0.42, 0);
        var result = SubLevelContactResolver.clipMovement(actor, requested, List.of(rotated),
                List.of(), 0.6, true, 0, true);
        assertEquals(requested, result.movement());
    }

    @Test
    void jumpingAwayFromSettledGroundDoesNotStickToTheSupportingBody() {
        var actor = new AABB(0.2, 0.9985, 0.2, 0.8, 2.7985, 0.8);
        var support = obstacle(new AABB(0, 0, 0, 8, 1, 8));
        var result = SubLevelContactResolver.clipMovement(actor, new Vec3(0.1, 0.42, 0),
                List.of(support), List.of(Shapes.create(new AABB(-10, -1, -10, 10, 0, 10))),
                0.6, true, 0, true);
        assertEquals(0.1, result.movement().x, 1.0E-8);
        assertEquals(0.42, result.movement().y, 1.0E-8);
    }
}
