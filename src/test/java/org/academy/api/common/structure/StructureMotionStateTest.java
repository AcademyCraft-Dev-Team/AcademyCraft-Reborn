package org.academy.api.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureMotionStateTest {
    private static final StructurePose POSE = StructurePose.identity(new Vec3(29_999_000.125, 80, -29_999_000.25));

    @Test
    void snapshotPreservesGridOriginSeparateFromCenterOfMassAndRuntimeVersions() {
        var state = new StructureMotionState(POSE, new Vec3(1, 0, 0), new Vec3(0, 0, 2),
                new Vec3(0.5, 0.25, 0), 3.5, false, 7, 2);
        assertEquals(POSE, state.pose());
        assertEquals(new Vec3(29_999_000.625, 80.25, -29_999_000.25),
                state.pose().localToWorldPoint(state.localCenterOfMass()));
        assertEquals(new Vec3(1, 2, 0), state.pose().surfaceVelocity(new Vec3(1.5, 0.25, 0),
                state.localCenterOfMass(), state.linearVelocity(), state.angularVelocity()));
        assertEquals(7, state.contentRevision());
        assertEquals(2, state.geometryEpoch());
        assertEquals(3.5, state.mass());
    }

    @Test
    void malformedNativeObservationsCannotBecomePublicMotionSnapshots() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> state(new Vec3(invalid, 0, 0), Vec3.ZERO, Vec3.ZERO, 1, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> state(Vec3.ZERO, new Vec3(0, invalid, 0), Vec3.ZERO, 1, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> state(Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, invalid), 1, 0, 0));
        }
        for (double mass : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> state(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, mass, 0, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> state(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> state(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1, 0, -1));
        assertThrows(NullPointerException.class, () -> state(null, Vec3.ZERO, Vec3.ZERO, 1, 0, 0));
    }

    @Test
    void regionBoundsRejectDegenerateUnboundedAndOversizedSimulationAreas() {
        assertDoesNotThrow(() -> StructurePhysics.requireBounds(new AABB(29_999_000, 64, -29_999_000,
                29_999_024, 88, -29_998_976)));
        for (var bounds : new AABB[]{new AABB(0, 0, 0, 0, 1, 1), new AABB(0, 0, 0, 1, 24.001, 1),
                new AABB(0, 0, 0, 1, 1, Double.POSITIVE_INFINITY), new AABB(Double.NaN, 0, 0, 1, 1, 1),
                new AABB(-Double.MAX_VALUE, 0, 0, Double.MAX_VALUE, 1, 1)}) {
            assertThrows(IllegalArgumentException.class, () -> StructurePhysics.requireBounds(bounds));
        }
    }

    private static StructureMotionState state(Vec3 linear, Vec3 angular, Vec3 center, double mass,
                                               long revision, long epoch) {
        return new StructureMotionState(POSE, linear, angular, center, mass, false, revision, epoch);
    }
}
