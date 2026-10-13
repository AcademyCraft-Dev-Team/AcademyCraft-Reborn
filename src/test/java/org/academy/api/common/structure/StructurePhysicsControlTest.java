package org.academy.api.common.structure;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructurePhysicsControlTest {
    @Test
    void forceAcceptsZeroAndExactNormBoundaryButNotPerAxisOnlyBounds() {
        assertEquals(Vec3.ZERO, new StructurePhysicsControl.Force(Vec3.ZERO, Vec3.ZERO).force());
        var boundary = new Vec3(6_000, 8_000, 0);
        assertEquals(boundary, new StructurePhysicsControl.Force(Vec3.ZERO, boundary).force());
        assertThrows(IllegalArgumentException.class,
                () -> new StructurePhysicsControl.Force(Vec3.ZERO, new Vec3(8_000, 8_000, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new StructurePhysicsControl.Force(Vec3.ZERO,
                        new Vec3(Double.MAX_VALUE, Double.MAX_VALUE, 0)));
    }

    @Test
    void localPointAllowsEachSignedBoundaryAndRejectsBeyondIt() {
        var boundary = new Vec3(1_000_000, -1_000_000, 1_000_000);
        assertEquals(boundary, new StructurePhysicsControl.Force(boundary, Vec3.ZERO).localPoint());
        assertEquals(boundary, anchor(boundary, Vec3.ZERO, Vec3.ZERO, 1, 0, 1).localPoint());
        for (double value : new double[]{Math.nextUp(1_000_000.0), -Math.nextUp(1_000_000.0)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new StructurePhysicsControl.Force(new Vec3(value, 0, 0), Vec3.ZERO));
            assertThrows(IllegalArgumentException.class,
                    () -> anchor(new Vec3(0, value, 0), Vec3.ZERO, Vec3.ZERO, 1, 0, 1));
        }
    }

    @Test
    void targetBoundsAreWorldComponentsAndVelocityNorm() {
        var position = new Vec3(30_000_000, -30_000_000, 30_000_000);
        var velocity = new Vec3(60, 80, 0);
        var control = anchor(Vec3.ZERO, position, velocity, 1, 0, 1);
        assertEquals(position, control.targetPosition());
        assertEquals(velocity, control.targetVelocity());
        assertThrows(IllegalArgumentException.class,
                () -> anchor(Vec3.ZERO, new Vec3(0, 0, Math.nextUp(30_000_000.0)), Vec3.ZERO, 1, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> anchor(Vec3.ZERO, Vec3.ZERO, new Vec3(100, 1, 0), 1, 0, 1));
    }

    @Test
    void gainLimitsAllowZeroDampingButRequirePositiveStiffnessAndForceLimit() {
        assertEquals(0, anchor(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 10_000, 0, 10_000).damping());
        assertEquals(10_000, anchor(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1, 10_000, 1).damping());
        assertDoesNotThrow(() -> anchor(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                Double.MIN_VALUE, 0, Double.MIN_VALUE));
        for (double invalid : new double[]{0, -1, Math.nextUp(10_000.0), Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> anchor(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, invalid, 1, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> anchor(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1, 1, invalid));
        }
        for (double invalid : new double[]{-1, Math.nextUp(10_000.0), Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> anchor(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1, invalid, 1));
        }
    }

    @Test
    void nonFiniteVectorsAreRejectedAtEveryInput() {
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            var bad = new Vec3(0, invalid, 0);
            assertThrows(IllegalArgumentException.class,
                    () -> new StructurePhysicsControl.Force(bad, Vec3.ZERO));
            assertThrows(IllegalArgumentException.class,
                    () -> new StructurePhysicsControl.Force(Vec3.ZERO, bad));
            assertThrows(IllegalArgumentException.class,
                    () -> anchor(bad, Vec3.ZERO, Vec3.ZERO, 1, 0, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> anchor(Vec3.ZERO, bad, Vec3.ZERO, 1, 0, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> anchor(Vec3.ZERO, Vec3.ZERO, bad, 1, 0, 1));
        }
    }

    @Test
    void nullVectorsAreNotImplicitlyTreatedAsZero() {
        assertThrows(NullPointerException.class, () -> new StructurePhysicsControl.Force(null, Vec3.ZERO));
        assertThrows(NullPointerException.class, () -> new StructurePhysicsControl.Force(Vec3.ZERO, null));
        assertThrows(NullPointerException.class, () -> anchor(null, Vec3.ZERO, Vec3.ZERO, 1, 0, 1));
        assertThrows(NullPointerException.class, () -> anchor(Vec3.ZERO, null, Vec3.ZERO, 1, 0, 1));
        assertThrows(NullPointerException.class, () -> anchor(Vec3.ZERO, Vec3.ZERO, null, 1, 0, 1));
    }

    private static StructurePhysicsControl.Anchor anchor(Vec3 point, Vec3 target, Vec3 velocity,
                                                          double stiffness, double damping, double maximum) {
        return new StructurePhysicsControl.Anchor(point, target, velocity, stiffness, damping, maximum);
    }
}
