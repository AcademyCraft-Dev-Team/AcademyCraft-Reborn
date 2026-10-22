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
import java.util.ArrayList;
import java.util.Set;

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

    @Test
    void unavailableDistantTerrainOnlyPausesItsOwnBodyWhileLocalBodyFallsAndRotates() throws IOException {
        try (var library = NativePhysicsLibrary.openConfigured();
             var world = library.createWorld(PhysicsVector.ZERO, new PhysicsVector(0, -9.81, 0))) {
            var universe = new SubLevelPhysicsUniverse(world);
            var box = new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5));
            var distant = SubLevelId.random();
            var local = SubLevelId.random();
            universe.add(distant, new PhysicsPose(new PhysicsVector(1000, 3, 0), PhysicsQuaternion.IDENTITY), List.of(box));
            universe.add(local, new PhysicsPose(new PhysicsVector(0, 6, 0), PhysicsQuaternion.IDENTITY), List.of(box));
            universe.setVelocity(distant, new PhysicsVector(4, -3, 0), new PhysicsVector(0, 1, 0));
            universe.setVelocity(local, PhysicsVector.ZERO, new PhysicsVector(0, 2, 0));
            var held = universe.motion(distant).orElseThrow();
            try (var floor = world.createBody(BodyType.FIXED,
                    new PhysicsPose(new PhysicsVector(0, -0.5, 0), PhysicsQuaternion.IDENTITY),
                    List.of(new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY, new PhysicsVector(8, 0.5, 8))),
                    0, 0.6, 0, false)) {
                for (int tick = 0; tick < 60; tick++) {
                    assertTrue(universe.stepCovered(0.05, 2, tick == 0, Set.of(distant)));
                    assertEquals(held, universe.motion(distant).orElseThrow());
                }
                assertTrue(universe.isSimulationPaused(distant));
                assertFalse(universe.isSimulationPaused(local));
                var fallen = universe.motion(local).orElseThrow();
                assertTrue(fallen.pose().position().y() < 0.8 && fallen.pose().position().y() > 0.4, fallen.toString());
                assertNotEquals(PhysicsQuaternion.IDENTITY, fallen.pose().rotation());
                assertFalse(universe.impulse(distant, new PhysicsVector(1000, 0, 0), held.pose().position()));
                assertFalse(universe.setVelocity(distant, new PhysicsVector(20, 0, 0), PhysicsVector.ZERO));
                assertFalse(universe.setPose(distant, PhysicsPose.IDENTITY));
                assertEquals(held, universe.motion(distant).orElseThrow(), "Rejected commands must not accumulate motion");
                universe.stepCovered(0.05, 2, true, Set.of());
                assertFalse(universe.isSimulationPaused(distant));
                var resumed = universe.motion(distant).orElseThrow();
                assertTrue(resumed.pose().position().x() > 1000.15 && resumed.pose().position().x() < 1000.25);
                assertTrue(resumed.pose().position().y() < 2.9);
            }
        }
    }

    @Test
    void pausedStructureRemainsCollidableAndCannotBeAwakenedByAnotherBody() throws IOException {
        try (var library = NativePhysicsLibrary.openConfigured();
             var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var universe = new SubLevelPhysicsUniverse(world);
            var box = new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5));
            var paused = SubLevelId.random();
            var projectile = SubLevelId.random();
            universe.add(paused, PhysicsPose.IDENTITY, List.of(box));
            universe.add(projectile, new PhysicsPose(new PhysicsVector(-4, 0, 0), PhysicsQuaternion.IDENTITY), List.of(box));
            universe.setVelocity(projectile, new PhysicsVector(12, 0, 0), PhysicsVector.ZERO);
            var held = universe.motion(paused).orElseThrow();
            for (int tick = 0; tick < 40; tick++) {
                universe.stepCovered(0.05, 2, tick == 0, Set.of(paused));
                assertEquals(held, universe.motion(paused).orElseThrow());
                assertTrue(universe.motion(projectile).orElseThrow().pose().position().x() < -0.95,
                        "Projectile crossed the paused body's collision");
            }
            assertTrue(universe.motion(projectile).orElseThrow().pose().position().x() > -1.2,
                    "The unaffected projectile must move before contact");
        }
    }

    @Test
    void terrainPauseAndResumeApplyToEveryWeldedBody() throws IOException {
        try (var library = NativePhysicsLibrary.openConfigured();
             var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var universe = new SubLevelPhysicsUniverse(world);
            var boxes = new ArrayList<PhysicsBox>();
            for (int index = 0; index < 257; index++) {
                boxes.add(new PhysicsBox(new PhysicsVector(index % 8 + 0.5, index / 64 + 0.5,
                                index / 8 % 8 + 0.5), PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5)));
            }
            var assembly = SubLevelId.random();
            var independent = SubLevelId.random();
            universe.add(assembly, PhysicsPose.IDENTITY, boxes);
            universe.add(independent, new PhysicsPose(new PhysicsVector(30, 0, 0), PhysicsQuaternion.IDENTITY),
                    List.of(boxes.getFirst()));
            assertEquals(3, universe.bodyCount(), "The assembly must exercise multiple welded bodies");
            universe.setVelocity(assembly, new PhysicsVector(2, 0, 0), PhysicsVector.ZERO);
            universe.setVelocity(independent, new PhysicsVector(1, 0, 0), PhysicsVector.ZERO);
            var held = universe.motion(assembly).orElseThrow();
            for (int tick = 0; tick < 20; tick++) {
                universe.stepCovered(0.05, 2, true, Set.of(assembly));
                assertEquals(held, universe.motion(assembly).orElseThrow());
            }
            assertTrue(universe.motion(independent).orElseThrow().pose().position().x() > 30.8);
            universe.stepCovered(0.05, 2, true, Set.of());
            var resumed = universe.motion(assembly).orElseThrow();
            assertEquals(held.mass(), resumed.mass(), 1.0E-8);
            assertTrue(resumed.pose().position().x() > 0.08 && resumed.pose().position().x() < 0.12);
            assertEquals(2, resumed.linearVelocity().x(), 0.05);
        }
    }
}
