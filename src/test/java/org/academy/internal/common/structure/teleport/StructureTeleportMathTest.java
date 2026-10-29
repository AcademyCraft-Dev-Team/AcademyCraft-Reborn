package org.academy.internal.common.structure.teleport;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.ability.teleport.targeting.TeleportStructures;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureTeleportMathTest {
    private static final double EPS = 1E-10;

    @Test void destinationCentersTheWholeBodyRatherThanItsOriginOrTargetedBlock() {
        var source = new StructurePose(new Vec3(100, 30, -40), new Quaterniond().rotationXYZ(.4, .7, -.2));
        var center = new Vec3(3.25, -2, .5);
        var wanted = new Vec3(-400, 120, 500);
        var rotation = new StructurePose(new Vec3(99, 99, 99), new Quaterniond().rotationXYZ(-.7, .1, 1.4));
        var result = StructureTeleportMath.destination(source, center, wanted, rotation);
        assertVector(wanted, result.localToWorldPoint(center));
        assertEquals(1, Math.abs(result.rotation().dot(rotation.rotation())), EPS);
        assertTrue(result.worldOrigin().distanceTo(wanted) > 1, "COM must not be confused with grid origin");
    }

    @Test void unspecifiedRotationPreservesTiltAndEveryRelativePoint() {
        var source = new StructurePose(new Vec3(5, 9, 1), new Quaterniond().rotationXYZ(.4, -.2, 1.1));
        var center = new Vec3(2, .5, -3);
        var destination = StructureTeleportMath.destination(source, center, new Vec3(20, 40, -10), null);
        var delta = destination.worldOrigin().subtract(source.worldOrigin());
        for (var local : java.util.List.of(Vec3.ZERO, center, new Vec3(1, 2, 9))) {
            assertVector(source.localToWorldPoint(local).add(delta), destination.localToWorldPoint(local));
        }
    }

    @Test void orientationChangeRotatesLinearAndAngularMomentumWithoutChangingSpeed() {
        var source = StructurePose.identity(Vec3.ZERO);
        var destination = new StructurePose(Vec3.ZERO, new Quaterniond().rotationY(Math.PI / 2));
        var motion = new Vec3(0, 2, 40);
        var rotated = StructureTeleportMath.rotateMotion(source, destination, motion);
        assertVector(new Vec3(40, 2, 0), rotated);
        assertEquals(motion.length(), rotated.length(), EPS);
        assertVector(motion, StructureTeleportMath.rotateMotion(destination, source, rotated));
        assertVector(new Vec3(3, 0, 0), StructureTeleportMath.rotateMotion(source, destination, new Vec3(0, 0, 3)));
    }

    @Test void structureForwardMatchesMinecraftViewDirectionIncludingVerticalPitch() {
        for (float yaw : new float[]{-180, -90, 0, 90, 145}) {
            for (float pitch : new float[]{-90, -37, 0, 50, 90}) {
                var forward = TeleportStructures.facing(yaw, pitch).localToWorldDirection(new Vec3(0, 0, 1));
                double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
                assertVector(new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)), forward);
                // Minecraft's float trigonometry uses a lookup table; its direction has that existing rounding.
                assertEquals(0, forward.distanceTo(Vec3.directionFromRotation(pitch, yaw)), 1E-4);
            }
        }
    }

    @Test void chunkCarriedContactOnlyPreservesTheSameCollisionImageAndRelativeGeometry() {
        var id = java.util.UUID.randomUUID();
        var source = StructurePose.identity(new Vec3(10, 20 - 1E-6, 30));
        var displacement = new Vec3(48, 0, -16);
        var floor = new net.minecraft.world.phys.AABB(10, 19, 30, 11, 20, 31).move(displacement);
        var observation = new LiveStructureTeleport.CarriedTerrain(id, source, displacement, java.util.Set.of(floor));
        var target = StructureTeleportMath.destination(source, new Vec3(.5, .5, .5),
                source.localToWorldPoint(new Vec3(.5, .5, .5)).add(displacement), null);
        assertTrue(observation.permits(id, source, target, floor), "unchanged carried floor contact is not a new overlap");
        assertFalse(observation.permits(java.util.UUID.randomUUID(), source, target, floor));
        assertFalse(observation.permits(id, source, target, floor.move(0, .001, 0)), "a different wall image has no contact allowance");
        assertFalse(observation.permits(id, source, new StructurePose(target.worldOrigin().add(0, -.001, 0), target.rotation()), floor),
                "the carried allowance cannot add any requested penetration");
        assertFalse(observation.permits(id, source, new StructurePose(target.worldOrigin(), new Quaterniond().rotationY(.01)), floor),
                "a rotation is not the same terrain-relative geometry");
        assertFalse(observation.permits(id, StructurePose.identity(source.worldOrigin().add(0, 1, 0)), target, floor),
                "a stale source pose cannot authorize inherited contacts");
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPS); assertEquals(expected.y, actual.y, EPS); assertEquals(expected.z, actual.z, EPS);
    }
}
