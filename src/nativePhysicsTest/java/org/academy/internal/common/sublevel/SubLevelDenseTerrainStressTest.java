package org.academy.internal.common.sublevel;

import org.academy.internal.common.structure.physics.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real ABI regression for ground contact, mining rebuilds and terrain handle churn. */
@Timeout(60)
final class SubLevelDenseTerrainStressTest {
    @Test
    void fiftyFiveAndEightyOneCellBodiesSurviveDenseGroundAndRepeatedRebuilds() throws IOException {
        try (var library = NativePhysicsLibrary.openConfigured();
             var world = library.createWorld(PhysicsVector.ZERO, new PhysicsVector(0, -9.81, 0))) {
            var universe = new SubLevelPhysicsUniverse(world);
            var first = SubLevelId.random();
            var second = SubLevelId.random();
            var firstPose = pose(2, 0.02, 2);
            var secondPose = pose(15, 0.02, 2);
            universe.add(first, firstPose, cells(55));
            universe.add(second, secondPose, cells(81));
            var terrain = terrain(world, 0);
            long started = System.nanoTime();
            try {
                for (int step = 0; step < 20; step++) universe.step(0.05, 2);
                assertTrue(universe.motion(first).orElseThrow().pose().position().y() > -0.1,
                        "55-cell body must actually contact and remain above the floor");
                assertTrue(universe.motion(second).orElseThrow().pose().position().y() > -0.1,
                        "81-cell body must actually contact and remain above the floor");

                for (int cycle = 0; cycle < 100; cycle++) {
                    // The live trace had 81 -> 80 -> 79 cells (82 -> 81 -> 80 boxes).
                    // Match runtime.refreshGeometry: remove the old body, rebuild at its pose,
                    // then restore the observed linear and angular velocity.
                    int remaining = 81 - cycle % 3;
                    var previous = universe.motion(second).orElseThrow();
                    assertTrue(universe.remove(second));
                    universe.add(second, previous.pose(), cells(remaining));
                    assertTrue(universe.setVelocity(second, previous.linearVelocity(), previous.angularVelocity()));
                    assertEquals(remaining, universe.motion(second).orElseThrow().mass(), 1.0E-7);

                    // Closing contacted fixed bodies must invalidate Rapier contact pairs before
                    // newly allocated handles can occupy those same terrain cells.
                    for (var body : terrain) body.close();
                    terrain = terrain(world, cycle + 1);
                    assertTrue(universe.setPose(first, firstPose));
                    assertTrue(universe.setPose(second, secondPose));
                    assertTrue(universe.setVelocity(first, new PhysicsVector(50, 0, 0), new PhysicsVector(0, 0.2, 0)));
                    assertTrue(universe.setVelocity(second, new PhysicsVector(-50, 0, 0), new PhysicsVector(0, -0.2, 0)));
                    for (int step = 0; step < 8; step++) {
                        universe.step(0.05, 2);
                        assertFinite(universe.motion(first).orElseThrow());
                        assertFinite(universe.motion(second).orElseThrow());
                    }
                    assertEquals(2, universe.activeCount());
                    assertEquals(2, universe.bodyCount());
                }
                assertTrue(universe.remove(first));
                assertTrue(universe.remove(second));
                universe.step(0.05, 2);
                assertEquals(0, universe.bodyCount());
                System.out.println("SUBLEVEL_DENSE_TERRAIN_STRESS_PASSED cycles=100 ticks=820"
                        + " initial_cells=55,81 dynamic_rebuilds=100 terrain_rebuilds=100"
                        + " launch_speed=50 elapsed_ms=" + (System.nanoTime() - started) / 1_000_000.0);
            } finally {
                for (var body : terrain) body.close();
            }
        }
    }

    private static List<PhysicsBox> cells(int count) {
        var result = new ArrayList<PhysicsBox>();
        for (int index = 0; index < count; index++) {
            int x = index % 9, z = index / 9;
            // One two-part cell retains unit mass while reproducing cells+1 collision boxes.
            if (index == 0 && count >= 79) {
                result.add(box(x + 0.5, 0.25, z + 0.5, 0.5, 0.25, 0.5));
                result.add(box(x + 0.5, 0.75, z + 0.5, 0.5, 0.25, 0.5));
            } else result.add(box(x + 0.5, 0.5, z + 0.5, 0.5, 0.5, 0.5));
        }
        return result;
    }

    private static List<NativePhysicsBody> terrain(NativePhysicsWorld world, int revision) {
        var result = new ArrayList<NativePhysicsBody>();
        // Four independently rebuilt sections, each with 256 ground cuboids. A changing corner
        // hole exercises actual geometry changes while keeping the initial support intact.
        for (int sectionX = 0; sectionX < 2; sectionX++) for (int sectionZ = 0; sectionZ < 2; sectionZ++) {
            var boxes = new ArrayList<PhysicsBox>();
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                if (x == 15 && z == 15 && revision % 2 == 1) continue;
                boxes.add(box(x + 0.5, -0.5, z + 0.5, 0.5, 0.5, 0.5));
            }
            result.add(world.createBody(BodyType.FIXED, pose(sectionX * 16, 0, sectionZ * 16),
                    boxes, 0, 0.6, 0, false));
        }
        return result;
    }

    private static PhysicsPose pose(double x, double y, double z) {
        return new PhysicsPose(new PhysicsVector(x, y, z), PhysicsQuaternion.IDENTITY);
    }

    private static PhysicsBox box(double x, double y, double z, double hx, double hy, double hz) {
        return new PhysicsBox(new PhysicsVector(x, y, z), PhysicsQuaternion.IDENTITY, new PhysicsVector(hx, hy, hz));
    }

    private static void assertFinite(SubLevelPhysicsUniverse.Motion motion) {
        for (var vector : List.of(motion.pose().position(), motion.linearVelocity(), motion.angularVelocity(),
                motion.localCenterOfMass())) {
            assertTrue(Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z()));
        }
        assertTrue(motion.mass() > 0 && Double.isFinite(motion.mass()));
        assertTrue(Math.abs(motion.pose().position().y()) < 100,
                "Dense contacts/rebuilds must not eject the body outside the bounded fixture");
    }
}
