package org.academy.internal.common.structure.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativePhysicsValuesTest {
    @Test
    void normalizesWithoutOverflowAndRejectsUndefinedRotations() {
        var rotation = new PhysicsQuaternion(Double.MAX_VALUE, 0, 0, Double.MAX_VALUE);
        assertEquals(Math.sqrt(0.5), rotation.x(), 1.0E-15);
        assertEquals(Math.sqrt(0.5), rotation.w(), 1.0E-15);
        assertThrows(IllegalArgumentException.class, () -> new PhysicsQuaternion(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsQuaternion(0, Double.NaN, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsVector(Double.POSITIVE_INFINITY, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsVector(Double.MAX_VALUE, 0, 0)
                .add(new PhysicsVector(Double.MAX_VALUE, 0, 0)));
    }

    @Test
    void boxLimitsRejectDegenerateAndUnboundedGeometry() {
        assertThrows(IllegalArgumentException.class, () -> new PhysicsBox(PhysicsVector.ZERO,
                PhysicsQuaternion.IDENTITY, new PhysicsVector(0, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsBox(PhysicsVector.ZERO,
                PhysicsQuaternion.IDENTITY, new PhysicsVector(1025, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsBox(new PhysicsVector(1_000_001, 0, 0),
                PhysicsQuaternion.IDENTITY, new PhysicsVector(1, 1, 1)));
        assertDoesNotThrow(() -> new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY,
                new PhysicsVector(1.0E-6, 1, 1024)));
    }
}
