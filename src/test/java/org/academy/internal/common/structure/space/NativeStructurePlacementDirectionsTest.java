package org.academy.internal.common.structure.space;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeStructurePlacementDirectionsTest {
    @Test
    void arbitraryStructureRotationRecoversTheSameLocalPlacementDirections() {
        var localLook = new Vec3(0.23, -0.91, -0.42).normalize();
        var localHorizontal = new Vec3(0.23, 0, -0.42).normalize();
        var identity = new NativeStructurePlacementDirections(StructurePose.identity(Vec3.ZERO), localLook, localHorizontal);
        var pose = new StructurePose(new Vec3(29_000_000.25, -400, -29_000_000.5),
                new Quaterniond().rotationXYZ(0.723, -1.436, 2.12));
        var transformed = new NativeStructurePlacementDirections(pose,
                pose.localToWorldDirection(localLook), pose.localToWorldDirection(localHorizontal));

        assertArrayEquals(identity.nearest(), transformed.nearest());
        assertEquals(identity.horizontal(), transformed.horizontal());
        assertEquals(identity.vertical(), transformed.vertical());
        assertEquals(identity.rotation(), transformed.rotation(), 1.0e-4);
    }

    @Test
    void supportFaceTakesPrecedenceWithoutLosingAnyFallbackDirection() {
        var directions = new NativeStructurePlacementDirections(StructurePose.identity(Vec3.ZERO),
                new Vec3(0.12, -0.75, 0.64).normalize(), new Vec3(0, 0, 1));
        for (var face : Direction.values()) {
            var ordered = directions.placementOrder(face, false);
            assertEquals(face.getOpposite(), ordered[0]);
            assertEquals(EnumSet.allOf(Direction.class), EnumSet.copyOf(Arrays.asList(ordered)));
            assertArrayEquals(directions.nearest(), directions.placementOrder(face, true));
        }
    }

    @Test
    void verticalViewUsesAStableHorizontalFallbackAndPreservesUpOrDown() {
        var pose = new StructurePose(Vec3.ZERO, new Quaterniond().rotationXYZ(1.3, 0.9, -0.7));
        var directions = new NativeStructurePlacementDirections(pose,
                pose.localToWorldDirection(new Vec3(0, 1, 0)),
                pose.localToWorldDirection(new Vec3(-1, 0, 0)));
        assertEquals(Direction.UP, directions.vertical());
        assertEquals(Direction.UP, directions.nearest()[0]);
        assertEquals(Direction.WEST, directions.horizontal());
        assertTrue(Float.isFinite(directions.rotation()));

        var degenerate = new NativeStructurePlacementDirections(StructurePose.identity(Vec3.ZERO),
                new Vec3(0, -1, 0), new Vec3(0, 1, 0));
        assertEquals(Direction.DOWN, degenerate.vertical());
        assertEquals(Direction.SOUTH, degenerate.horizontal());
    }
}
