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
}
