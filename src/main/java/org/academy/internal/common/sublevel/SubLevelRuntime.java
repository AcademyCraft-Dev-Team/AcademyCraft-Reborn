package org.academy.internal.common.sublevel;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSimulationBudget;
import org.academy.internal.common.structure.physics.PhysicsBox;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.upgrade.control.LiveStructureOrientation;
import org.academy.internal.common.structure.upgrade.control.LiveStructureExtraction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Phase 1 prototype runtime tying a shared plot lease to one body in the shared universe. */
public final class SubLevelRuntime {
    private final ServerLevel host;
    private final PlotStorage storage;
    private final SubLevelPhysicsUniverse universe;
    private final SubLevelManifest manifest;
    private final TerrainColliderCache terrain;
    private static final StructureSimulationBudget.Limits BUDGET_LIMITS = new StructureSimulationBudget.Limits(
            Math.max(1_000_000L, Long.getLong("academy.sublevel.targetTickNanos", 6_000_000L)),
            Math.max(1, Integer.getInteger("academy.sublevel.terrainSections", 64)),
            Math.max(1, Integer.getInteger("academy.sublevel.geometryCells", 32_768)),
            Math.max(1, Integer.getInteger("academy.sublevel.contactPairs", 4_096)),
            Math.max(1, Integer.getInteger("academy.sublevel.snapshotBytes", 1_048_576)));
    private final SubLevelCollisionIndex collision = new SubLevelCollisionIndex();
    private final Map<SubLevelId, Managed> subLevels = new LinkedHashMap<>();
    private final Map<SubLevelId, SubLevelState> restorationIsolation = new LinkedHashMap<>();
    private final LinkedHashSet<SubLevelId> emptyMarkedCleanup = new LinkedHashSet<>();
    private final SubLevelSimulationBudget budget = SubLevelEnhancementBootstrap.SIMULATION;
    private boolean terrainNeedsUpdate = true;
    private boolean terrainBlocked;
    private long nextTerrainWarning;
    private List<AABB> sampledTerrainBounds = List.of();

    public SubLevelRuntime(ServerLevel host, PlotStorage storage, SubLevelPhysicsUniverse universe) {
        this(host, storage, universe, null, null);
    }

    public SubLevelRuntime(ServerLevel host, PlotStorage storage, SubLevelPhysicsUniverse universe,
                           SubLevelManifest manifest) {
        this(host, storage, universe, manifest, null);
    }

    public SubLevelRuntime(ServerLevel host, PlotStorage storage, SubLevelPhysicsUniverse universe,
                           SubLevelManifest manifest, TerrainColliderCache terrain) {
        this.host = Objects.requireNonNull(host, "host");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.universe = Objects.requireNonNull(universe, "universe");
        this.manifest = manifest;
        this.terrain = terrain;
        if (host.getServer() != storage.level().getServer()) {
            throw new IllegalArgumentException("Host and plot storage belong to different servers");
        }
        if (host == storage.level()) {
            throw new IllegalArgumentException("A SubLevel host cannot be the plot dimension");
        }
        SubLevelCollisionRuntime.register(host, collision);
    }

    public SubLevelCollisionIndex collisionIndex() {
        return collision;
    }

    public ServerLevel host() {
        return host;
    }

    public PlotStorage storage() {
        return storage;
    }

    public SubLevelPhysicsUniverse universe() {
        return universe;
    }

