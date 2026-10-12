package org.academy.internal.common.structure.physics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Opt-in internal prototype. Merely referencing this class never loads native code. */
public final class NativePhysicsLibrary implements AutoCloseable {
    public static final int MAX_WORLDS = 8;
    private final Thread owner = Thread.currentThread();
    private final PhysicsNativeAccess access;
    private final Runnable release;
    private final Set<NativePhysicsWorld> worlds = new LinkedHashSet<>();
    private boolean closing;
    private boolean closed;

    NativePhysicsLibrary(PhysicsNativeAccess access, Runnable release) {
        this.access = Objects.requireNonNull(access, "access");
        this.release = Objects.requireNonNull(release, "release");
    }

    public static NativePhysicsLibrary openConfigured() throws IOException {
        // Read explicit properties first: no classpath probing, download, global search or fallback.
        var artifact = NativePhysicsArtifact.configured();
        Path directory = Files.createTempDirectory("academy-native-physics-");
        Path library = directory.resolve(artifact.library().getFileName());
        PhysicsNativeAccess access = null;
        try {
            // Load a unique verified copy, so rebuilding the source artifact cannot replace code
            // between its hash check and the OS loader opening the library.
            NativePhysicsArtifact.copyBounded(artifact.library(), library);
            if (!NativePhysicsArtifact.digest(library).equals(artifact.sha256())) {
                throw new IOException("Native physics artifact changed while preparing its private copy");
            }
            access = NativePhysicsBridgeLoader.open(library);
            return new NativePhysicsLibrary(access, () -> deleteCopy(library, directory));
        } catch (IOException | RuntimeException | Error failure) {
            try {
                if (access != null) access.close();
            } catch (RuntimeException | AssertionError | LinkageError cleanup) {
                failure.addSuppressed(cleanup);
            }
            try {
                deleteCopy(library, directory);
            } catch (RuntimeException | AssertionError | LinkageError cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /** The actual binding module, rather than an inference from the launcher arguments. */
    public String bindingModuleName() {
        requireOpen();
        var module = access.getClass().getModule();
        return module.isNamed() ? module.getName() : "ALL-UNNAMED";
    }

    public boolean bindingNativeAccessEnabled() {
        requireOpen();
        return access.getClass().getModule().isNativeAccessEnabled();
    }

    public NativePhysicsWorld createWorld(PhysicsVector worldOrigin, PhysicsVector gravity) {
        requireOpen();
        Objects.requireNonNull(worldOrigin, "worldOrigin");
        Objects.requireNonNull(gravity, "gravity");
        PhysicsLimits.vector(gravity, 1.0E4, "gravity");
        if (worlds.size() >= MAX_WORLDS) throw new IllegalStateException("Native physics world limit reached");
        var world = new NativePhysicsWorld(this, access, access.createWorld(gravity.array()), worldOrigin);
        worlds.add(world);
        return world;
    }

    void requireOpen() {
        requireThread();
        if (closed || closing) throw new IllegalStateException("Native physics library is closing or closed");
    }

    void requireThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Native physics is confined to its creating thread");
    }

    void removed(NativePhysicsWorld world) {
        worlds.remove(world);
    }

    @Override
    public void close() {
        requireThread();
        if (closed) return;
        closing = true;
        Throwable failure = null;
        var reverse = new ArrayList<>(worlds).reversed();
        for (var world : reverse) {
            try {
                world.close();
            } catch (RuntimeException | AssertionError | LinkageError error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        // A destroy failure retains the native code arena and the remaining handles. Only
        // close may be retried; never unload executable code still owned by a live world.
        if (worlds.isEmpty()) {
            try {
                access.close();
                release.run();
                closed = true;
            } catch (RuntimeException | AssertionError | LinkageError error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
    }

    private static void deleteCopy(Path library, Path directory) {
        try {
            Files.deleteIfExists(library);
            Files.deleteIfExists(directory);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot remove the closed native physics private copy", failure);
        }
    }
}
