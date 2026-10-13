package org.academy.api.common.structure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.academy.internal.common.structure.NativeStructureRuntime;

import java.util.Objects;
import java.util.Optional;

/** Explicit entry point for the independent backend's bounded host-world physics prototype. */
public final class StructurePhysics {
    public static final double MAX_REGION_AXIS = 24.0;

    private StructurePhysics() {}

    /**
     * Opens at most one physics region in this live host level, on its owning server thread.
     * Each region axis must have positive finite length of at most 24 blocks. Opening a region
     * does not capture host blocks, attach structures automatically, or provide entity transport.
     *
     * <p>The native library and its verified manifest must be explicitly configured with
     * {@code academy.nativePhysics.library} and {@code academy.nativePhysics.manifest}; the
     * JVM must grant {@code --enable-native-access=ALL-UNNAMED}. Merely referencing this API
     * never loads the library. Missing permission, invalid artifacts or unavailable native code
     * fail explicitly, without another backend or simulated-success fallback.</p>
     *
     * <p>Saved attachments reserve their region until explicitly resumed or discarded. This
     * method refuses that pending state instead of replacing saved velocity with a fresh body.</p>
     *
     * @throws IllegalArgumentException if the bounds are invalid or this is not a supported host
     * @throws IllegalStateException if called off-thread, another region exists, or loading fails
     */
    public static StructurePhysicsRegion openRegion(ServerLevel host, AABB bounds) {
        Objects.requireNonNull(host, "host");
        requireBounds(bounds);
        return NativeStructureRuntime.backend(host.getServer()).openPhysicsRegion(host, bounds);
    }

    /** Reads saved region bounds without loading the native library or activating structure chunks. */
    public static Optional<AABB> savedRegionBounds(ServerLevel host) {
        Objects.requireNonNull(host, "host");
        return NativeStructureRuntime.backend(host.getServer()).savedPhysicsRegionBounds(host);
    }

    /**
     * Explicitly opens the saved region using the configured native library. Active structures
     * wait for their ordinary activation to finish; inactive structures are never implicitly loaded.
     * Loading one later joins it to this region. Missing host chunks pause the region without
     * creating chunk tickets. Saved nonempty geometry must match its collision digest exactly;
     * a mismatch pauses the complete region and retains the checkpoint until geometry matches
     * again or the saved association is explicitly discarded.
     * Saved sleeping bodies resume awake so the fresh solver can establish current contacts.
     * Failed checkpoints require discard and cannot be resumed. No offline time is simulated.
     */
    public static StructurePhysicsRegion resumeRegion(ServerLevel host) {
        Objects.requireNonNull(host, "host");
        return NativeStructureRuntime.backend(host.getServer()).resumePhysicsRegion(host);
    }

    /**
     * Removes saved physics associations only when no live region exists. Stored blocks and poses
     * remain unchanged. Returns false when no association existed. Close a live region first.
     */
    public static boolean discardSavedRegion(ServerLevel host) {
        Objects.requireNonNull(host, "host");
        return NativeStructureRuntime.backend(host.getServer()).discardSavedPhysicsRegion(host);
    }

    static void requireBounds(AABB bounds) {
        Objects.requireNonNull(bounds, "bounds");
        if (!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.minZ)
                || !Double.isFinite(bounds.maxX) || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)
                || !validLength(bounds.maxX - bounds.minX) || !validLength(bounds.maxY - bounds.minY)
                || !validLength(bounds.maxZ - bounds.minZ)) {
            throw new IllegalArgumentException("Physics region axes must be finite, positive and at most 24 blocks");
        }
    }

    private static boolean validLength(double length) {
        return Double.isFinite(length) && length > 0 && length <= MAX_REGION_AXIS;
    }
}