    /** Durable empty allocation for the shared capture transaction; not publicly controllable. */
    public Optional<SubLevel> createStaged(SubLevelId id, AABB localBounds, StructurePose pose) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(localBounds, "localBounds");
        Objects.requireNonNull(pose, "pose");
        if (subLevels.containsKey(id) || SubLevelServer.runtime(host.getServer(), id).isPresent()) {
            throw new IllegalArgumentException("Sub-level already exists: " + id);
        }
        long allocateStarted = org.academy.internal.common.structure.transaction.CaptureTimings.begin();
        var lease = storage.allocate().orElse(null);
        org.academy.internal.common.structure.transaction.CaptureTimings.record(id.value().toString(),
                "plot_allocate", allocateStarted);
        if (lease == null) return Optional.empty();
        var subLevel = new SubLevel(id, lease.plot(), localBounds, pose);
        var managed = new Managed(subLevel, lease, List.of(),
                org.academy.internal.common.structure.space.NativeStructureView.EMPTY);
        subLevels.put(id, managed);
        SubLevelActivationRegistry.activation(id);
        remember(subLevel);
        try {
            long persistStarted = org.academy.internal.common.structure.transaction.CaptureTimings.begin();
            persist();
            org.academy.internal.common.structure.transaction.CaptureTimings.record(id.value().toString(),
                    "staging_manifest_persist", persistStarted);
            return Optional.of(subLevel);
        } catch (RuntimeException | Error failure) {
            forget(id);
            try {
                persist();
                subLevels.remove(id);
                storage.release(lease);
            } catch (RuntimeException | Error cleanup) {
                // An uncertain durable row must keep ownership; no other capture can reuse it.
                remember(subLevel);
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /** Finalizes a copied staging plot without changing the transaction's activation identity. */
    public boolean finalizeStaged(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        if (managed == null || restorationIsolation.containsKey(id)) return false;
        if (managed.subLevel.state() == SubLevelState.ACTIVE || managed.subLevel.state() == SubLevelState.SLEEPING) {
            flushStorage();
            persist();
            return true;
        }
        if (managed.subLevel.state() != SubLevelState.ALLOCATED && managed.subLevel.state() != SubLevelState.LOADING) return false;
        long geometryStarted = org.academy.internal.common.structure.transaction.CaptureTimings.begin();
        var geometry = PlotGeometry.capture(storage, managed.subLevel.plot(), managed.subLevel.localBounds());
        org.academy.internal.common.structure.transaction.CaptureTimings.record(id.value().toString(),
                "finalize_geometry_capture", geometryStarted);
        long bodyStarted = org.academy.internal.common.structure.transaction.CaptureTimings.begin();
        boolean bodyReady = !geometry.boxes().isEmpty() && addBody(id, physicsPose(managed.subLevel.pose()), geometry.boxes());
        org.academy.internal.common.structure.transaction.CaptureTimings.record(id.value().toString(),
                "finalize_add_body", bodyStarted);
        if (!bodyReady) return false;
        var boxes = localBoxes(geometry.boxes());
        long viewStarted = org.academy.internal.common.structure.transaction.CaptureTimings.begin();
        var view = PlotView.capture(storage, managed.subLevel.plot(), managed.subLevel.localBounds());
        org.academy.internal.common.structure.transaction.CaptureTimings.record(id.value().toString(),
                "finalize_view_capture", viewStarted);
        managed.subLevel.setState(SubLevelState.ACTIVE);
        managed.subLevel.geometryChanged();
        subLevels.put(id, new Managed(managed.subLevel, managed.lease, boxes, view));
        collision.update(id, boxes, managed.subLevel.pose(), managed.subLevel.geometryRevision(),
                managed.subLevel.revision());
        budget.invalidate(host, budgetReference(managed.subLevel), StructureSimulationBudget.DirtyPart.CONTENT);
        terrainNeedsUpdate = true;
        remember(managed.subLevel);
        long flushStarted = org.academy.internal.common.structure.transaction.CaptureTimings.begin();
        if (!flushPlotChunks(id)) {
            throw new IllegalStateException("SubLevel plot chunk save failed: " + id);
        }
        org.academy.internal.common.structure.transaction.CaptureTimings.record(id.value().toString(),
                "finalize_flush_storage", flushStarted);
        long persistStarted = org.academy.internal.common.structure.transaction.CaptureTimings.begin();
        persist();
        org.academy.internal.common.structure.transaction.CaptureTimings.record(id.value().toString(),
                "finalize_manifest_persist", persistStarted);
        return true;
    }

    public void flushStorage() {
        storage.level().save(null, true, false);
        if (org.academy.internal.common.structure.space.NativeStructureStorageErrors.failed(storage.level())) {
            throw new IllegalStateException("SubLevel plot save failed");
        }
    }

    /** Saves only this plot's chunks; capture durability does not require every other plot. */
    public boolean flushPlotChunks(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        if (managed == null) return false;
        return TargetedChunkDurability.save(storage.level(), managed.lease.chunks());
    }

    /** Isolates an uncommitted copy while retaining its plot and transaction activation. */
    public boolean suspendCapture(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        if (managed == null) return false;
        try {
            universe.remove(id);
        } finally {
            collision.remove(id);
            SubLevelMotionController.cancel(id);
            managed.subLevel.setState(SubLevelState.ALLOCATED);
            subLevels.put(id, new Managed(managed.subLevel, managed.lease, List.of(),
                    org.academy.internal.common.structure.space.NativeStructureView.EMPTY));
            remember(managed.subLevel);
            // No body is observable now, so any former public controller loses its lease.
            org.academy.api.common.structure.StructureDirectControl.tick(host.getServer());
            try {
                persist();
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.error("Could not persist suspended SubLevel {}; retaining plot and journal", id, failure);
            }
        }
        return true;
    }

    public Optional<SubLevel> create(SubLevelId id, AABB localBounds, PhysicsPose bodyPose, List<PhysicsBox> boxes) {
        Objects.requireNonNull(id, "id");
        if (subLevels.containsKey(id)) throw new IllegalArgumentException("Sub-level already exists: " + id);
        var lease = storage.allocate().orElse(null);
        if (lease == null) return Optional.empty();
        try {
            return Optional.of(createInLease(id, lease, localBounds, bodyPose, boxes));
        } catch (RuntimeException | Error failure) {
            storage.release(lease);
            throw failure;
        }
    }

    /** Creates a SubLevel in an already allocated plot lease; the caller owns cleanup on failure. */
    public SubLevel createInLease(SubLevelId id, PlotStorage.Lease lease, AABB localBounds,
                                  PhysicsPose bodyPose, List<PhysicsBox> boxes) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lease, "lease");
        if (subLevels.containsKey(id)) throw new IllegalArgumentException("Sub-level already exists: " + id);
        if (SubLevelServer.runtimes(host.getServer()).stream().flatMap(runtime -> runtime.subLevels().stream())
                .anyMatch(existing -> existing.id().equals(id) || existing.plot().equals(lease.plot()))) {
            throw new IllegalArgumentException("SubLevel identity or plot already belongs to another structure");
        }
        if (storage.lease(lease.plot()).filter(current -> current.equals(lease)).isEmpty()) {
            throw new IllegalArgumentException("Plot lease is not owned by this runtime");
        }
        var subLevel = new SubLevel(id, lease.plot(), localBounds, structurePose(bodyPose));
        var localBoxes = localBoxes(boxes);
        var view = PlotView.capture(storage, lease.plot(), localBounds);
        boolean active = addBody(id, bodyPose, boxes);
        subLevel.setState(active ? SubLevelState.ACTIVE : SubLevelState.INACTIVE);
        subLevels.put(id, new Managed(subLevel, lease, localBoxes, view));
        SubLevelActivationRegistry.activation(id);
        collision.update(id, localBoxes, subLevel.pose(), subLevel.geometryRevision(), subLevel.revision());
        budget.invalidate(host, budgetReference(subLevel), StructureSimulationBudget.DirtyPart.CONTENT);
        terrainNeedsUpdate = true;
        remember(subLevel);
        return subLevel;
    }

    /** Rebuilds body and collision after a plot block changes. */
    public void refreshGeometry(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        if (managed == null || restorationIsolation.containsKey(id)) return;
        emptyMarkedCleanup.remove(id);
        var previousBounds = managed.subLevel.localBounds();
        var contentBounds = SubLevelGrowth.contentBounds(storage, managed.subLevel.plot(), previousBounds);
        managed.subLevel.setLocalBounds(contentBounds);
        var geometry = PlotGeometry.capture(storage, managed.subLevel.plot(), managed.subLevel.localBounds());
        var motion = universe.motion(id).orElse(null);
        universe.remove(id);
        managed.subLevel.geometryChanged();
        var reference = budgetReference(managed.subLevel);
        budget.invalidate(host, reference, StructureSimulationBudget.DirtyPart.CONTENT);
        budget.invalidate(host, reference, StructureSimulationBudget.DirtyPart.COLLISION);
        terrainNeedsUpdate = true;
        if (geometry.boxes().isEmpty()) {
            collision.remove(id);
            SubLevelMotionController.cancel(id);
            SubLevelTerrainBreaker.forget(id);
            var remainingView = PlotView.capture(storage, managed.subLevel.plot(), managed.subLevel.localBounds());
            if (remainingView.cells().isEmpty() && retireEmptyMarked(managed)) return;
            if (remainingView.cells().isEmpty()) emptyMarkedCleanup.add(id);
            // Protection may retain decoration after the last collision cell is mined. It
            // remains selectable without manufacturing a zero-mass rigid body.
            managed.subLevel.setState(emptyBodyState(remainingView.cells().size()));
            subLevels.put(id, new Managed(managed.subLevel, managed.lease, List.of(),
                    remainingView));
            remember(managed.subLevel);
            if (!previousBounds.equals(contentBounds) && TargetedChunkDurability.save(storage.level(), managed.lease.chunks())) persist();
            return;
        }
        var localBoxes = localBoxes(geometry.boxes());
        var view = PlotView.capture(storage, managed.subLevel.plot(), managed.subLevel.localBounds());
        boolean active = addBody(id, physicsPose(managed.subLevel.pose()), geometry.boxes());
        if (active && motion != null) universe.setVelocity(id, motion.linearVelocity(), motion.angularVelocity());
        managed.subLevel.setState(active ? SubLevelState.ACTIVE : SubLevelState.INACTIVE);
        collision.update(id, localBoxes, managed.subLevel.pose(), managed.subLevel.geometryRevision(),
                managed.subLevel.revision());
        subLevels.put(id, new Managed(managed.subLevel, managed.lease, localBoxes, view));
        remember(managed.subLevel);
        if (!previousBounds.equals(contentBounds) && TargetedChunkDurability.save(storage.level(), managed.lease.chunks())) persist();
    }

