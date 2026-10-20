package org.academy.internal.common.sublevel;

import org.academy.internal.common.structure.physics.BodyType;
import org.academy.internal.common.structure.physics.NativePhysicsLibrary;
import org.academy.internal.common.structure.physics.PhysicsBox;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real native solver guard: unavailable terrain must not advance a body through the missing floor. */
@Timeout(30)
class SubLevelTerrainCoveragePhysicsTest {
    @Test
    void missingTerrainHoldsExactMotionThenResumesAgainstInstalledGround() throws IOException {
        try (var library = NativePhysicsLibrary.openConfigured();
             var world = library.createWorld(PhysicsVector.ZERO, new PhysicsVector(0, -9.81, 0))) {
            var universe = new SubLevelPhysicsUniverse(world);
            var id = SubLevelId.random();
            universe.add(id, new PhysicsPose(new PhysicsVector(0, 3, 0), PhysicsQuaternion.IDENTITY),
                    List.of(new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY,
                            new PhysicsVector(0.5, 0.5, 0.5))));
            universe.setVelocity(id, new PhysicsVector(0, -3, 0), PhysicsVector.ZERO);
            var held = universe.motion(id).orElseThrow();
            long revision = universe.revision();
            for (int tick = 0; tick < 100; tick++) assertFalse(universe.stepCovered(0.05, 2, true, false));
            assertEquals(held, universe.motion(id).orElseThrow());
            assertEquals(revision, universe.revision());
            try (var floor = world.createBody(BodyType.FIXED,
                    new PhysicsPose(new PhysicsVector(0, -0.5, 0), PhysicsQuaternion.IDENTITY),
                    List.of(new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY,
                            new PhysicsVector(8, 0.5, 8))), 0, 0.6, 0, false)) {
                for (int tick = 0; tick < 100; tick++) assertTrue(universe.stepCovered(0.05, 2, tick == 0, true));
                var supported = universe.motion(id).orElseThrow();
                assertTrue(supported.pose().position().y() >= 0.45, "Body crossed installed floor: " + supported);
                assertTrue(supported.pose().position().y() < 0.6, "Body did not resume falling onto floor: " + supported);
                assertTrue(Math.abs(supported.linearVelocity().y()) < 0.1);
            }
        }
    }
}
