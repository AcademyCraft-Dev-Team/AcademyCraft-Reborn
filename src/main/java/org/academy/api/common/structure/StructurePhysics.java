package org.academy.api.common.structure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.academy.internal.common.structure.NativeStructureRuntime;

import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/** Explicit entry point for the independent backend's bounded host-world physics prototype. */
public final class StructurePhysics {
    public static final double MAX_REGION_AXIS = 24.0;

    private StructurePhysics() {}

    /**
     * Finds an existing ready region containing this exact structure reference in this host.
     * Reads only live ownership; does not initialize a backend/native library, load chunks or
     * structures, resume saved regions, or attach a structure. Paused/closed members are absent.
     * Requires the owning server thread. The returned region is not a reservation or permission.
     */
    public static Optional<StructurePhysicsRegion> activeRegion(ServerLevel host, StructureRef reference) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(reference, "reference");
        return NativeStructureRuntime.existing(host.getServer())
                .flatMap(runtime -> runtime.activePhysicsRegion(host, reference));
    }

    /**
     * Opens a new independent physics region in this live host level, on its owning server thread.
     * Each region axis must have positive finite length of at most 24 blocks. Opening a region
     * does not capture host blocks, attach structures automatically, or provide entity transport.
     *
     * <p>The native library and its verified manifest must be explicitly configured with
     * {@code academy.nativePhysics.library} and {@code academy.nativePhysics.manifest}; the
     * JVM must grant {@code --enable-native-access=ALL-UNNAMED}. Merely referencing this API
     * never loads the library. Missing permission, invalid artifacts or unavailable native code
     * fail explicitly, without another backend or simulated-success fallback.</p>
     *
     * <p>Live and saved regions together reserve at most three distinct regions per server.
     * Two regions in the same host must be separated along at least one axis by one block.
     * Saved attachments reserve their bounds until explicitly resumed or discarded. Overlapping
     * or insufficiently separated regions are rejected, including regions whose chunks are not
     * loaded. Structures that may collide belong in the same region. Regions do not merge,
     * transfer members, or advance one another while paused.</p>
     *
     * @throws IllegalArgumentException if the bounds are invalid or this is not a supported host
     * @throws IllegalStateException if called off-thread, a reservation conflicts, the budget is full, or loading fails
     */
    public static StructurePhysicsRegion openRegion(ServerLevel host, AABB bounds) {
        Objects.requireNonNull(host, "host");
        requireBounds(bounds);
        return NativeStructureRuntime.backend(host.getServer()).openPhysicsRegion(host, bounds);
    }

    /**
     * Reads the sole saved region's bounds without loading native code or chunks.
     * More than one saved region is ambiguous and throws; use {@link #savedRegions} in that case.
     */
    public static Optional<AABB> savedRegionBounds(ServerLevel host) {
        Objects.requireNonNull(host, "host");
        return NativeStructureRuntime.backend(host.getServer()).savedPhysicsRegionBounds(host);
    }

    /**
     * Lists immutable saved region descriptions for this host without opening native code or
     * loading chunks. Regions without any saved members are absent. Do not rely on list order.
     */
    public static List<StructurePhysicsRegionInfo> savedRegions(ServerLevel host) {
        Objects.requireNonNull(host, "host");
        return NativeStructureRuntime.backend(host.getServer()).savedPhysicsRegions(host);
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
     * This convenience overload requires exactly one saved region in this host.
     */
    public static StructurePhysicsRegion resumeRegion(ServerLevel host) {
        Objects.requireNonNull(host, "host");
        return NativeStructureRuntime.backend(host.getServer()).resumePhysicsRegion(host);
    }

    /** Resumes the exact saved region in this host; other regions and their checkpoints are unchanged. */
    public static StructurePhysicsRegion resumeRegion(ServerLevel host, UUID regionId) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(regionId, "regionId");
        return NativeStructureRuntime.backend(host.getServer()).resumePhysicsRegion(host, regionId);
    }

    /**
     * Removes the sole saved region's associations only when that region has no live handle.
     * Stored blocks and poses remain unchanged. Returns false when no association existed.
     * More than one saved region is ambiguous and throws without changing any association.
     */
    public static boolean discardSavedRegion(ServerLevel host) {
        Objects.requireNonNull(host, "host");
        return NativeStructureRuntime.backend(host.getServer()).discardSavedPhysicsRegion(host);
    }

    /**
     * Discards only the selected saved region in this host, preserving stored content and poses.
     * Close its live handle first. Returns false for an unknown ID; it never selects another region.
     */
    public static boolean discardSavedRegion(ServerLevel host, UUID regionId) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(regionId, "regionId");
        return NativeStructureRuntime.backend(host.getServer()).discardSavedPhysicsRegion(host, regionId);
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
