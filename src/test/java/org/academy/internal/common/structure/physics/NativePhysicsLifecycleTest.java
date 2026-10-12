package org.academy.internal.common.structure.physics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NativePhysicsLifecycleTest {
    private static final List<PhysicsBox> BOX = List.of(new PhysicsBox(PhysicsVector.ZERO,
            PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5)));

    @Test
    void convertsFarOriginPositionsAndImpulsePointsWithoutFloatRounding() {
        var access = new FakeAccess();
        try (var library = new NativePhysicsLibrary(access, () -> {})) {
            var origin = new PhysicsVector(29_999_000, 100, -29_999_000);
            var world = library.createWorld(origin, PhysicsVector.ZERO);
            var position = origin.add(new PhysicsVector(0.125, 2, -0.25));
            var body = world.createBody(BodyType.DYNAMIC, new PhysicsPose(position, PhysicsQuaternion.IDENTITY), BOX, 1, 0.5, 0, true);
            assertArrayEquals(new double[]{0.125, 2, -0.25}, java.util.Arrays.copyOf(access.description, 3));
            assertEquals(position, body.pose().position());
            body.applyImpulse(new PhysicsVector(3, 0, 0), origin.add(new PhysicsVector(0.5, 1, 0)));
            assertArrayEquals(new double[]{3, 0, 0, 0.5, 1, 0}, access.impulse);
        }
    }

    @Test
    void destroysWorldsInReverseOrderBeforeReleasingCodeAndInvalidatesBodies() {
        var access = new FakeAccess();
        var library = new NativePhysicsLibrary(access, () -> access.events.add("release"));
        var first = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO);
        var body = body(first);
        var second = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO);
        library.close();
        assertEquals(List.of("destroy:3", "destroy:1", "close-access", "release"), access.events);
        assertThrows(IllegalStateException.class, body::state);
        assertThrows(IllegalStateException.class, () -> second.step(0.05, 3));
        assertThrows(IllegalStateException.class, () -> library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO));
        assertDoesNotThrow(body::close);
        assertDoesNotThrow(first::close);
        assertDoesNotThrow(library::close);
        assertEquals(4, access.events.size());
    }

    @Test
    void destroyFailureRetainsArenaAndAllowsOnlyCloseRetry() {
        var access = new FakeAccess();
        var library = new NativePhysicsLibrary(access, () -> access.events.add("release"));
        var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO);
        var body = body(world);
        access.destroyFails = true;
        assertThrows(NativePhysicsException.class, library::close);
        assertFalse(access.events.contains("release"));
        assertThrows(IllegalStateException.class, body::state);
        assertThrows(IllegalStateException.class, () -> library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO));
        access.destroyFails = false;
        library.close();
        assertEquals(List.of("destroy:1", "close-access", "release"), access.events);
    }

    @Test
    void accessCloseFailureRetainsPrivateCopyAndCanBeRetried() {
        var access = new FakeAccess();
        var library = new NativePhysicsLibrary(access, () -> access.events.add("release"));
        library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO);
        access.closeFails = true;
        assertThrows(IllegalStateException.class, library::close);
        assertEquals(List.of("destroy:1"), access.events);
        assertThrows(IllegalStateException.class, () -> library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO));
        access.closeFails = false;
        library.close();
        assertEquals(List.of("destroy:1", "close-access", "release"), access.events);
    }

    @Test
    void nativePanicPoisonsOnlyThatWorldButItCanStillBeDestroyed() {
        var access = new FakeAccess();
        try (var library = new NativePhysicsLibrary(access, () -> {})) {
            var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO);
            var body = body(world);
            access.stepFails = true;
            assertThrows(NativePhysicsException.class, () -> world.step(0.05, 3));
            access.stepFails = false;
            assertThrows(IllegalStateException.class, () -> world.step(0.05, 3));
            assertThrows(IllegalStateException.class, body::state);
            assertDoesNotThrow(world::close);
            assertDoesNotThrow(() -> library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO).step(0.05, 3));
        }
    }

    @Test
    void rejectedCapacityAndArgumentsDoNotPoisonWorld() {
        var access = new FakeAccess();
        try (var library = new NativePhysicsLibrary(access, () -> {})) {
            var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO);
            for (int i = 0; i < NativePhysicsWorld.MAX_BODIES; i++) body(world);
            assertThrows(IllegalStateException.class, () -> body(world));
            assertThrows(IllegalArgumentException.class, () -> world.step(Double.NaN, 1));
            assertThrows(IllegalArgumentException.class, () -> world.step(0.05, 0));
            assertThrows(IllegalArgumentException.class, () -> world.step(1.0E-6, 16));
            assertDoesNotThrow(() -> world.step(0.05, 3));
            assertEquals(128, access.states.size());
        }
    }

    @Test
    void removedBodyCannotReachReusedWorldButCapacityIsReleased() {
        var access = new FakeAccess();
        try (var library = new NativePhysicsLibrary(access, () -> {})) {
            var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO);
            var old = body(world);
            old.close();
            var replacement = body(world);
            assertThrows(IllegalStateException.class, () -> old.setPose(PhysicsPose.IDENTITY));
            assertDoesNotThrow(old::close);
            assertDoesNotThrow(replacement::state);
            assertEquals(1, access.states.size());
        }
    }

    @Test
    void wrongThreadCannotQueryOrCloseNativeObjects() throws InterruptedException {
        var access = new FakeAccess();
        try (var library = new NativePhysicsLibrary(access, () -> {})) {
            var world = library.createWorld(PhysicsVector.ZERO, PhysicsVector.ZERO);
            var body = body(world);
            var error = new AtomicReference<Throwable>();
            var thread = new Thread(() -> {
                try {
                    assertThrows(IllegalStateException.class, body::state);
                    assertThrows(IllegalStateException.class, world::close);
                    assertThrows(IllegalStateException.class, library::close);
                } catch (Throwable failure) { error.set(failure); }
            });
            thread.start();
            thread.join();
            assertNull(error.get());
            assertTrue(access.events.isEmpty());
        }
    }

    private static NativePhysicsBody body(NativePhysicsWorld world) {
        return world.createBody(BodyType.DYNAMIC, PhysicsPose.IDENTITY, BOX, 1, 0.5, 0, false);
    }

    private static final class FakeAccess implements PhysicsNativeAccess {
        long next = 1;
        final List<String> events = new ArrayList<>();
        final Map<Long, double[]> states = new HashMap<>();
        double[] description;
        double[] impulse;
        boolean destroyFails;
        boolean stepFails;
        boolean closeFails;

        @Override public void close() {
            if (closeFails) throw new IllegalStateException("synthetic access close failure");
            events.add("close-access");
        }

        @Override public long createWorld(double[] gravity) { return next++; }
        @Override public void destroyWorld(long world) {
            if (destroyFails) throw new NativePhysicsException("world_destroy", 5);
            events.add("destroy:" + world);
        }
        @Override public void step(long world, double duration, int substeps) {
            if (stepFails) throw new NativePhysicsException("world_step", 6);
        }
        @Override public long createBody(long world, int kind, double[] description, double[] boxes) {
            this.description = description.clone();
            long id = next++;
            var state = new double[26];
            System.arraycopy(description, 0, state, 0, 13);
            state[13] = 1;
            state[23] = 1;
            state[25] = kind;
            states.put(id, state);
            return id;
        }
        @Override public double[] state(long world, long body) { return states.get(body).clone(); }
        @Override public void setPose(long world, long body, double[] pose) { System.arraycopy(pose, 0, states.get(body), 0, 7); }
        @Override public void setVelocity(long world, long body, double[] velocity) { System.arraycopy(velocity, 0, states.get(body), 7, 6); }
        @Override public void impulse(long world, long body, double[] impulseAndPoint) { impulse = impulseAndPoint.clone(); }
        @Override public void sleep(long world, long body) { states.get(body)[24] = 1; }
        @Override public void wake(long world, long body) { states.get(body)[24] = 0; }
        @Override public void removeBody(long world, long body) { states.remove(body); }
    }
}
