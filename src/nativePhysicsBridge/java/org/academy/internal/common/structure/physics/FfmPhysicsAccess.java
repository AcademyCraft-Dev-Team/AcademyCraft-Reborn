package org.academy.internal.common.structure.physics;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** ABI v2. Native memory and pointers never escape a synchronous downcall. */
public final class FfmPhysicsAccess implements PhysicsNativeAccess {
    private final Thread owner = Thread.currentThread();
    private final Arena libraryArena;
    private final Map<String, MethodHandle> functions = new HashMap<>();

    /**
     * This class is defined in its own unnamed module. JDK 25 permits restricted FFM calls from
     * an unnamed module with a warning, so the bundled solver works without a launcher flag.
     * Deployments that want the strict pre-JDK-25 contract can set
     * {@code -Dacademy.nativePhysics.requireNativeAccess=true}; the permission probe uses it.
     */
    public static PhysicsNativeAccess open(Path library) {
        if (Boolean.getBoolean("academy.nativePhysics.requireNativeAccess")
                && !FfmPhysicsAccess.class.getModule().isNativeAccessEnabled()) {
            throw new IllegalStateException("Native physics bridge requires --enable-native-access=ALL-UNNAMED");
        }
        var arena = Arena.ofConfined();
        try {
            return new FfmPhysicsAccess(SymbolLookup.libraryLookup(library, arena), arena);
        } catch (RuntimeException | Error failure) {
            try {
                arena.close();
            } catch (RuntimeException | AssertionError | LinkageError cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private FfmPhysicsAccess(SymbolLookup symbols, Arena libraryArena) {
        this.libraryArena = libraryArena;
        var linker = Linker.nativeLinker();
        bind(linker, symbols, "abi_version", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        bind(linker, symbols, "engine_version", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        // Reject an older binary before looking up symbols introduced by this ABI.
        try {
            int actual = (int) functions.get("abi_version").invokeExact();
            if (actual != NativePhysicsArtifact.ABI) throw new IllegalStateException("Native physics exported ABI mismatch: " + actual);
            int engine = (int) functions.get("engine_version").invokeExact();
            if (engine != NativePhysicsArtifact.ENGINE_VERSION) {
                throw new IllegalStateException("Native physics exported engine version mismatch: " + engine);
            }
        } catch (Throwable failure) {
            throw invocationFailure("abi_version", failure);
        }
        bind(linker, symbols, "world_create", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        bind(linker, symbols, "world_destroy", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        bind(linker, symbols, "world_step", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT));
        bind(linker, symbols, "body_create", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        for (var name : new String[]{"body_state", "body_set_pose", "body_set_velocity", "body_impulse_at_point"}) {
            bind(linker, symbols, name, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        }
        for (var name : new String[]{"body_sleep", "body_wake", "body_remove"}) {
            bind(linker, symbols, name, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        }
        bind(linker, symbols, "body_set_simulation_paused", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        bind(linker, symbols, "fixed_joint_create", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    }

    private void bind(Linker linker, SymbolLookup symbols, String name, FunctionDescriptor descriptor) {
        var address = symbols.find("academy_physics_" + name)
                .orElseThrow(() -> new IllegalStateException("Missing native physics ABI symbol: " + name));
        functions.put(name, linker.downcallHandle(address, descriptor));
    }

    @Override
    public void setSimulationPaused(long world, long body, boolean paused) {
        requireOpen();
        try {
            check("body_set_simulation_paused",
                    (int) functions.get("body_set_simulation_paused").invokeExact(world, body, paused ? 1 : 0));
        } catch (Throwable failure) {
            throw invocationFailure("body_set_simulation_paused", failure);
        }
    }

    @Override
    public long createWorld(double[] gravity) {
        requireOpen();
        try (var arena = Arena.ofConfined()) {
            var input = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, gravity);
            var output = arena.allocate(ValueLayout.JAVA_LONG);
            check("world_create", (int) functions.get("world_create").invokeExact(input, gravity.length, output, 1));
            return requireHandle(output.get(ValueLayout.JAVA_LONG, 0));
        } catch (Throwable failure) {
            throw invocationFailure("world_create", failure);
        }
    }

    @Override
    public void destroyWorld(long world) {
        requireOpen();
        try {
            check("world_destroy", (int) functions.get("world_destroy").invokeExact(world));
        } catch (Throwable failure) {
            throw invocationFailure("world_destroy", failure);
        }
    }

    @Override
    public void step(long world, double duration, int substeps) {
        requireOpen();
        try {
            check("world_step", (int) functions.get("world_step").invokeExact(world, duration, substeps));
        } catch (Throwable failure) {
            throw invocationFailure("world_step", failure);
        }
    }

    @Override
    public long createBody(long world, int kind, double[] description, double[] boxes) {
        requireOpen();
        try (var arena = Arena.ofConfined()) {
            var input = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, description);
            var shapes = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, boxes);
            var output = arena.allocate(ValueLayout.JAVA_LONG);
            check("body_create", (int) functions.get("body_create").invokeExact(
                    world, kind, input, description.length, shapes, boxes.length, output, 1));
            return requireHandle(output.get(ValueLayout.JAVA_LONG, 0));
        } catch (Throwable failure) {
            throw invocationFailure("body_create", failure);
        }
    }

    @Override
    public double[] state(long world, long body) {
        requireOpen();
        try (var arena = Arena.ofConfined()) {
            var output = arena.allocate(ValueLayout.JAVA_DOUBLE, 26);
            check("body_state", (int) functions.get("body_state").invokeExact(world, body, output, 26));
            return output.toArray(ValueLayout.JAVA_DOUBLE);
        } catch (Throwable failure) {
            throw invocationFailure("body_state", failure);
        }
    }

    @Override public void setPose(long world, long body, double[] pose) { arrayCall("body_set_pose", world, body, pose); }
    @Override public void setVelocity(long world, long body, double[] velocity) { arrayCall("body_set_velocity", world, body, velocity); }
    @Override public void impulse(long world, long body, double[] impulseAndPoint) { arrayCall("body_impulse_at_point", world, body, impulseAndPoint); }
    @Override public void sleep(long world, long body) { pairCall("body_sleep", world, body); }
    @Override public void wake(long world, long body) { pairCall("body_wake", world, body); }
    @Override public void removeBody(long world, long body) { pairCall("body_remove", world, body); }

    @Override
    public void createFixedJoint(long world, long body1, long body2, double[] anchors) {
        requireOpen();
        try (var arena = Arena.ofConfined()) {
            var input = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, anchors);
            check("fixed_joint_create", (int) functions.get("fixed_joint_create").invokeExact(
                    world, body1, body2, input, anchors.length));
        } catch (Throwable failure) {
            throw invocationFailure("fixed_joint_create", failure);
        }
    }

    private void arrayCall(String name, long world, long body, double[] values) {
        requireOpen();
        try (var arena = Arena.ofConfined()) {
            MemorySegment input = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, values);
            check(name, (int) functions.get(name).invokeExact(world, body, input, values.length));
        } catch (Throwable failure) {
            throw invocationFailure(name, failure);
        }
    }

    private void pairCall(String name, long world, long body) {
        requireOpen();
        try {
            check(name, (int) functions.get(name).invokeExact(world, body));
        } catch (Throwable failure) {
            throw invocationFailure(name, failure);
        }
    }

    private static long requireHandle(long handle) {
        if (handle == 0) throw new IllegalStateException("Native physics returned a null handle");
        return handle;
    }

    private void requireOpen() {
        requireThread();
        if (!libraryArena.scope().isAlive()) throw new IllegalStateException("Native physics bridge is closed");
    }

    private void requireThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Native physics bridge is confined to its creating thread");
    }

    @Override
    public void close() {
        requireThread();
        if (libraryArena.scope().isAlive()) libraryArena.close();
    }

    private static void check(String operation, int status) {
        if (status != 0) throw new NativePhysicsException(operation, status);
    }

    private static RuntimeException invocationFailure(String operation, Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException("Cannot invoke native physics ABI: " + operation, failure);
    }
}
