package org.academy.api.common.structure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.StructureCapturePhysics;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.physics.NativePhysicsArtifact;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/** Explicit entry point for the independent backend's bounded host-world physics prototype. */
public final class StructurePhysics {
    public static final double MAX_REGION_AXIS = 24.0;

    private StructurePhysics() {}

    /**
     * Non-mutating readiness check for callers that are about to capture blocks into a body.
     * Empty means the selected backend can open a body with the current deployment. A present
     * value is a stable machine-readable reason and must be reported before any source block is
     * removed; it never falls back to a different physics implementation.
     */
    public static Optional<String> physicalBackendProblem(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        var status = StructureBackendRouter.status(server);
        return switch (status.mode()) {
            case SABLE -> Optional.empty();
            case NATIVE -> {
                try {
                    NativePhysicsArtifact.configured();
                    yield Optional.empty();
                } catch (IOException | RuntimeException unavailable) {
                    yield Optional.of("native_physics_unavailable:" + unavailable.getMessage());
                }
            }
            default -> Optional.of(status.mode().name().toLowerCase(Locale.ROOT) + ":" + status.diagnostic());
        };
    }

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
     * Returns the live region that already contains this exact structure, even while that region
     * is PAUSED during geometry rebuild. Callers that captured a body before source removal use
     * this to adopt the prepared region instead of opening a conflicting second one.
     */
    public static Optional<StructurePhysicsRegion> attachedRegion(ServerLevel host, StructureRef reference) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(reference, "reference");
        return NativeStructureRuntime.existing(host.getServer())
                .flatMap(runtime -> runtime.attachedPhysicsRegion(host, reference));
    }

    /**
     * Attaches a visible legacy structure to physics without recapturing its content or changing
     * its identity. Callers must authorize the actor and target before invoking this method.
     * The existing region is reused; a new attachment becomes ready on a later server tick.
     * Ordinary geometry, region-budget and reservation checks remain in force.
     */
    public static StructurePhysicsRegion adoptExistingRegion(ServerLevel host, StructureRef reference) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(reference, "reference");
        if (!NativeStructureRuntime.ID.equals(reference.backend())
                || !host.dimension().identifier().equals(reference.dimension())) {
            throw new IllegalArgumentException("Only a legacy native structure in this host can be adopted");
        }
        var existing = attachedRegion(host, reference).orElse(null);
        if (existing != null) return existing;
        var runtime = NativeStructureRuntime.backend(host.getServer());
        var handle = runtime.resolve(reference).orElseThrow(
                () -> new IllegalStateException("The retained structure is not active"));
        var occupied = publishedBounds(host, handle);
        if (occupied == null) throw new IllegalStateException("The retained structure has no published collision geometry");
        var candidates = new java.util.LinkedHashMap<UUID, StructurePhysicsRegion>();
        for (var candidate : runtime.loadedReferences()) {
            // A resumed cohort can still be PAUSED while rebuilding its bodies. It already
            // owns the reservation and must not be mistaken for an absent region.
            attachedRegion(host, candidate).ifPresent(region -> candidates.putIfAbsent(region.id(), region));
        }
        for (var shared : candidates.values()) {
            if (!contains(shared.bounds(), occupied)) continue;
            if (shared.hasActiveControl()) {
                throw new IllegalStateException("The containing physics region has active control");
            }
            requireAdoptableCohort(host, runtime, shared);
            org.academy.AcademyCraft.LOGGER.info("LEGACY_STRUCTURE_ADOPTION_CONTAINED structure={} region={}"
                            + " status={} diagnostic={} occupied={} bounds={}",
                    reference, shared.id(), shared.status(), shared.diagnostic(), occupied, shared.bounds());
            shared.attach(handle);
            return shared;
        }
        var corners = List.of(BlockPos.containing(occupied.minX, occupied.minY, occupied.minZ),
                BlockPos.containing(occupied.maxX - 1.0E-6, occupied.maxY - 1.0E-6, occupied.maxZ - 1.0E-6));
        var bounds = StructureCapturePhysics.regionBounds(host, corners);
        if (bounds == null) throw new IllegalStateException("The retained structure does not fit a physics region");
        for (var shared : candidates.values()) {
            if (!shared.bounds().inflate(1.0).intersects(bounds)) continue;
            if (shared.hasActiveControl()) {
                throw new IllegalStateException("The overlapping physics region has active control");
            }
            var cohort = requireAdoptableCohort(host, runtime, shared);
            var required = cohort == null ? occupied : occupied.minmax(cohort);
            var previous = shared.bounds();
            var rebased = adoptionBounds(previous, required);
            org.academy.AcademyCraft.LOGGER.info("LEGACY_STRUCTURE_ADOPTION_CANDIDATE structure={} region={}"
                            + " status={} diagnostic={} occupied={} bounds={} proposed={}",
                    reference, shared.id(), shared.status(), shared.diagnostic(), occupied, previous, rebased);
            if (rebased == null || !shared.rebind(rebased)) continue;
            try {
                shared.attach(handle);
                return shared;
            } catch (RuntimeException failure) {
                // A failed attachment must not silently leave unrelated members rebased.
                if (!shared.rebind(previous)) failure.addSuppressed(
                        new IllegalStateException("Could not restore the previous physics region bounds"));
                throw failure;
            }
        }
        var region = openRegion(host, bounds);
        try {
            region.attach(handle);
            return region;
        } catch (RuntimeException failure) {
            try { region.close(); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static AABB publishedBounds(ServerLevel host, StructureHandle handle) {
        var origin = handle.pose().worldOrigin();
        AABB occupied = null;
        for (var projection : host.getEntitiesOfClass(NativeStructureProjectionEntity.class,
                new AABB(origin, origin).inflate(32))) {
            if (projection.isRemoved() || !projection.viewState().reference().equals(handle.reference())) continue;
            for (var box : projection.worldCollisionBoxes()) occupied = occupied == null ? box : occupied.minmax(box);
        }
        return occupied;
    }

    private static AABB requireAdoptableCohort(ServerLevel host, NativeStructureRuntime runtime,
                                               StructurePhysicsRegion region) {
        if (region.status() != StructurePhysicsRegion.Status.READY
                && !List.of("awaiting_saved_geometry", "awaiting_geometry", "awaiting_rebound_geometry")
                        .contains(region.diagnostic())) {
            throw new IllegalStateException("The overlapping physics region cannot currently resume: " + region.diagnostic());
        }
        AABB required = null;
        for (var member : runtime.savedPhysicsEntries(host, region.id())) {
            var handle = runtime.resolve(member.reference()).orElse(null);
            if (!member.active() || member.quarantined() || !member.physicsAttachment().resumeAllowed()
                    || handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
                throw new IllegalStateException("The overlapping physics region has an unavailable saved member");
            }
            var occupied = publishedBounds(host, handle);
            if (occupied == null) throw new IllegalStateException("The overlapping physics region has unpublished geometry");
            required = required == null ? occupied : required.minmax(occupied);
        }
        return required;
    }

    /** Reuses existing slack, moving bounds only as far as all current bodies require. */
    static AABB adoptionBounds(AABB previous, AABB required) {
        if (required.getXsize() > MAX_REGION_AXIS || required.getYsize() > MAX_REGION_AXIS
                || required.getZsize() > MAX_REGION_AXIS) return null;
        double x = Math.max(previous.getXsize(), required.getXsize());
        double y = Math.max(previous.getYsize(), required.getYsize());
        double z = Math.max(previous.getZsize(), required.getZsize());
        double minX = Math.clamp(previous.minX, required.maxX - x, required.minX);
        double minY = Math.clamp(previous.minY, required.maxY - y, required.minY);
        double minZ = Math.clamp(previous.minZ, required.maxZ - z, required.minZ);
        return new AABB(minX, minY, minZ, minX + x, minY + y, minZ + z);
    }

    private static boolean contains(AABB outer, AABB inner) {
        return outer.minX <= inner.minX && outer.minY <= inner.minY && outer.minZ <= inner.minZ
                && outer.maxX >= inner.maxX && outer.maxY >= inner.maxY && outer.maxZ >= inner.maxZ;
    }

    /**
     * Opens a new independent physics region in this live host level, on its owning server thread.
     * Each region axis must have positive finite length of at most 24 blocks. Opening a region
     * does not capture host blocks, attach structures automatically, or provide entity transport.
     *
     * <p>The solver resolves an explicit {@code academy.nativePhysics.library} / manifest pair,
     * a game-directory deployment, or the project's signed in-JAR artifact. JDK 25 permits the
     * FFM calls with a warning; set {@code -Dacademy.nativePhysics.requireNativeAccess=true} to
     * enforce {@code --enable-native-access=ALL-UNNAMED}. Missing or invalid artifacts fail
     * explicitly, without another backend or simulated-success fallback.</p>
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
     * Failed checkpoints cannot be resumed; use {@link #recoverFailedRegion} to rebuild them
     * at rest after validation. No offline time is simulated.
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
     * Rebuilds every member of a failed legacy region at its last published pose with zero
     * velocity and fresh contacts. Stored blocks, structure IDs and slots remain unchanged.
     * Requires the owning server thread and normal activation of every saved member.
     * Validation and durable checkpoint replacement finish before the new region is published;
     * a rejected attempt retains the old checkpoint. Callers must authorize the operation.
     */
    public static StructurePhysicsRecovery recoverFailedRegion(ServerLevel host, UUID regionId) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(regionId, "regionId");
        var result = NativeStructureRuntime.backend(host.getServer()).recoverPhysicsRegion(host, regionId);
        var status = switch (result.status()) {
            case RECOVERED -> StructurePhysicsRecovery.Status.RECOVERED;
            case WAITING -> StructurePhysicsRecovery.Status.WAITING;
            case REJECTED -> StructurePhysicsRecovery.Status.REJECTED;
        };
        return new StructurePhysicsRecovery(status, result.region(), result.diagnostic());
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
