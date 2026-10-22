package org.academy.internal.common.structure.physics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Requires the explicitly configured real f64 library; never substitutes a mock or skips loading. */
@Timeout(20)
final class NativePhysicsEngineIntegrationTest {
    private static final double DT = 1.0 / 120.0;
    private static final PhysicsVector GRAVITY = vector(0, -9.81, 0);
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
    void gravityIntegratesRealFreeFall() {
        try (var world = library.createWorld(PhysicsVector.ZERO, GRAVITY)) {
            var body = cube(world, BodyType.DYNAMIC, 0, 10, 0, 0.5, false);
            steps(world, 120);
            assertRange(body.pose().position().y(), 4.9, 5.2);
            assertRange(body.linearVelocity().y(), -10.0, -9.6);
            assertEquals(0, body.pose().position().x(), 1.0E-10);
            assertEquals(1, body.mass(), 1.0E-9);
        }
    }

    @Test
    void pausedCompoundIsAnImmovableObstacleAndRetainsSavedMotionForResume() {
        try (var world = library.createWorld(PhysicsVector.ZERO, GRAVITY)) {
            var obstacle = world.createBody(BodyType.DYNAMIC, pose(0, 4, 0),
                    List.of(box(vector(1, 0, 0), vector(2, 0.5, 2))), 1, 0.6, 0, true);
            var falling = cube(world, BodyType.DYNAMIC, 1, 7, 0, 0.5, true);
            obstacle.setVelocity(vector(2, -3, 1), vector(0.1, 0.2, 0.3));
            long handle = obstacle.handle;
            var before = obstacle.state();
            obstacle.setSimulationPaused(true);
            obstacle.setSimulationPaused(true);
            assertTrue(obstacle.isSimulationPaused());
            assertTrue(obstacle.isCcdEnabled());
            assertThrows(IllegalStateException.class, () -> obstacle.setVelocity(PhysicsVector.ZERO, PhysicsVector.ZERO));
            assertThrows(IllegalStateException.class, () -> obstacle.applyImpulse(vector(5, 0, 0), vector(0, 4, 0)));
            assertThrows(IllegalStateException.class, obstacle::sleep);
            assertThrows(IllegalStateException.class, obstacle::wake);
            assertThrows(IllegalStateException.class,
                    () -> world.createFixedJoint(obstacle, falling, PhysicsVector.ZERO, PhysicsVector.ZERO));
            assertThrows(IllegalStateException.class,
                    () -> world.createFixedJoint(falling, obstacle, PhysicsVector.ZERO, PhysicsVector.ZERO));
            steps(world, 360);
            assertEquals(before, obstacle.state(), "Contacts and gravity must not change a paused body or saved motion");
            assertEquals(5, falling.pose().position().y(), 0.05, "The paused compound must remain a collider");
            assertEquals(handle, obstacle.handle);
            obstacle.setPose(pose(10, 12, 0));
            steps(world, 6);
            assertEquals(pose(10, 12, 0), obstacle.pose());
            assertEquals(before.linearVelocity(), obstacle.linearVelocity());
            assertEquals(before.angularVelocity(), obstacle.angularVelocity());
            assertEquals(before.localCenterOfMass(), obstacle.localCenterOfMass());
            assertEquals(before.mass(), obstacle.mass());
            assertEquals(before.principalInertia(), obstacle.principalInertia());
            obstacle.setSimulationPaused(false);
            obstacle.setSimulationPaused(false);
            assertFalse(obstacle.isSimulationPaused());
            assertEquals(before.linearVelocity(), obstacle.linearVelocity());
            assertEquals(before.angularVelocity(), obstacle.angularVelocity());
            steps(world, 6);
            assertTrue(obstacle.pose().position().x() > 10);
            obstacle.setSimulationPaused(true);
            obstacle.close();
            assertDoesNotThrow(obstacle::close);
            assertThrows(IllegalStateException.class, obstacle::isSimulationPaused);
            assertDoesNotThrow(() -> world.step(DT, 1));
        }
    }

