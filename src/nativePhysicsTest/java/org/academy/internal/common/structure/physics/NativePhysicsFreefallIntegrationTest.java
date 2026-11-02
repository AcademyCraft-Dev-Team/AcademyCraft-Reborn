package org.academy.internal.common.structure.physics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises released blocks through the real Java/native boundary at gameplay cadence. */
@Timeout(20)
final class NativePhysicsFreefallIntegrationTest {
    @Test
    void releasedBlocksLandWithoutReboundOrLosingPitSupport() throws IOException {
        try (var library = NativePhysicsLibrary.openConfigured()) {
            for (boolean pit : new boolean[]{false, true}) {
                try (var world = library.createWorld(PhysicsVector.ZERO, new PhysicsVector(0, -9.81, 0))) {
                    box(world, BodyType.FIXED, 0, -0.5, 0, 8, 0.5, 8);
                    if (pit) {
                        box(world, BodyType.FIXED, -1, 0.5, 0, 0.5, 0.5, 8);
                        box(world, BodyType.FIXED, 1, 0.5, 0, 0.5, 0.5, 8);
                        box(world, BodyType.FIXED, 0, 0.5, -1, 0.5, 0.5, 0.5);
                        box(world, BodyType.FIXED, 0, 0.5, 1, 0.5, 0.5, 0.5);
                    }
                    var falling = box(world, BodyType.DYNAMIC, 0, 5.5, 0, 0.5, 0.5, 0.5);
                    for (int tick = 0; tick < 240; tick++) {
                        world.step(0.05, 2);
                        var state = falling.state();
                        assertTrue(state.pose().position().y() >= 0.49,
                                "Lost floor support: pit=" + pit + " tick=" + tick + " state=" + state);
                        assertTrue(state.linearVelocity().y() <= 0.02,
                                "Inelastic touchdown rebounded: pit=" + pit + " tick=" + tick + " state=" + state);
                    }
                    assertEquals(0.5, falling.pose().position().y(), 0.01);
                    assertTrue(falling.state().sleeping(), "Released block should naturally sleep");
                }
            }
        }
    }

    private static NativePhysicsBody box(NativePhysicsWorld world, BodyType type,
                                         double x, double y, double z, double hx, double hy, double hz) {
        var shape = new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY, new PhysicsVector(hx, hy, hz));
        return world.createBody(type, new PhysicsPose(new PhysicsVector(x, y, z), PhysicsQuaternion.IDENTITY),
                List.of(shape), type == BodyType.DYNAMIC ? 1 : 0, 0.6, 0, type == BodyType.DYNAMIC);
    }
}
