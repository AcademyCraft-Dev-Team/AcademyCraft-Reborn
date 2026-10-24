package org.academy.internal.common.sublevel;

import org.academy.internal.common.structure.physics.NativePhysicsLibrary;
import org.academy.internal.common.structure.physics.NativePhysicsWorld;
import org.academy.internal.common.structure.physics.PhysicsBox;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 0 capacity and lifecycle probe for a single shared world with many sub-level bodies. */
@Timeout(30)
final class SubLevelPhysicsUniverseTest {
    private NativePhysicsLibrary library;

    @BeforeEach
    void loadRealEngine() throws IOException {
        library = NativePhysicsLibrary.openConfigured();
    }

    @AfterEach
    void releaseEngine() {
        if (library != null) library.close();
    }

    @Test
    void sixtyFourBodiesStepInOneSharedWorld() {
        try (var world = library.createWorld(PhysicsVector.ZERO, new PhysicsVector(0, -9.81, 0))) {
            var universe = new SubLevelPhysicsUniverse(world);
            var box = new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY,
                    new PhysicsVector(0.5, 0.5, 0.5));
            var ids = new ArrayList<SubLevelId>();
            for (int index = 0; index < SubLevelPhysicsUniverse.MAX_ACTIVE; index++) {
                var id = SubLevelId.random();
                ids.add(id);
                universe.add(id, new PhysicsPose(
                                new PhysicsVector((index % 8) * 3.0, 20 + (index / 8) * 3.0, 0),
                                PhysicsQuaternion.IDENTITY), List.of(box));
            }
            assertEquals(64, universe.activeCount());
            assertEquals(64, universe.bodyCount());
            long started = System.nanoTime();
            for (int tick = 0; tick < 120; tick++) universe.step(1.0 / 120.0, 1);
            long elapsedNanos = System.nanoTime() - started;
            for (var id : ids) {
                var motion = universe.motion(id).orElseThrow();
                assertTrue(motion.pose().position().y() < 20 + (ids.indexOf(id) / 8) * 3.0 + 1.0);
                assertTrue(Double.isFinite(motion.linearVelocity().x()));
                assertTrue(motion.mass() > 0);
            }
            for (int index = 0; index < 32; index++) assertTrue(universe.remove(ids.get(index)));
            universe.step(1.0 / 120.0, 1);
            assertEquals(32, universe.activeCount());
            System.out.println("SUB_LEVEL_PHYSICS_BENCHMARK bodies=64 ticks=120 elapsed_ms="
                    + (elapsedNanos / 1_000_000.0));
        }
    }

    @Test
    void sixtyFourActiveBodiesWithTerrainSectionsStayWithinBudget() {
        try (var world = library.createWorld(PhysicsVector.ZERO, new PhysicsVector(0, -9.81, 0))) {
            var terrainBoxes = new ArrayList<PhysicsBox>(256);
            for (int index = 0; index < 256; index++) {
                terrainBoxes.add(new PhysicsBox(new PhysicsVector((index % 16) + 0.5, 40.5, (index / 16) + 0.5),
                        PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5)));
            }
            for (int section = 0; section < 16; section++) {
                world.createBody(org.academy.internal.common.structure.physics.BodyType.FIXED,
                        new PhysicsPose(new PhysicsVector(section * 32.0, 0, 0), PhysicsQuaternion.IDENTITY),
                        terrainBoxes, 0.0, 0.6, 0.0, false);
            }
            var universe = new SubLevelPhysicsUniverse(world);
            var box = new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5));
            var ids = new ArrayList<SubLevelId>(SubLevelPhysicsUniverse.MAX_ACTIVE);
            for (int index = 0; index < SubLevelPhysicsUniverse.MAX_ACTIVE; index++) {
                var id = SubLevelId.random();
                ids.add(id);
                universe.add(id, new PhysicsPose(
                        new PhysicsVector((index % 8) * 3.0, 20 + (index / 8) * 3.0, 0), PhysicsQuaternion.IDENTITY),
                        List.of(box));
            }
            long started = System.nanoTime();
            for (int tick = 0; tick < 120; tick++) universe.step(1.0 / 120.0, 1);
            var elapsed = (System.nanoTime() - started) / 1_000_000.0;
            assertEquals(64, universe.activeCount());
            assertEquals(80, universe.bodyCount() + 16);
            assertTrue(universe.motion(ids.getFirst()).orElseThrow().mass() > 0);
            System.out.println("SUB_LEVEL_ACTIVE_BENCHMARK bodies=64 terrain=16 ticks=120 elapsed_ms=" + elapsed);
        }
    }

    @Test
    void largeStructureIsSplitIntoFixedJointedBodies() {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var universe = new SubLevelPhysicsUniverse(world);
            var boxes = new ArrayList<PhysicsBox>(300);
            for (int index = 0; index < 300; index++) {
                boxes.add(new PhysicsBox(new PhysicsVector((index % 10) + 0.5, (index / 10) + 0.5, 0.5),
                        PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5)));
            }
            var id = SubLevelId.random();
            universe.add(id, new PhysicsPose(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY), boxes);
            assertEquals(1, universe.activeCount());
            assertEquals(2, universe.bodyCount());
            universe.step(1.0 / 120.0, 1);
            assertTrue(universe.motion(id).orElseThrow().mass() > 0);
        }
    }

    @Test
    void velocityAndPoseCommandsMoveEveryWeldedBodyWithoutLosingSpeed() {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var universe = new SubLevelPhysicsUniverse(world);
            var boxes = new ArrayList<PhysicsBox>(300);
            for (int index = 0; index < 300; index++) {
                boxes.add(new PhysicsBox(new PhysicsVector((index % 10) + 0.5, (index / 10) + 0.5, 0.5),
                        PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5)));
            }
            var id = SubLevelId.random();
            universe.add(id, new PhysicsPose(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY), boxes);
            assertTrue(universe.setVelocity(id, new PhysicsVector(6, 0, 0), PhysicsVector.ZERO));
            assertEquals(6, universe.motion(id).orElseThrow().linearVelocity().x(), 1.0E-5);
            for (int step = 0; step < 20; step++) universe.step(0.05, 4);
            assertEquals(6, universe.motion(id).orElseThrow().pose().position().x(), 0.05);
            var rotated = new PhysicsPose(new PhysicsVector(10, 20, 30),
                    new PhysicsQuaternion(0, Math.sin(Math.PI / 4), 0, Math.cos(Math.PI / 4)));
            assertTrue(universe.setPose(id, rotated));
            assertEquals(2, universe.bodyCount());
            assertEquals(6, universe.motion(id).orElseThrow().linearVelocity().x(), 1.0E-5);
            universe.step(0.05, 4);
            var motion = universe.motion(id).orElseThrow();
            assertEquals(10.3, motion.pose().position().x(), 0.02);
            assertEquals(20, motion.pose().position().y(), 0.02);
            assertEquals(30, motion.pose().position().z(), 0.02);
        }
    }

    @Test
    void angularCorrectionPreservesWeldedComTranslationAndDoesNotFightItsJoints() {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var universe = new SubLevelPhysicsUniverse(world);
            var boxes = new ArrayList<PhysicsBox>(300);
            for (int index = 0; index < 300; index++) {
                boxes.add(new PhysicsBox(new PhysicsVector((index % 10) + 0.5, (index / 10) + 0.5, 0.5),
                        PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5)));
            }
            var id = SubLevelId.random();
            universe.add(id, new PhysicsPose(new PhysicsVector(4, 20, 8), PhysicsQuaternion.IDENTITY), boxes);
            assertEquals(2, universe.bodyCount());
            var translation = new PhysicsVector(2, 3, 4);
            assertTrue(universe.setVelocity(id, translation, new PhysicsVector(0, 0, 0.2)));
            var before = universe.angularMotion(id).orElseThrow();
            // A 10 x 30 x 1 solid has Izz = m (10² + 30²) / 12. Using only the
            // primary batch's inertia omits the second part and the parallel-axis terms.
            double exactLargestInertia = 300.0 * (100 + 900) / 12;
            assertTrue(before.maximumInertia() >= exactLargestInertia * (1 - 1.0E-5));
            assertTrue(before.maximumInertia() < exactLargestInertia * 1.1);
            assertTrue(universe.setAngularVelocity(id, new PhysicsVector(0, 0, 1)));
            var corrected = universe.motion(id).orElseThrow();
            assertVector(translation, corrected.linearVelocity(), 1.0E-5);
            assertEquals(before.motion().pose(), corrected.pose(), "Angular control never teleports the grid origin");
            universe.step(0.005, 1);
            var stepped = universe.motion(id).orElseThrow();
            assertVector(translation, stepped.linearVelocity(), 0.003);
            assertEquals(1, stepped.angularVelocity().z(), 0.02,
                    "The fixed joint must not cancel a spin whose member COM velocities were left stale");
            assertTrue(universe.setAngularVelocity(id, PhysicsVector.ZERO));
            universe.step(0.005, 1);
            assertVector(translation, universe.motion(id).orElseThrow().linearVelocity(), 0.003);
            assertVector(PhysicsVector.ZERO, universe.motion(id).orElseThrow().angularVelocity(), 0.02);
        }
    }

    @Test
    void substepHookSeesFreshSolverMotionAndCannotWriteTerrainPausedAssemblies() {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var universe = new SubLevelPhysicsUniverse(world);
            var box = new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5));
            var moving = SubLevelId.random();
            var paused = SubLevelId.random();
            universe.add(moving, new PhysicsPose(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY), List.of(box));
            universe.add(paused, new PhysicsPose(new PhysicsVector(20, 0, 0), PhysicsQuaternion.IDENTITY), List.of(box));
            universe.setVelocity(moving, new PhysicsVector(1, 0, 0), PhysicsVector.ZERO);
            universe.setVelocity(paused, new PhysicsVector(2, 0, 0), PhysicsVector.ZERO);
            var observations = new ArrayList<Double>();
            var revisions = new ArrayList<Long>();
            assertTrue(universe.stepCovered(0.05, 2, false, Set.of(paused), seconds -> {
                assertEquals(0.025, seconds, 1.0E-12);
                var observed = universe.angularMotion(moving).orElseThrow();
                observations.add(observed.motion().pose().position().x());
                revisions.add(observed.revision());
                assertTrue(universe.isSimulationPaused(paused));
                assertFalse(universe.setAngularVelocity(paused, new PhysicsVector(0, 1, 0)));
                assertTrue(universe.setAngularVelocity(moving, new PhysicsVector(0, 0, 1)));
            }));
            assertEquals(2, observations.size());
            assertEquals(0, observations.getFirst(), 1.0E-6);
            assertEquals(0.025, observations.getLast(), 1.0E-5);
            assertTrue(revisions.getLast() > revisions.getFirst());
            assertEquals(0.05, universe.motion(moving).orElseThrow().pose().position().x(), 1.0E-5);
            assertEquals(20, universe.motion(paused).orElseThrow().pose().position().x(), 1.0E-6);
            assertVector(PhysicsVector.ZERO, universe.motion(paused).orElseThrow().angularVelocity(), 1.0E-6);
        }
    }

    @Test
    void sleepingAssemblyCanBeWokenByItsFirstAngularSubstep() {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var universe = new SubLevelPhysicsUniverse(world);
            var id = SubLevelId.random();
            universe.add(id, new PhysicsPose(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY), List.of(
                    new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5))));
            for (int tick = 0; tick < 100; tick++) universe.step(0.05, 1, true);
            assertTrue(universe.motion(id).orElseThrow().sleeping());
            assertTrue(universe.stepCovered(0.05, 2, false, Set.of(), seconds ->
                    assertTrue(universe.setAngularVelocity(id, new PhysicsVector(0, 1, 0)))));
            var motion = universe.motion(id).orElseThrow();
            assertFalse(motion.sleeping());
            assertTrue(Math.abs(motion.pose().rotation().y()) > 0.01);
        }
    }

    private static void assertVector(PhysicsVector expected, PhysicsVector actual, double tolerance) {
        assertEquals(expected.x(), actual.x(), tolerance);
        assertEquals(expected.y(), actual.y(), tolerance);
        assertEquals(expected.z(), actual.z(), tolerance);
    }
}