    /** Reserve and persist the editable envelope before vanilla can write or consume an item. */
    public boolean prepareEdit(SubLevelId id, net.minecraft.core.BlockPos target, int padding, int additions) {
        if (!host.getServer().isSameThread()) throw new IllegalStateException("SubLevel edits require the server thread");
        if (padding < 0 || padding > 1 || additions < 0 || additions > 2) return false;
        var managed = subLevels.get(id);
        if (managed == null || !SubLevelServer.published(managed.subLevel) || restorationIsolation.containsKey(id)
                || org.academy.internal.common.structure.transaction.StructureCaptureCoordinator.isPending(host.getServer(), budgetReference(managed.subLevel))
                || target.getY() < storage.level().getMinY() || target.getY() >= storage.level().getMaxY()) return false;
        var bounds = SubLevelGrowth.include(managed.subLevel.localBounds(), target, padding, storage.level().getMinY(), storage.level().getMaxY());
        if (!SubLevelGrowth.valid(bounds, storage.level().getMinY(), storage.level().getMaxY())) return false;
        int count = 0;
        int limit = org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance.metadata(host.getServer(), budgetReference(managed.subLevel)).isPresent()
                ? org.academy.api.common.structure.StructureCaptureRequest.MAXIMUM_BLOCKS
                : org.academy.internal.common.structure.space.NativeStructureView.MAX_CELLS;
        var current = managed.subLevel.localBounds();
        for (var pos : net.minecraft.core.BlockPos.betweenClosed((int) current.minX, (int) current.minY, (int) current.minZ,
                (int) current.maxX - 1, (int) current.maxY - 1, (int) current.maxZ - 1)) {
            if (!storage.blockState(managed.subLevel.plot(), pos).isAir()) count++;
        }
        if (!SubLevelGrowth.blockBudgetAllows(count, additions, !storage.blockState(managed.subLevel.plot(), target).isAir(), limit)) return false;
        if (!PlotLayout.spacious(managed.subLevel.plot()) && !relocateLegacy(managed, bounds)) return false;
        managed = subLevels.get(id);
        if (!storage.ensureBounds(managed.subLevel.plot(), bounds)) return false;
        var before = managed.subLevel.localBounds();
        if (before.equals(bounds)) return true;
        managed.subLevel.setLocalBounds(bounds);
        remember(managed.subLevel);
        try { persist(); return true; }
        catch (RuntimeException failure) {
            managed.subLevel.setLocalBounds(before); remember(managed.subLevel);
            AcademyCraft.LOGGER.error("SubLevel edit envelope could not be persisted for {}", id, failure);
            return false;
        }
    }

    public enum MigrationPhase { COPY_SAVED, OWNER_SAVED }
    public record MigrationCheckpoint(SubLevelId id, PlotPos source, PlotPos destination, MigrationPhase phase) {}
    private java.util.function.Consumer<MigrationCheckpoint> migrationObserver;
    public void observeMigration(java.util.function.Consumer<MigrationCheckpoint> observer) { migrationObserver = observer; }

    /** The atomic manifest is the sole owner: no block or local coordinate changes during migration. */
    private boolean relocateLegacy(Managed managed, AABB requested) {
        if (manifest == null || SubLevelEnhancementBootstrap.INTERACTIONS.restorationBusy(host, budgetReference(managed.subLevel))) return false;
        // The compatibility USE path can open a menu without a controller tracking record.
        if (host.players().stream().anyMatch(player -> player.containerMenu != player.inventoryMenu
                && player.containerMenu.slots.stream().anyMatch(slot -> slot.container instanceof SubLevelContainer container
                && container.subLevelId().equals(managed.subLevel.id())))) return false;
        var source = managed.subLevel.plot();
        if (storage.loadedEntities(source).filter(java.util.List::isEmpty).isEmpty()) return false;
        var destination = storage.allocateMigration(source).orElse(null);
        if (destination == null) return false;
        var sourceLock = java.util.UUID.randomUUID();
        var targetLock = java.util.UUID.randomUUID();
        var images = new java.util.LinkedHashMap<net.minecraft.core.BlockPos, org.academy.api.common.structure.StructureBlockData>();
        boolean committed = false;
        boolean deciding = false;
        try {
            if (!storage.ensureBounds(destination.plot(), requested)) return false;
            var bounds = managed.subLevel.localBounds();
            for (var pos : net.minecraft.core.BlockPos.betweenClosed((int) bounds.minX, (int) bounds.minY, (int) bounds.minZ,
                    (int) bounds.maxX - 1, (int) bounds.maxY - 1, (int) bounds.maxZ - 1)) {
                if (storage.blockState(source, pos).isAir()) continue;
                images.put(pos.immutable(), org.academy.internal.common.structure.transaction.StructureCellPayload.read(storage.level(), storage.worldPosition(source, pos)));
            }
            org.academy.internal.common.structure.transaction.PayloadIsolation.lock(storage.level(), images.keySet().stream()
                    .map(pos -> storage.worldPosition(source, pos)).toList(), sourceLock);
            org.academy.internal.common.structure.transaction.PayloadIsolation.lock(storage.level(), images.keySet().stream()
                    .map(pos -> storage.worldPosition(destination.plot(), pos)).toList(), targetLock);
            if (!TargetedChunkDurability.save(storage.level(), managed.lease.chunks())) return false;
            for (var entry : images.entrySet()) org.academy.internal.common.structure.transaction.StructureCellPayload.write(storage.level(),
                    storage.worldPosition(destination.plot(), entry.getKey()), entry.getValue(), SubLevelCaptureService.TRANSACTION_FLAGS);
            if (!TargetedChunkDurability.save(storage.level(), destination.chunks())) return false;
            if (migrationObserver != null) migrationObserver.accept(new MigrationCheckpoint(managed.subLevel.id(), source, destination.plot(), MigrationPhase.COPY_SAVED));
            deciding = true;
            manifest.put(new SubLevelManifest.Entry(managed.subLevel.id(), destination.plot(), requested, managed.subLevel.pose(),
                    managed.subLevel.state(), managed.subLevel.revision()));
            persist();
            committed = true;
            managed.subLevel.relocateStorage(destination.plot());
            managed.subLevel.setLocalBounds(requested);
            subLevels.put(managed.subLevel.id(), new Managed(managed.subLevel, destination, managed.localBoxes, managed.view));
            if (migrationObserver != null) migrationObserver.accept(new MigrationCheckpoint(managed.subLevel.id(), source, destination.plot(), MigrationPhase.OWNER_SAVED));
            for (var pos : images.keySet()) org.academy.internal.common.structure.transaction.StructureCellPayload.write(storage.level(), storage.worldPosition(source, pos),
                    new org.academy.api.common.structure.StructureBlockData(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()), SubLevelCaptureService.TRANSACTION_FLAGS);
            if (!TargetedChunkDurability.save(storage.level(), managed.lease.chunks())) throw new IllegalStateException("Old plot cleanup did not become durable");
            storage.release(managed.lease);
            org.academy.internal.common.structure.transaction.PayloadIsolation.release(host.getServer(), sourceLock);
            return true;
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.error("SubLevel storage migration retained for {} (ownership committed={})", managed.subLevel.id(), committed, failure);
            if (deciding && !committed) {
                // An uncertain atomic replacement must keep both images unavailable until restart.
                universe.remove(managed.subLevel.id()); collision.remove(managed.subLevel.id());
                managed.subLevel.setState(SubLevelState.LOADING);
            }
            return committed;
        } finally {
            if (committed) org.academy.internal.common.structure.transaction.PayloadIsolation.release(host.getServer(), targetLock);
            else if (!deciding) {
                storage.release(destination);
                org.academy.internal.common.structure.transaction.PayloadIsolation.release(host.getServer(), targetLock);
                org.academy.internal.common.structure.transaction.PayloadIsolation.release(host.getServer(), sourceLock);
            }
        }
    }

