package org.academy.internal.common.structure.physics;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructurePhysicsGeometryDigestTest {
    private static final PhysicsBox STONE = new PhysicsBox(new PhysicsVector(0.5, 80.5, 0.5),
            PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5));
    private static final PhysicsBox SLAB = new PhysicsBox(new PhysicsVector(1.5, 80.25, 0.5),
            PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.25, 0.5));

    @Test
    void decompositionOrderIsNotPersistentIdentityButMultiplicityIs() {
        var forward = NativeStructurePhysicsGeometryDigest.of(List.of(STONE, SLAB));
        assertEquals(forward, NativeStructurePhysicsGeometryDigest.of(List.of(SLAB, STONE)));
        assertTrue(forward.matches("[0-9a-f]{64}"));
        assertNotEquals(forward, NativeStructurePhysicsGeometryDigest.of(List.of(STONE, SLAB, STONE)));
        assertNotEquals(forward, NativeStructurePhysicsGeometryDigest.of(List.of(STONE)));
        assertNotEquals(NativeStructurePhysicsGeometryDigest.of(List.of()),
                NativeStructurePhysicsGeometryDigest.of(List.of(STONE)));
    }

    @Test
    void changedLocationExtentsOrRotationCannotReuseSavedMomentum() {
        var digest = NativeStructurePhysicsGeometryDigest.of(List.of(SLAB));
        var moved = new PhysicsBox(SLAB.center().add(new PhysicsVector(0, 1, 0)), SLAB.rotation(), SLAB.halfExtents());
        var full = new PhysicsBox(SLAB.center(), SLAB.rotation(), new PhysicsVector(0.5, 0.5, 0.5));
        var rotated = new PhysicsBox(SLAB.center(), new PhysicsQuaternion(0, 0, 1, 1), SLAB.halfExtents());
        for (var changed : List.of(moved, full, rotated)) {
            assertNotEquals(digest, NativeStructurePhysicsGeometryDigest.of(List.of(changed)));
        }
    }

    @Test
    void equivalentQuaternionSignsAndSignedZeroHaveOneDigest() {
        var first = new PhysicsBox(new PhysicsVector(0, 80, -0.0),
                new PhysicsQuaternion(0, 0, 1, 0), SLAB.halfExtents());
        var second = new PhysicsBox(new PhysicsVector(-0.0, 80, 0),
                new PhysicsQuaternion(-0.0, -0.0, -1, -0.0), SLAB.halfExtents());
        assertEquals(NativeStructurePhysicsGeometryDigest.of(List.of(first)),
                NativeStructurePhysicsGeometryDigest.of(List.of(second)));
    }

    @Test
    void refusesGeometryBeyondTheNativePerBodyLimit() {
        assertDoesNotThrow(() -> NativeStructurePhysicsGeometryDigest.of(Collections.nCopies(256, STONE)));
        assertThrows(IllegalArgumentException.class,
                () -> NativeStructurePhysicsGeometryDigest.of(Collections.nCopies(257, STONE)));
    }
}