    @Test
    void pausedJointedAssemblyResumesAsTheSameRigidAssembly() {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var left = cube(world, BodyType.DYNAMIC, 0, 0, 0, 0.5, false);
            var right = cube(world, BodyType.DYNAMIC, 1, 0, 0, 0.5, false);
            world.createFixedJoint(left, right, vector(0.5, 0, 0), vector(-0.5, 0, 0));
            for (var part : List.of(left, right)) {
                part.setVelocity(vector(0, 1, 0), PhysicsVector.ZERO);
                part.setSimulationPaused(true);
            }
            steps(world, 120);
            assertEquals(pose(0, 0, 0), left.pose());
            assertEquals(pose(1, 0, 0), right.pose());
            for (var part : List.of(left, right)) part.setSimulationPaused(false);
            steps(world, 120);
            assertEquals(1, left.pose().position().y(), 0.02);
            assertVectorEquals(vector(1, 0, 0), right.pose().position().subtract(left.pose().position()), 0.02);
            // Paused removal must release joints, and closing the world must safely release
            // the remaining paused member without requiring a final simulation step.
            for (var part : List.of(left, right)) part.setSimulationPaused(true);
            left.close();
        }
    }

    @Test
    void pauseRestoresSleepingStateAndRejectsFixedBodiesWithoutFailingWorld() {
        try (var world = library.createWorld(PhysicsVector.ZERO, GRAVITY)) {
            var body = cube(world, BodyType.DYNAMIC, 0, 5, 0, 0.5, false);
            var fixed = cube(world, BodyType.FIXED, 10, 5, 0, 0.5, false);
            assertThrows(IllegalArgumentException.class, () -> fixed.setSimulationPaused(true));
            assertThrows(IllegalArgumentException.class, () -> fixed.setSimulationPaused(false));
            body.sleep();
            body.setSimulationPaused(true);
            steps(world, 12);
            assertTrue(body.isSleeping());
            body.setSimulationPaused(false);
            assertTrue(body.isSleeping());
            assertEquals(PhysicsVector.ZERO, body.linearVelocity());
            steps(world, 12);
            assertEquals(5, body.pose().position().y(), 0);
            body.wake();
            steps(world, 12);
            assertTrue(body.pose().position().y() < 5);
        }
    }

    @Test
    void fixedJointWiresTwoRealBodiesIntoOneAssembly() {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var left = cube(world, BodyType.DYNAMIC, 0, 0, 0, 0.5, false);
            var right = cube(world, BodyType.DYNAMIC, 1, 0, 0, 0.5, false);
            world.createFixedJoint(left, right, vector(0.5, 0, 0), vector(-0.5, 0, 0));
            left.setVelocity(vector(0, 1, 0), PhysicsVector.ZERO);
            steps(world, 6);
            var separation = right.pose().position().subtract(left.pose().position());
            assertEquals(1.0, separation.x(), 0.15);
            assertEquals(0.0, separation.y(), 0.15);
            assertEquals(0.0, separation.z(), 0.15);
        }
    }

    @Test
    void fixedFloorStopsAndSettlesFallingBody() {
        try (var world = library.createWorld(PhysicsVector.ZERO, GRAVITY)) {
            var floor = floor(world);
            var body = cube(world, BodyType.DYNAMIC, 0, 4, 0, 0.5, false);
            steps(world, 720);
            assertEquals(0.5, body.pose().position().y(), 0.05);
            assertEquals(0, body.linearVelocity().y(), 0.05);
            assertEquals(-0.5, floor.pose().position().y(), 0);
            assertTrue(body.isSleeping(), "A settled body should enter engine sleep");
        }
    }

    @Test
    void dynamicBodiesExchangeMomentumOnContact() {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var left = body(world, BodyType.DYNAMIC, pose(-2, 0, 0), boxes(0.5, 0.5, 0.5), 1, false);
            var right = body(world, BodyType.DYNAMIC, pose(2, 0, 0), boxes(0.5, 0.5, 0.5), 1, false);
            left.setVelocity(vector(2, 0, 0), PhysicsVector.ZERO);
            right.setVelocity(vector(-2, 0, 0), PhysicsVector.ZERO);
            steps(world, 180);
            assertTrue(left.linearVelocity().x() < -1.5, "Left body must rebound from the other dynamic body");
            assertTrue(right.linearVelocity().x() > 1.5, "Right body must rebound from the other dynamic body");
            assertEquals(0, left.linearVelocity().x() + right.linearVelocity().x(), 0.05);
            assertTrue(left.pose().position().x() < right.pose().position().x());
        }
    }

    @Test
    void offCenterImpulseUsesMassAndRotationalInertia() {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var body = cube(world, BodyType.DYNAMIC, 0, 0, 0, 0.5, false);
            body.applyImpulse(vector(1, 0, 0), vector(0, 0.5, 0));
            assertEquals(1, body.linearVelocity().x(), 0.02);
            assertRange(body.angularVelocity().z(), -3.1, -2.9);
            assertRange(body.principalInertia().x(), 0.16, 0.17);
            steps(world, 30);
            assertTrue(Math.abs(body.pose().rotation().z()) > 0.2);
            assertFinite(body.state());
        }
    }

    @Test
    void finiteForceIntegratesSecondsAndRespondsToMassWithoutPersistingAfterRelease() {
        double dt = 0.05 / 3;
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var light = world.createBody(BodyType.DYNAMIC, pose(0, 0, -2), boxes(0.5, 0.5, 0.5), 1, 0, 0, false);
            var heavy = world.createBody(BodyType.DYNAMIC, pose(0, 0, 2), boxes(0.5, 0.5, 0.5), 2, 0, 0, false);
            assertEquals(1, light.mass(), 1.0E-9);
            assertEquals(2, heavy.mass(), 1.0E-9);
            // Same six-unit force for exactly one second, refreshed at every actual substep.
            for (int step = 0; step < 60; step++) {
                light.applyImpulse(vector(6 * dt, 0, 0), light.pose().position());
                heavy.applyImpulse(vector(6 * dt, 0, 0), heavy.pose().position());
                world.step(dt, 1);
            }
            assertEquals(6, light.linearVelocity().x(), 1.0E-7);
            assertEquals(3, heavy.linearVelocity().x(), 1.0E-7);
            assertRange(light.pose().position().x(), 2.95, 3.1);
            assertRange(heavy.pose().position().x(), 1.475, 1.55);
            double releasedX = light.pose().position().x();
            for (int step = 0; step < 6; step++) world.step(dt, 1);
            assertEquals(6, light.linearVelocity().x(), 1.0E-7, "Released force must not remain in an engine accumulator");
            assertEquals(0.6, light.pose().position().x() - releasedX, 1.0E-7);
        }
    }

    @Test
    void repeatedOffCenterFiniteForceProducesIntegratedTorque() {
        double dt = 0.05 / 3;
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var body = cube(world, BodyType.DYNAMIC, 0, 0, 0, 0.5, false);
            for (int step = 0; step < 6; step++) {
                // A fixed world-space lever arm about the moving COM keeps torque exactly -1.
                var point = body.pose().position().add(vector(0, 0.5, 0));
                body.applyImpulse(vector(2 * dt, 0, 0), point);
                world.step(dt, 1);
            }
            assertEquals(0.2, body.linearVelocity().x(), 1.0E-7);
            assertEquals(-0.6, body.angularVelocity().z(), 1.0E-6);
            assertRange(body.pose().rotation().z(), -0.025, -0.01);
            assertFinite(body.state());
        }
    }

    @Test
    void sustainedFiniteForceCannotPushABodyThroughFixedCollisionGeometry() {
        double dt = 0.05 / 3;
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var wall = body(world, BodyType.FIXED, pose(0, 0, 0), boxes(0.25, 4, 4), 0, false);
            var pushed = cube(world, BodyType.DYNAMIC, -2, 0, 0, 0.5, true);
            for (int step = 0; step < 120; step++) {
                pushed.applyImpulse(vector(20 * dt, 0, 0), pushed.pose().position());
                world.step(dt, 1);
                assertTrue(pushed.pose().position().x() < -0.65,
                        "A force request must still pass through the solver's contact response");
            }
            assertRange(pushed.pose().position().x(), -0.82, -0.70);
            assertEquals(0, pushed.linearVelocity().x(), 0.1);
            assertEquals(0, wall.pose().position().x(), 0);
            assertFinite(pushed.state());
        }
    }

    @Test
    void compoundHonorsChildRotationTranslationAndBodyRotation() {
        var quarterTurn = new PhysicsQuaternion(0, 0, Math.sqrt(0.5), Math.sqrt(0.5));
        try (var world = library.createWorld(PhysicsVector.ZERO, GRAVITY)) {
            // Two quarter turns make a horizontal slab whose center is at world (-1, 0, 0).
            // Ignoring either the child rotation or either transform makes this landing fail.
            world.createBody(BodyType.FIXED, new PhysicsPose(PhysicsVector.ZERO, quarterTurn),
                    List.of(new PhysicsBox(vector(0, 1, 0), quarterTurn, vector(2, 0.25, 2))), 1, 0.6, 0, false);
            var falling = cube(world, BodyType.DYNAMIC, -2.5, 3, 0, 0.25, false);
            steps(world, 600);
            assertEquals(0.5, falling.pose().position().y(), 0.06);
        }
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var compound = world.createBody(BodyType.DYNAMIC, pose(0, 0, 0), List.of(
                    box(vector(-1, 0, 0), vector(0.5, 0.5, 0.5)),
                    box(vector(1, 0, 0), vector(0.25, 0.25, 0.25))), 1, 0, 0, false);
            assertEquals(-7.0 / 9.0, compound.localCenterOfMass().x(), 1.0E-8);
            assertEquals(1.125, compound.mass(), 1.0E-8);
            compound.setVelocity(PhysicsVector.ZERO, vector(0, 0, 2));
            steps(world, 60);
            assertRange(Math.abs(compound.pose().rotation().z()), 0.4, 0.6);
            assertFinite(compound.state());
        }
    }

    @Test
    void sleepingBodyWakesOnImpulseAndExplicitWake() {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var body = cube(world, BodyType.DYNAMIC, 0, 0, 0, 0.5, false);
            body.sleep();
            assertTrue(body.isSleeping());
            steps(world, 10);
            assertEquals(0, body.pose().position().x(), 0);
            body.applyImpulse(vector(1, 0, 0), PhysicsVector.ZERO);
            assertFalse(body.isSleeping());
            steps(world, 10);
            assertTrue(body.pose().position().x() > 0.05);
            body.sleep();
            assertTrue(body.isSleeping());
            body.wake();
            assertFalse(body.isSleeping());
        }
    }

    @Test
    void automaticCcdStopsFastBodyAtFixedWallWithoutBulletUpgrade() {
        // Rapier 0.36.1 automatically sweeps fixed geometry for every fast dynamic body.
        var result = crossThinWall(BodyType.FIXED, false);
        System.out.println("Native physics automatic static CCD: " + result);
        assertTrue(result.projectileX() > -0.3 && result.projectileX() < -0.05,
                () -> "Automatic CCD must stop at the fixed wall: " + result);
        assertEquals(0, result.wallX(), 0, result.toString());
        assertEquals(0, result.wallVx(), 0, result.toString());
    }

    @Test
    void bulletCcdPreventsCrossingDynamicThinWallAtHighSpeed() {
        // The per-body flag upgrades CCD to sweep dynamic/kinematic targets (excluding bullets).
        // The stationary dynamic target is deliberately non-bullet in both otherwise identical worlds.
        var withoutCcd = crossThinWall(BodyType.DYNAMIC, false);
        var withCcd = crossThinWall(BodyType.DYNAMIC, true);
        String evidence = "control=" + withoutCcd + ", bullet=" + withCcd;
        System.out.println("Native physics bullet CCD: " + evidence);
        assertEquals(3, withoutCcd.projectileX(), 0.05,
                "Control must cross the entire wall in one step: " + evidence);
        assertTrue(withCcd.projectileX() > -0.3 && withCcd.projectileX() < -0.05,
                () -> "Bullet CCD must stop at the dynamic wall: " + evidence);
        assertEquals(0, withoutCcd.wallX(), 0.01, evidence);
        assertEquals(0, withCcd.wallX(), 0.01, evidence);
    }

    @Test
    void removingBodyRemovesColliderAndRejectsStaleWrapper() {
        try (var world = library.createWorld(PhysicsVector.ZERO, GRAVITY)) {
            var floor = floor(world);
            var falling = cube(world, BodyType.DYNAMIC, 0, 3, 0, 0.5, false);
            floor.close();
            floor.close();
            assertThrows(IllegalStateException.class, floor::pose);
            var replacement = cube(world, BodyType.FIXED, 10, -0.5, 0, 0.5, false);
            assertThrows(IllegalStateException.class, () -> floor.setPose(pose(0, 0, 0)));
            assertEquals(10, replacement.pose().position().x(), 0);
            steps(world, 120);
            assertTrue(falling.pose().position().y() < -1, "Removed floor collider remained in the solver");
        }
    }

    @Test
    void closingWorldAndLibraryInvalidatesDependentHandles() {
        var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO);
        var body = cube(world, BodyType.DYNAMIC, 0, 0, 0, 0.5, false);
        world.close();
        world.close();
        assertThrows(IllegalStateException.class, body::state);
        assertThrows(IllegalStateException.class, () -> world.step(DT, 1));
        assertThrows(IllegalStateException.class, () -> cube(world, BodyType.DYNAMIC, 0, 0, 0, 0.5, false));
        body.close();
        var nextWorld = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO);
        var nextBody = cube(nextWorld, BodyType.DYNAMIC, 3, 0, 0, 0.5, false);
        assertThrows(IllegalStateException.class, body::pose);
        assertEquals(3, nextBody.pose().position().x(), 0);
        library.close();
        library.close();
        assertThrows(IllegalStateException.class, nextBody::pose);
        assertThrows(IllegalStateException.class, () -> nextWorld.step(DT, 1));
        assertThrows(IllegalStateException.class, () -> library.createWorld(PhysicsVector.ZERO, GRAVITY));
    }

    @Test
    void largeWorldOriginRetainsSubBlockPrecision() {
        var origin = vector(29_999_000, 100, -29_999_000);
        try (var near = library.createWorld(PhysicsVector.ZERO, GRAVITY);
             var far = library.createWorld(origin, GRAVITY)) {
            var a = cube(near, BodyType.DYNAMIC, 0.125, 10, -0.25, 0.5, false);
            var b = cube(far, BodyType.DYNAMIC, origin.x() + 0.125, origin.y() + 10, origin.z() - 0.25, 0.5, false);
            a.setVelocity(vector(0.125, 0, -0.0625), PhysicsVector.ZERO);
            b.setVelocity(vector(0.125, 0, -0.0625), PhysicsVector.ZERO);
            for (int step = 0; step < 120; step++) {
                near.step(DT, 1);
                far.step(DT, 1);
            }
            var p = a.pose().position();
            var q = b.pose().position();
            assertEquals(p.x(), q.x() - origin.x(), 1.0E-6);
            assertEquals(p.y(), q.y() - origin.y(), 1.0E-6);
            assertEquals(p.z(), q.z() - origin.z(), 1.0E-6);
            assertEquals(a.linearVelocity().y(), b.linearVelocity().y(), 1.0E-9);

            var destination = vector(0.5, 8, -0.75);
            var rotation = new PhysicsQuaternion(0, Math.sqrt(0.5), 0, Math.sqrt(0.5));
            a.setPose(new PhysicsPose(destination, rotation));
            b.setPose(new PhysicsPose(origin.add(destination), rotation));
            assertVectorEquals(destination, a.pose().position(), 1.0E-9);
            assertVectorEquals(destination, b.pose().position().subtract(origin), 1.0E-6);
            assertSameRotation(rotation, a.pose().rotation());
            assertSameRotation(rotation, b.pose().rotation());
            a.setVelocity(PhysicsVector.ZERO, PhysicsVector.ZERO);
            b.setVelocity(PhysicsVector.ZERO, PhysicsVector.ZERO);
            var point = destination.add(vector(0, 0.5, 0));
            a.applyImpulse(vector(3, 0, 0), point);
            b.applyImpulse(vector(3, 0, 0), origin.add(point));
            assertEquals(3, a.linearVelocity().x(), 0.02);
            assertEquals(-9, a.angularVelocity().z(), 0.05);
            assertVectorEquals(a.linearVelocity(), b.linearVelocity(), 1.0E-9);
            assertVectorEquals(a.angularVelocity(), b.angularVelocity(), 1.0E-9);
            steps(near, 30);
            steps(far, 30);
            assertVectorEquals(a.pose().position(), b.pose().position().subtract(origin), 1.0E-6);
            assertSameRotation(a.pose().rotation(), b.pose().rotation());
        }
    }

    @Test
    void invalidInputAndBodyBudgetDoNotMutateValidWorld() {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var body = cube(world, BodyType.DYNAMIC, 0, 0, 0, 0.5, false);
            var original = body.state();
            for (double dt : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0, -DT, 0.2, 1.0E-7}) {
                assertThrows(RuntimeException.class, () -> world.step(dt, 1));
            }
            assertThrows(RuntimeException.class, () -> world.step(DT, 0));
            assertThrows(RuntimeException.class, () -> world.step(DT, 17));
            assertThrows(RuntimeException.class, () -> new PhysicsVector(Double.NaN, 0, 0));
            assertThrows(RuntimeException.class, () -> new PhysicsQuaternion(0, 0, 0, 0));
            assertThrows(RuntimeException.class, () -> box(PhysicsVector.ZERO, vector(0, 1, 1)));
            assertThrows(RuntimeException.class, () -> world.createBody(BodyType.DYNAMIC, pose(0, 0, 0),
                    boxes(0.5, 0.5, 0.5), -1, 0, 0, false));
            assertThrows(RuntimeException.class, () -> world.createBody(BodyType.DYNAMIC, pose(0, 0, 0),
                    Collections.nCopies(257, box(PhysicsVector.ZERO, vector(0.1, 0.1, 0.1))), 1, 0, 0, false));
            assertEquals(original, body.state(), "Rejected calls must not advance or rewrite a live body");
            for (int index = 1; index < NativePhysicsWorld.MAX_BODIES; index++) cube(world, BodyType.FIXED, index * 2, 0, 0, 0.25, false);
            assertThrows(RuntimeException.class, () -> cube(world, BodyType.FIXED, 300, 0, 0, 0.25, false));
            body.applyImpulse(vector(1, 0, 0), PhysicsVector.ZERO);
            world.step(DT, 1);
            assertTrue(body.pose().position().x() > 0, "World stopped functioning after capacity rejection");
        }
    }

    @Test
    void worldCapacityIsReleasedByClose() {
        var worlds = new ArrayList<NativePhysicsWorld>();
        try {
            for (int i = 0; i < 8; i++) worlds.add(library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO));
            assertThrows(RuntimeException.class, () -> library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO));
            worlds.removeFirst().close();
            var replacement = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO);
            worlds.add(replacement);
            replacement.step(DT, 1);
        } finally {
            worlds.forEach(NativePhysicsWorld::close);
        }
    }

    @Test
    void compoundBudgetIsReclaimedWhenItsBodyIsRemoved() {
        var compound = new ArrayList<PhysicsBox>();
        for (int index = 0; index < 128; index++) {
            compound.add(box(vector((index % 8) * 0.1, (index / 8 % 8) * 0.1, (index / 64) * 0.1),
                    vector(0.04, 0.04, 0.04)));
        }
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var bodies = new ArrayList<NativePhysicsBody>();
            for (int index = 0; index < NativePhysicsWorld.MAX_BODIES; index++) {
                bodies.add(body(world, BodyType.FIXED, pose(index * 3, 0, 0), compound, 0, false));
            }
            assertThrows(RuntimeException.class, () -> cube(world, BodyType.DYNAMIC, 0, 3, 0, 0.25, false));
            bodies.removeFirst().close();
            var replacement = body(world, BodyType.DYNAMIC, pose(0, 3, 0), compound, 0, false);
            world.step(DT, 1);
            assertFinite(replacement.state());
        }
    }

    @Test
    void substepsDivideTheTotalDuration() {
        try (var fine = library.createWorld(PhysicsVector.ZERO, GRAVITY);
             var divided = library.createWorld(PhysicsVector.ZERO, GRAVITY)) {
            var a = cube(fine, BodyType.DYNAMIC, 0, 10, 0, 0.5, false);
            var b = cube(divided, BodyType.DYNAMIC, 0, 10, 0, 0.5, false);
            steps(fine, 120);
            for (int index = 0; index < 60; index++) divided.step(DT * 2, 2);
            assertEquals(a.pose().position().y(), b.pose().position().y(), 1.0E-9);
            assertEquals(a.linearVelocity().y(), b.linearVelocity().y(), 1.0E-9);
        }
    }

    @Test
    void worldRejectsCrossThreadAccess() throws Exception {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO);
             var executor = Executors.newSingleThreadExecutor()) {
            var body = cube(world, BodyType.DYNAMIC, 0, 0, 0, 0.5, false);
            var future = executor.submit(() -> {
                try {
                    body.pose();
                    return null;
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertInstanceOf(IllegalStateException.class, future.get(5, TimeUnit.SECONDS));
            world.step(DT, 1);
            assertFinite(body.state());
        }
    }

    @Test
    void fixedSeedFiniteSceneRemainsBounded() {
        var random = new Random(0x41CA_DE26L);
        try (var world = library.createWorld(PhysicsVector.ZERO, GRAVITY)) {
            floor(world);
            var bodies = new ArrayList<NativePhysicsBody>();
            for (int index = 0; index < 12; index++) {
                bodies.add(cube(world, BodyType.DYNAMIC, (index % 4 - 1.5) * 1.5,
                        2 + index / 4 * 1.5 + random.nextDouble() * 0.2,
                        random.nextDouble() - 0.5, 0.3, true));
            }
            steps(world, 720);
            for (var body : bodies) {
                assertFinite(body.state());
                assertRange(body.pose().position().y(), 0.2, 4);
                assertTrue(Math.abs(body.pose().position().x()) < 8);
            }
        }
    }

    private ThinWallResult crossThinWall(BodyType wallType, boolean ccd) {
        try (var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO)) {
            var wall = body(world, wallType, pose(0, 0, 0), boxes(0.05, 10, 10), 0, false);
            var fast = cube(world, BodyType.DYNAMIC, -2, 0, 0, 0.1, ccd);
            assertEquals(ccd, fast.isCcdEnabled());
            assertFalse(wall.isCcdEnabled());
            // Stay below the engine's default 400 m/s speed cap, moving five metres this step.
            fast.setVelocity(vector(300, 0, 0), PhysicsVector.ZERO);
            world.step(1.0 / 60.0, 1);
            var projectileState = fast.state();
            var wallState = wall.state();
            assertFinite(projectileState);
            assertFinite(wallState);
            return new ThinWallResult(projectileState.pose().position().x(), projectileState.linearVelocity().x(),
                    wallState.pose().position().x(), wallState.linearVelocity().x());
        }
    }

    private record ThinWallResult(double projectileX, double projectileVx, double wallX, double wallVx) {}

    private static NativePhysicsBody floor(NativePhysicsWorld world) {
        return body(world, BodyType.FIXED, pose(0, -0.5, 0), boxes(10, 0.5, 10), 0, false);
    }

    private static NativePhysicsBody cube(NativePhysicsWorld world, BodyType type,
                                          double x, double y, double z, double halfExtent, boolean ccd) {
        return body(world, type, pose(x, y, z), boxes(halfExtent, halfExtent, halfExtent), 0, ccd);
    }

    private static NativePhysicsBody body(NativePhysicsWorld world, BodyType type, PhysicsPose pose,
                                          List<PhysicsBox> boxes, double restitution, boolean ccd) {
        return world.createBody(type, pose, boxes, 1, 0.6, restitution, ccd);
    }

    private static PhysicsPose pose(double x, double y, double z) {
        return new PhysicsPose(vector(x, y, z), PhysicsQuaternion.IDENTITY);
    }

    private static PhysicsVector vector(double x, double y, double z) {
        return new PhysicsVector(x, y, z);
    }

    private static PhysicsBox box(PhysicsVector center, PhysicsVector halfExtents) {
        return new PhysicsBox(center, PhysicsQuaternion.IDENTITY, halfExtents);
    }

    private static List<PhysicsBox> boxes(double x, double y, double z) {
        return List.of(box(PhysicsVector.ZERO, vector(x, y, z)));
    }

    private static void steps(NativePhysicsWorld world, int count) {
        for (int index = 0; index < count; index++) world.step(DT, 1);
    }

    private static void assertRange(double actual, double minimum, double maximum) {
        assertTrue(Double.isFinite(actual) && actual >= minimum && actual <= maximum,
                () -> "Expected " + actual + " in [" + minimum + ", " + maximum + "]");
    }

    private static void assertVectorEquals(PhysicsVector expected, PhysicsVector actual, double tolerance) {
        assertEquals(expected.x(), actual.x(), tolerance);
        assertEquals(expected.y(), actual.y(), tolerance);
        assertEquals(expected.z(), actual.z(), tolerance);
    }

    private static void assertSameRotation(PhysicsQuaternion expected, PhysicsQuaternion actual) {
        double dot = expected.x() * actual.x() + expected.y() * actual.y()
                + expected.z() * actual.z() + expected.w() * actual.w();
        assertEquals(1, Math.abs(dot), 1.0E-9, "Expected the same orientation (quaternion sign is immaterial)");
    }

    private static void assertFinite(PhysicsBodyState state) {
        for (var vector : List.of(state.pose().position(), state.linearVelocity(), state.angularVelocity(),
                state.localCenterOfMass(), state.principalInertia())) {
            assertTrue(Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z()));
        }
        var q = state.pose().rotation();
        assertEquals(1, q.x() * q.x() + q.y() * q.y() + q.z() * q.z() + q.w() * q.w(), 1.0E-8);
    }
}