    public Optional<SubLevel> get(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        return managed == null ? Optional.empty() : Optional.of(managed.subLevel);
    }

    /** Missing activation does not imply that the durable source identity has been retired. */
    public boolean hasPersisted(SubLevelId id) {
        return manifest != null && manifest.get(Objects.requireNonNull(id, "id")).isPresent();
    }

    /** Recovery owns the plot until its transaction ends; hide only this source from gameplay. */
    public boolean isolateRestoration(SubLevelId id) {
        var managed = subLevels.get(id);
        if (managed == null) return false;
        if (restorationIsolation.containsKey(id)) return true;
        restorationIsolation.put(id, managed.subLevel.state());
        universe.remove(id);
        collision.remove(id);
        SubLevelMotionController.cancel(id);
        managed.subLevel.setState(SubLevelState.LOADING);
        terrainNeedsUpdate = true;
        return true;
    }

    static SubLevelState emptyBodyState(int renderedCells) {
        return renderedCells > 0 ? SubLevelState.SLEEPING : SubLevelState.INACTIVE;
    }

    public boolean remove(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        if (managed == null) return false;
        universe.remove(id);
        collision.remove(id);
        budget.forget(host, budgetReference(managed.subLevel));
        SubLevelTerrainBreaker.forget(id);
        SubLevelActivationRegistry.find(id).ifPresent(activation ->
                SubLevelEnhancementBootstrap.INTERACTIONS.cancelStructure(host.getServer(),
                        SubLevelStructureRefs.of(managed.subLevel, host.dimension().identifier(),
                                activation.generation()), activation.runtimeId(),
                        org.academy.api.common.structure.StructureInteractionController.CancelReason
                                .STRUCTURE_UNAVAILABLE, SubLevelServer.MINING_FEEDBACK));
        managed.subLevel.setState(SubLevelState.INACTIVE);
        SubLevelActivationRegistry.invalidate(id);
        SubLevelMotionController.cancel(id);
        forget(id);
        try {
            // The durable owner must disappear before the shared allocator can hand this plot
            // to another host. Failure keeps the content and its lease recoverable in memory.
            persist();
        } catch (RuntimeException | Error failure) {
            remember(managed.subLevel);
            throw failure;
        }
        subLevels.remove(id);
        restorationIsolation.remove(id);
        emptyMarkedCleanup.remove(id);
        storage.release(managed.lease);
        managed.subLevel.setState(SubLevelState.REMOVED);
        retireCreationMark(managed.subLevel);
        return true;
    }

    private boolean retireEmptyMarked(Managed managed) {
        var reference = budgetReference(managed.subLevel);
        var server = host.getServer();
        if (org.academy.internal.common.structure.transaction.StructureCaptureCoordinator.isPending(server, reference)
                || org.academy.internal.common.structure.upgrade.lifecycle.MarkedGridRestoration.pending(server, reference)
                || org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance.metadata(server, reference).isEmpty()) return false;
        var entities = storage.loadedEntities(managed.subLevel.plot());
        if (entities.isEmpty() || !entities.get().isEmpty()) return false;
        // remove persists manifest retirement before releasing the lease. No entity/chunk load is requested.
        return remove(managed.subLevel.id());
    }

    private void retireCreationMark(SubLevel subLevel) {
        var reference = new StructureRef(subLevel.id().value(), host.dimension().identifier(), SubLevelStructureRefs.BACKEND, 0);
        var server = host.getServer();
        // A recovery transaction owns mark retirement until its durable transfer has completed.
        if (org.academy.internal.common.structure.transaction.StructureCaptureCoordinator.isPending(server, reference)
                || org.academy.internal.common.structure.upgrade.lifecycle.MarkedGridRestoration.pending(server, reference)) return;
        org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance.metadata(server, reference)
                .ifPresent(metadata -> org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance.remove(
                        server, reference, metadata.creationOperationId()));
    }

    private void retryEmptyMarkedCleanup() {
        if (emptyMarkedCleanup.isEmpty() || host.getGameTime() % 20 != 0) return;
        var iterator = emptyMarkedCleanup.iterator();
        var id = iterator.next();
        iterator.remove();
        var managed = subLevels.get(id);
        if (managed == null || !managed.view.cells().isEmpty()) return;
        if (org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance
                .metadata(host.getServer(), budgetReference(managed.subLevel)).isEmpty()) return;
        if (!retireEmptyMarked(managed)) emptyMarkedCleanup.add(id);
    }

    /** Relative world-yaw rotation, preserving the center of mass and active body. */
    public boolean rotate(SubLevelId id, double yawDegrees) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        if (managed == null || !Double.isFinite(yawDegrees)) return false;
        var rotation = new org.joml.Quaterniond().rotateY(Math.toRadians(Math.IEEEremainder(yawDegrees, 360.0)))
                .mul(managed.subLevel.pose().rotation());
        return rotateTo(id, new StructurePose(Vec3.ZERO, rotation));
    }

    /** Applies an absolute orientation about the mass center; target translation is ignored. */
    public boolean rotateTo(SubLevelId id, StructurePose target) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        Objects.requireNonNull(target, "target");
        if (managed == null || restorationIsolation.containsKey(id)) return false;
        var motion = universe.motion(id).orElse(null);
        var localCenter = motion == null ? managed.subLevel.localBounds().getCenter()
                : new Vec3(motion.localCenterOfMass().x(), motion.localCenterOfMass().y(), motion.localCenterOfMass().z());
        var rotated = rotationAround(managed.subLevel.pose(), localCenter, target);
        if (motion != null && !universe.setPose(id, physicsPose(rotated))) return false;
        managed.subLevel.setPose(rotated);
        if (motion != null) managed.subLevel.setState(SubLevelState.ACTIVE);
        collision.update(id, managed.localBoxes, rotated, managed.subLevel.previousPose(),
                managed.subLevel.geometryRevision(), managed.subLevel.revision());
        budget.invalidate(host, budgetReference(managed.subLevel), StructureSimulationBudget.DirtyPart.POSE);
        terrainNeedsUpdate = true;
        remember(managed.subLevel);
        return true;
    }

    static StructurePose rotationAround(StructurePose current, Vec3 localCenter, StructurePose target) {
        var rotation = new StructurePose(Vec3.ZERO, target.rotation());
        return new StructurePose(current.localToWorldPoint(localCenter)
                .subtract(rotation.localToWorldDirection(localCenter)), rotation.rotation());
    }

    /** Sets a bounded launch velocity, replacing any previous movement or rotation. */
    public boolean launch(SubLevelId id, Vec3 direction, double speed) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        var unit = SubLevelMotionController.normalized(direction);
        if (managed == null || restorationIsolation.containsKey(id) || unit == null || !Double.isFinite(speed) || speed <= 0.0) return false;
        if (managed.subLevel.state() == SubLevelState.INACTIVE) {
            var geometry = PlotGeometry.capture(storage, managed.subLevel.plot(), managed.subLevel.localBounds());
            if (geometry.boxes().isEmpty()) return false;
            if (!activate(id, physicsPose(managed.subLevel.pose()), geometry.boxes())) return false;
        }
        var motion = universe.motion(id).orElse(null);
        if (motion == null) return false;
        speed = Math.min(speed, 400.0);
        boolean launched = universe.setVelocity(id, new org.academy.internal.common.structure.physics.PhysicsVector(
                unit.x * speed, unit.y * speed, unit.z * speed),
                org.academy.internal.common.structure.physics.PhysicsVector.ZERO);
        if (launched) SubLevelMotionController.cancel(id);
        return launched;
    }

    /** Translates the existing body without invalidating its identity, velocity or control. */
    public boolean shift(SubLevelId id, Vec3 delta) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        if (managed == null || restorationIsolation.containsKey(id)) return false;
        var pose = managed.subLevel.pose();
        var shifted = new StructurePose(pose.worldOrigin().add(Objects.requireNonNull(delta, "delta")),
                pose.rotationX(), pose.rotationY(), pose.rotationZ(), pose.rotationW());
        var motion = universe.motion(id).orElse(null);
        if (motion != null && !universe.setPose(id, physicsPose(shifted))) return false;
        managed.subLevel.setPose(shifted);
        if (motion != null) managed.subLevel.setState(SubLevelState.ACTIVE);
        collision.update(id, managed.localBoxes, shifted, managed.subLevel.previousPose(),
                managed.subLevel.geometryRevision(), managed.subLevel.revision());
        budget.invalidate(host, budgetReference(managed.subLevel), StructureSimulationBudget.DirtyPart.POSE);
        terrainNeedsUpdate = true;
        remember(managed.subLevel);
        return true;
    }

    /** Drops the body but keeps the plot lease and content for a later reactivation. */
    public boolean deactivate(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        if (managed == null || managed.subLevel.state() == SubLevelState.INACTIVE) return false;
        universe.remove(id);
        SubLevelMotionController.cancel(id);
        managed.subLevel.setState(SubLevelState.INACTIVE);
        remember(managed.subLevel);
        return true;
    }

    public boolean activate(SubLevelId id, PhysicsPose pose, List<PhysicsBox> boxes) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        if (managed == null || restorationIsolation.containsKey(id) || managed.subLevel.state() == SubLevelState.ACTIVE) return false;
        if (!addBody(id, pose, boxes)) return false;
        var localBoxes = localBoxes(boxes);
        var view = PlotView.capture(storage, managed.subLevel.plot(), managed.subLevel.localBounds());
        managed.subLevel.setPose(structurePose(pose));
        managed.subLevel.setState(SubLevelState.ACTIVE);
        SubLevelActivationRegistry.advance(id);
        collision.update(id, localBoxes, managed.subLevel.pose(), managed.subLevel.geometryRevision(),
                managed.subLevel.revision());
        budget.invalidate(host, budgetReference(managed.subLevel), StructureSimulationBudget.DirtyPart.POSE);
        terrainNeedsUpdate = true;
        subLevels.put(id, new Managed(managed.subLevel, managed.lease, localBoxes, view));
        remember(managed.subLevel);
        return true;
    }

    /** Steps the shared universe once and publishes each body pose back to its SubLevel. */
    public void tick(double duration, int substeps) {
        resumeRestorationSources();
        retryEmptyMarkedCleanup();
        if (subLevels.isEmpty()) return;
        if (!Double.isFinite(duration) || duration <= 0.0 || substeps < 1) return;
        long serverTick = host.getGameTime();
        var work = new ArrayList<StructureSimulationBudget.WorkItem>();
        for (var managed : subLevels.values()) {
            var subLevel = managed.subLevel;
            if (subLevel.state() != SubLevelState.ACTIVE && subLevel.state() != SubLevelState.SLEEPING) continue;
            var reference = budgetReference(subLevel);
            work.add(new StructureSimulationBudget.WorkItem(reference, budget.version(host, reference),
                    subLevel.state() == SubLevelState.SLEEPING));
        }
        StructureSimulationBudget.FramePlan plan = null;
        if (!work.isEmpty()) {
            plan = budget.plan(host, serverTick, work, BUDGET_LIMITS);
        }
        // Commands have already published their velocities. Remove only authorized soft
        // contacts before rebuilding terrain, otherwise leaves stop the body before a sweep.
        for (var managed : subLevels.values()) {
            var subLevel = managed.subLevel;
            if (subLevel.state() != SubLevelState.ACTIVE && subLevel.state() != SubLevelState.SLEEPING) continue;
            if (universe.isSimulationPaused(subLevel.id())) continue;
            SubLevelTerrainBreaker.beforeStep(host, subLevel, universe.motion(subLevel.id()).orElse(null),
                    managed.localBoxes, duration);
        }
        long terrainStart = System.nanoTime();
        boolean terrainUpdated = false;
        var terrainRequests = terrainRequests(duration);
        if (terrain != null) {
            var terrainBounds = List.copyOf(terrainRequests.values());
            // A velocity/rotation command can wake a previously sleeping body before its pose
            // changes. Its next sweep must already be covered on that first moving tick.
            if (terrainNeedsUpdate || !terrainBounds.equals(sampledTerrainBounds) || terrain.needsUpdate()) {
                terrain.update(terrainBounds);
                sampledTerrainBounds = terrainBounds;
                terrainUpdated = true;
                terrainNeedsUpdate = false;
            }
        }
        recordPhases(serverTick, work, StructureSimulationBudget.Phase.TERRAIN_SCAN,
                System.nanoTime() - terrainStart, terrainUpdated ? 1 : 0);
        long physicsStart = System.nanoTime();
        var blocked = new LinkedHashSet<SubLevelId>();
        if (terrain != null) {
            for (var request : terrainRequests.entrySet()) {
                if (!terrain.covers(request.getValue())) blocked.add(request.getKey());
            }
        }
        if (!blocked.isEmpty()) {
            terrainNeedsUpdate = true;
            if (!terrainBlocked || serverTick >= nextTerrainWarning) {
                AcademyCraft.LOGGER.warn("SubLevel terrain admission paused {}/{} structures in {}: {};"
                                + " cache={}, paused_ids={}; covered structures continue simulating",
                        blocked.size(), terrainRequests.size(), host.dimension().identifier(),
                        terrain.coverageFailure(), terrain.diagnostics(), blocked.stream().limit(8).toList());
                nextTerrainWarning = serverTick + 100;
            }
            terrainBlocked = true;
        } else if (terrainBlocked) {
            AcademyCraft.LOGGER.info("SubLevel terrain admission restored in {}; paused structures resumed",
                    host.dimension().identifier());
            terrainBlocked = false;
        }
        if (LiveStructureOrientation.hasActive(host, false) || LiveStructureExtraction.hasActive(host, false)) {
            universe.stepCovered(duration, substeps, terrainUpdated, blocked,
                    seconds -> {
                        LiveStructureOrientation.beforeSubstep(host, false, seconds);
                        LiveStructureExtraction.beforeSubstep(host, false, seconds);
                    });
        } else {
            universe.stepCovered(duration, substeps, terrainUpdated, blocked);
        }
        long physicsNanos = System.nanoTime() - physicsStart;
        int simulated = 0;
        for (var managed : subLevels.values()) {
            var subLevel = managed.subLevel;
            if (subLevel.state() != SubLevelState.ACTIVE && subLevel.state() != SubLevelState.SLEEPING) continue;
            simulated++;
            var motion = universe.motion(subLevel.id()).orElse(null);
            if (motion == null) continue;
            var pose = structurePose(motion.pose());
            var nextState = motion.sleeping() ? SubLevelState.SLEEPING : SubLevelState.ACTIVE;
            if (pose.equals(subLevel.pose())) {
                if (subLevel.state() != nextState) {
                    subLevel.setState(nextState);
                    remember(subLevel);
                }
                continue;
            }
            var previous = subLevel.pose();
            subLevel.setPose(pose);
            subLevel.setState(nextState);
            collision.update(subLevel.id(), managed.localBoxes, pose, previous,
                    subLevel.geometryRevision(), subLevel.revision());
            budget.invalidate(host, budgetReference(subLevel), StructureSimulationBudget.DirtyPart.POSE);
            terrainNeedsUpdate = true;
            remember(subLevel);
            SubLevelTerrainBreaker.sweep(host, subLevel, previous, pose, managed.localBoxes);
        }
        if (simulated > 0) {
            long perStructure = Math.max(0L, physicsNanos / simulated);
            for (var managed : subLevels.values()) {
                if (managed.subLevel.state() != SubLevelState.ACTIVE
                        && managed.subLevel.state() != SubLevelState.SLEEPING) continue;
                budget.record(host, budgetReference(managed.subLevel),
                        StructureSimulationBudget.Phase.PHYSICS_STEP, perStructure,
                        universe.isSimulationPaused(managed.subLevel.id()) ? 0 : universe.bodyCount());
            }
        }
        if (plan != null && Boolean.getBoolean("academy.sublevel.budgetLog")) {
            AcademyCraft.LOGGER.debug("SubLevel budget tick={} scheduled={} deferred={}",
                    serverTick, plan.scheduled().size(), plan.deferred().size());
        }
    }

    private void recordPhases(long serverTick, List<StructureSimulationBudget.WorkItem> work,
                              StructureSimulationBudget.Phase phase, long elapsedNanos, long workUnits) {
        if (work.isEmpty()) return;
        long perStructure = Math.max(0L, elapsedNanos / work.size());
        for (var item : work) {
            budget.record(host, item.reference(), phase, perStructure, workUnits);
        }
    }

    private Map<SubLevelId, AABB> terrainRequests(double duration) {
        var demands = new ArrayList<TerrainDemand>();
        var players = host.players().stream().map(net.minecraft.world.entity.Entity::position).toList();
        boolean angularControl = LiveStructureOrientation.hasActive(host, false);
        for (var managed : subLevels.values()) {
            var state = managed.subLevel.state();
            if (state != SubLevelState.ACTIVE && state != SubLevelState.SLEEPING) continue;
            if (managed.localBoxes.isEmpty()) continue;
            var current = managed.subLevel.worldBounds();
            var motion = universe.motion(managed.subLevel.id()).orElse(null);
            var swept = motion == null ? current : sweptTerrainBounds(current,
                    managed.subLevel.localBounds(), motion, duration);
            if (angularControl && motion != null
                    && LiveStructureOrientation.controls(host, budgetReference(managed.subLevel))) {
                // The controller is applied only after terrain admission. Cover its complete
                // rotation envelope now even when the currently observed omega is still zero.
                swept = angularTerrainBounds(managed.subLevel.localBounds(), motion, duration);
            }
            swept = LiveStructureExtraction.terrainBounds(host, budgetReference(managed.subLevel), swept, duration);
            demands.add(new TerrainDemand(managed.subLevel.id(), swept,
                    terrainDistanceBand(swept, players), terrain == null || terrain.covers(swept)));
        }
        // Nearby requests may displace far sleeping coverage. Within the same 32-block band,
        // retain existing complete coverage first to avoid sub-block movement churning the cache.
        demands.sort(java.util.Comparator.comparingInt(TerrainDemand::distanceBand)
                .thenComparing(demand -> !demand.covered()));
        var bounds = new LinkedHashMap<SubLevelId, AABB>();
        for (var demand : demands) bounds.put(demand.id(), demand.bounds());
        // The empty space between unrelated structures is not a terrain interest area.
        // A shared union lets distant/void bodies consume the whole section budget.
        return bounds;
    }

    static int terrainDistanceBand(AABB bounds, List<Vec3> players) {
        double distance = Double.POSITIVE_INFINITY;
        for (var player : players) {
            double x = Math.max(Math.max(bounds.minX - player.x, player.x - bounds.maxX), 0);
            double y = Math.max(Math.max(bounds.minY - player.y, player.y - bounds.maxY), 0);
            double z = Math.max(Math.max(bounds.minZ - player.z, player.z - bounds.maxZ), 0);
            distance = Math.min(distance, Math.sqrt(x * x + y * y + z * z));
        }
        return !Double.isFinite(distance) ? Integer.MAX_VALUE : (int) Math.min(Integer.MAX_VALUE, distance / 32);
    }

    private record TerrainDemand(SubLevelId id, AABB bounds, int distanceBand, boolean covered) {}

    static AABB sweptTerrainBounds(AABB current, AABB localBounds, SubLevelPhysicsUniverse.Motion motion, double duration) {
        var linear = motion.linearVelocity();
        var angular = motion.angularVelocity();
        var center = motion.localCenterOfMass();
        double x = Math.max(Math.abs(localBounds.minX - center.x()), Math.abs(localBounds.maxX - center.x()));
        double y = Math.max(Math.abs(localBounds.minY - center.y()), Math.abs(localBounds.maxY - center.y()));
        double z = Math.max(Math.abs(localBounds.minZ - center.z()), Math.abs(localBounds.maxZ - center.z()));
        double radius = Math.sqrt(x * x + y * y + z * z);
        double angle = Math.sqrt(angular.x() * angular.x() + angular.y() * angular.y() + angular.z() * angular.z()) * duration;
        double rotationMargin = Math.min(2.0 * radius, angle * radius);
        return current.expandTowards(linear.x() * duration,
                linear.y() * duration - 4.905 * duration * duration, linear.z() * duration).inflate(rotationMargin);
    }

    /** Covers every rotation about the actual COM plus the complete translational step. */
    static AABB angularTerrainBounds(AABB localBounds, SubLevelPhysicsUniverse.Motion motion, double duration) {
        var localCenter = motion.localCenterOfMass();
        var center = structurePose(motion.pose()).localToWorldPoint(
                new Vec3(localCenter.x(), localCenter.y(), localCenter.z()));
        double x = Math.max(Math.abs(localBounds.minX - localCenter.x()), Math.abs(localBounds.maxX - localCenter.x()));
        double y = Math.max(Math.abs(localBounds.minY - localCenter.y()), Math.abs(localBounds.maxY - localCenter.y()));
        double z = Math.max(Math.abs(localBounds.minZ - localCenter.z()), Math.abs(localBounds.maxZ - localCenter.z()));
        double radius = Math.hypot(Math.hypot(x, y), z);
        var linear = motion.linearVelocity();
        return new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius)
                .expandTowards(linear.x() * duration, linear.y() * duration, linear.z() * duration)
                .expandTowards(0, -4.905 * duration * duration, 0);
    }

    public boolean terrainCoverageComplete() { return terrain == null || terrain.coverageComplete(); }

    private StructureRef budgetReference(SubLevel subLevel) {
        long generation = SubLevelActivationRegistry.find(subLevel.id())
                .map(SubLevelActivationRegistry.Activation::generation).orElse(0L);
        return SubLevelStructureRefs.of(subLevel, host.dimension().identifier(), generation);
    }

    /** Reattaches persisted ACTIVE/SLEEPING entries to their plot identities and one universe. */
    public int restore() {
        return restore(Map.of());
    }

    /** Server startup reserves every host's saved plots before any dimension can allocate. */
    int restore(Map<SubLevelId, PlotStorage.Lease> reservations) {
        if (manifest == null) return 0;
        int restored = 0;
        for (var entry : manifest.entries()) {
            if (subLevels.containsKey(entry.id()) || entry.state() == SubLevelState.REMOVED) continue;
            var lease = reservations.get(entry.id());
            if (lease == null) lease = storage.claim(entry.plot(), entry.localBounds()).orElse(null);
            if (lease != null && (!lease.plot().equals(entry.plot())
                    || storage.lease(entry.plot()).filter(lease::equals).isEmpty())) {
                throw new IllegalStateException("Saved SubLevel does not own its reserved plot");
            }
            if (lease == null) {
                AcademyCraft.LOGGER.warn("Could not reclaim SubLevel plot {} for {}", entry.plot(), entry.id());
                continue;
            }
            try {
                var reference = new StructureRef(entry.id().value(), host.dimension().identifier(), SubLevelStructureRefs.BACKEND, 0);
                if (org.academy.internal.common.structure.upgrade.lifecycle.MarkedGridRestoration.pending(host.getServer(), reference)) {
                    // Keep the durable source readable by recovery, but never publish two owners.
                    var isolated = new SubLevel(entry.id(), entry.plot(), entry.localBounds(), entry.pose());
                    SubLevelActivationRegistry.activation(entry.id());
                    isolated.setState(SubLevelState.LOADING);
                    restorationIsolation.put(entry.id(), entry.state());
                    subLevels.put(entry.id(), new Managed(isolated, lease, List.of(),
                            org.academy.internal.common.structure.space.NativeStructureView.EMPTY));
                    restored++;
                    continue;
                }
                var geometry = PlotGeometry.capture(storage, entry.plot(), entry.localBounds());
                if (entry.state() == SubLevelState.ALLOCATED || entry.state() == SubLevelState.LOADING) {
                    var staged = new SubLevel(entry.id(), entry.plot(), entry.localBounds(), entry.pose());
                    SubLevelActivationRegistry.activation(entry.id());
                    staged.setState(entry.state());
                    subLevels.put(entry.id(), new Managed(staged, lease, List.of(),
                            org.academy.internal.common.structure.space.NativeStructureView.EMPTY));
                    restored++;
                    continue;
                }
                if (geometry.boxes().isEmpty()) {
                    var remaining = new SubLevel(entry.id(), entry.plot(), entry.localBounds(), entry.pose());
                    var view = PlotView.capture(storage, entry.plot(), entry.localBounds());
                    SubLevelActivationRegistry.activation(entry.id());
                    remaining.setState(emptyBodyState(view.cells().size()));
                    subLevels.put(entry.id(), new Managed(remaining, lease, List.of(), view));
                    if (view.cells().isEmpty()) emptyMarkedCleanup.add(entry.id());
                    restored++;
                    continue;
                }
                boolean shouldSimulate = entry.state() == SubLevelState.ACTIVE
                        || entry.state() == SubLevelState.SLEEPING;
                boolean active = shouldSimulate && addBody(entry.id(), physicsPose(entry.pose()), geometry.boxes());
                var subLevel = new SubLevel(entry.id(), entry.plot(), entry.localBounds(), entry.pose());
                SubLevelActivationRegistry.activation(entry.id());
                subLevel.setState(active ? entry.state() : SubLevelState.INACTIVE);
                var localBoxes = localBoxes(geometry.boxes());
                var view = PlotView.capture(storage, entry.plot(), entry.localBounds());
                subLevels.put(entry.id(), new Managed(subLevel, lease, localBoxes, view));
                collision.update(entry.id(), localBoxes, entry.pose(), subLevel.geometryRevision(),
                        subLevel.revision());
                restored++;
            } catch (RuntimeException | Error failure) {
                // The manifest still owns the content. Releasing this reservation would let a
                // later capture clear the only copy after a geometry/native restore failure.
                AcademyCraft.LOGGER.error("Could not restore SubLevel {}", entry.id(), failure);
            }
        }
        return restored;
    }

    /** A rolled-back transfer may publish its retained source only after the journal was retired. */
    private void resumeRestorationSources() {
        if (restorationIsolation.isEmpty()) return;
        for (var id : List.copyOf(restorationIsolation.keySet())) {
            var reference = new StructureRef(id.value(), host.dimension().identifier(), SubLevelStructureRefs.BACKEND, 0);
            if (org.academy.internal.common.structure.upgrade.lifecycle.MarkedGridRestoration.pending(host.getServer(), reference)) continue;
            var managed = subLevels.get(id);
            if (managed == null) { restorationIsolation.remove(id); continue; }
            var geometry = PlotGeometry.capture(storage, managed.subLevel.plot(), managed.subLevel.localBounds());
            var view = PlotView.capture(storage, managed.subLevel.plot(), managed.subLevel.localBounds());
            var original = restorationIsolation.get(id);
            boolean simulate = original == SubLevelState.ACTIVE || original == SubLevelState.SLEEPING;
            boolean active = simulate && !geometry.boxes().isEmpty()
                    && addBody(id, physicsPose(managed.subLevel.pose()), geometry.boxes());
            if (simulate && !geometry.boxes().isEmpty() && !active) continue;
            var boxes = localBoxes(geometry.boxes());
            managed.subLevel.setState(active ? original : geometry.boxes().isEmpty()
                    ? emptyBodyState(view.cells().size()) : original);
            SubLevelActivationRegistry.advance(id);
            subLevels.put(id, new Managed(managed.subLevel, managed.lease, boxes, view));
            collision.update(id, boxes, managed.subLevel.pose(), managed.subLevel.geometryRevision(), managed.subLevel.revision());
            restorationIsolation.remove(id);
            remember(managed.subLevel);
            terrainNeedsUpdate = true;
        }
    }

    /** Persists active entries and closes bodies/leases without deleting their manifest rows. */
    public void close() {
        for (var managed : List.copyOf(subLevels.values())) {
            SubLevelActivationRegistry.find(managed.subLevel.id()).ifPresent(activation ->
                    SubLevelEnhancementBootstrap.INTERACTIONS.cancelStructure(host.getServer(),
                            SubLevelStructureRefs.of(managed.subLevel, host.dimension().identifier(),
                                    activation.generation()), activation.runtimeId(),
                            org.academy.api.common.structure.StructureInteractionController.CancelReason
                                    .SERVER_STOPPED, SubLevelServer.MINING_FEEDBACK));
            remember(managed.subLevel);
            managed.subLevel.setState(SubLevelState.INACTIVE);
            SubLevelActivationRegistry.invalidate(managed.subLevel.id());
            SubLevelMotionController.cancel(managed.subLevel.id());
            try { universe.remove(managed.subLevel.id()); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("SubLevel body close failed", failure); }
            try { storage.release(managed.lease); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("SubLevel plot close failed", failure); }
        }
        subLevels.clear();
        restorationIsolation.clear();
        emptyMarkedCleanup.clear();
        collision.clear();
        SubLevelCollisionRuntime.unregister(host, collision);
        SubLevelTerrainBreaker.forgetHost(host);
        budget.closeHost(host);
        if (terrain != null) terrain.close();
        persist();
    }

    /** Records one measured snapshot-encoding phase for every published body. */
    public void recordSnapshot(long elapsedNanos, int structures) {
        if (structures <= 0 || elapsedNanos < 0) return;
        long perStructure = elapsedNanos / structures;
        for (var managed : subLevels.values()) {
            if (managed.subLevel.state() != SubLevelState.ACTIVE
                    && managed.subLevel.state() != SubLevelState.SLEEPING
                    && managed.subLevel.state() != SubLevelState.INACTIVE) continue;
            budget.record(host, budgetReference(managed.subLevel),
                    StructureSimulationBudget.Phase.SNAPSHOT_ENCODE, perStructure, structures);
        }
    }

    public TerrainColliderCache.Diagnostics probeTerrain(AABB bounds) {
        if (terrain == null) throw new IllegalStateException("Terrain cache is not configured");
        return terrain.update(List.of(Objects.requireNonNull(bounds, "bounds")));
    }

    public void onHostBlockChanged(net.minecraft.core.BlockPos position) {
        if (terrain != null) terrain.onBlockChanged(position);
        terrainNeedsUpdate = true;
        for (var managed : subLevels.values()) {
            budget.invalidate(host, budgetReference(managed.subLevel),
                    StructureSimulationBudget.DirtyPart.TERRAIN);
        }
    }

    public TerrainColliderCache.Diagnostics terrainDiagnostics() {
        if (terrain == null) return new TerrainColliderCache.Diagnostics(0, 0, 0, 0);
        return terrain.diagnostics();
    }

    public int size() {
        return subLevels.size();
    }

    public List<SubLevel> subLevels() {
        return subLevels.values().stream().map(managed -> managed.subLevel).toList();
    }

    public List<AABB> localCollisionBoxes(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        return managed == null ? List.of() : managed.localBoxes;
    }

    public org.academy.internal.common.structure.space.NativeStructureView view(SubLevelId id) {
        var managed = subLevels.get(Objects.requireNonNull(id, "id"));
        return managed == null ? org.academy.internal.common.structure.space.NativeStructureView.EMPTY : managed.view;
    }

    private boolean addBody(SubLevelId id, PhysicsPose pose, List<PhysicsBox> boxes) {
        if (universe.activeCount() >= SubLevelPhysicsUniverse.MAX_ACTIVE) return false;
        universe.add(id, pose, boxes);
        return true;
    }

    private void remember(SubLevel subLevel) {
        if (manifest == null) return;
        manifest.put(new SubLevelManifest.Entry(subLevel.id(), subLevel.plot(), subLevel.localBounds(),
                subLevel.pose(), restorationIsolation.getOrDefault(subLevel.id(), subLevel.state()), subLevel.revision()));
    }

    private void forget(SubLevelId id) {
        if (manifest == null) return;
        manifest.remove(id);
    }

    public void persist() {
        if (manifest == null) return;
        try { manifest.persist(); }
        catch (IOException failure) { throw new IllegalStateException("Could not persist SubLevel manifest", failure); }
    }

    private static PhysicsPose physicsPose(StructurePose pose) {
        return new PhysicsPose(new org.academy.internal.common.structure.physics.PhysicsVector(
                pose.worldOrigin().x, pose.worldOrigin().y, pose.worldOrigin().z),
                new org.academy.internal.common.structure.physics.PhysicsQuaternion(
                        pose.rotationX(), pose.rotationY(), pose.rotationZ(), pose.rotationW()));
    }

    private static StructurePose structurePose(PhysicsPose pose) {
        var position = pose.position();
        return new StructurePose(new Vec3(position.x(), position.y(), position.z()),
                pose.rotation().x(), pose.rotation().y(), pose.rotation().z(), pose.rotation().w());
    }

    private static List<AABB> localBoxes(List<PhysicsBox> boxes) {
        var result = new ArrayList<AABB>(boxes.size());
        for (var box : boxes) {
            var center = box.center();
            var half = box.halfExtents();
            result.add(new AABB(center.x() - half.x(), center.y() - half.y(), center.z() - half.z(),
                    center.x() + half.x(), center.y() + half.y(), center.z() + half.z()));
        }
        return List.copyOf(result);
    }

    private record Managed(SubLevel subLevel, PlotStorage.Lease lease, List<AABB> localBoxes,
                           org.academy.internal.common.structure.space.NativeStructureView view) {
        private Managed {
            localBoxes = List.copyOf(localBoxes);
        }
    }
}
