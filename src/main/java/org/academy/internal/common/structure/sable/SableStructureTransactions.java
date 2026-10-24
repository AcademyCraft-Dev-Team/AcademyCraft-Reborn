package org.academy.internal.common.structure.sable;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.transaction.CaptureCheckpointPolicy;
import org.academy.internal.common.structure.transaction.CaptureTarget;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Sable capture executor for the neutral staged coordinator. It allocates a real sub-level,
 * writes cells through the real {@link LevelChunk} update chain so Sable drives mass,
 * collision and heat data, and persists through Sable's own holding chunk map. The public
 * capture grid is anchored like the native backend: {@code StructurePose.worldOrigin()}
 * equals the selection's lower corner and local offset {@code n} maps to world
 * {@code origin + n}.
 */
public final class SableStructureTransactions implements CaptureTarget {
    private static final int WRITE_FLAGS = Block.UPDATE_SKIP_ON_PLACE
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    /** Empty chunks are pre-built across the largest admissible selection span. */
    private static final int PREFABRICATED_SPAN = StructureCaptureRequest.MAXIMUM_SPAN;

    private final MinecraftServer server;

    public SableStructureTransactions(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Identifier backendId() {
        return StructureBackendRouter.SABLE_ID;
    }

    @Override
    public @Nullable String rejectionDetail(ServerLevel host, BlockPos position, BlockState state,
                                             @Nullable BlockEntity blockEntity) {
        return SableCaptureAdmission.rejectionDetail(host, position, state, blockEntity);
    }

    @Override
    public boolean canSeed(ServerLevel host, BlockPos position, BlockState state) {
        return SableCaptureAdmission.seedable(!state.getCollisionShape(host, position).isEmpty(),
                PhysicsBlockPropertyHelper.getMass(host, position, state));
    }

    @Override
    public Optional<StructureRef> allocate(ServerLevel host, BlockPos origin, BlockPos seedOffset, BlockState seedState) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(seedOffset, "seedOffset");
        Objects.requireNonNull(seedState, "seedState");
        SableStructureAccess.requireThread(server);
        var container = SubLevelContainer.getContainer(host);
        if (container == null) return Optional.empty();
        ServerSubLevel ship = null;
        try {
            // The empty body has no mass pivot yet. The first real write installs Sable's
            // seed COM without translating the body, so this placeholder must be corrected
            // from the resulting mass data before returning the allocation.
            var position = new Vector3d(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
            var pose = new Pose3d(position, new Quaterniond(), new Vector3d(), new Vector3d(1.0, 1.0, 1.0));
            var allocated = SableContainerGuard.containerAllocation("sable.capture.allocate",
                    () -> container.allocateNewSubLevel(pose)).orElse(null);
            if (!(allocated instanceof ServerSubLevel serverSubLevel)) return Optional.empty();
            ship = serverSubLevel;
            var plot = ship.getPlot();
            var plotOrigin = plot.getCenterBlock();
            ship.logicalPose().rotationPoint().set(plotOrigin.getX() + 0.5, plotOrigin.getY() + 0.5,
                    plotOrigin.getZ() + 0.5);
            prepareChunks(plot, plotOrigin);
            var reference = new StructureRef(ship.getUniqueId(), host.dimension().identifier(),
                    StructureBackendRouter.SABLE_ID, 0);
            // Sable removes a zero-mass body on its own sweep; the seed must land before this
            // call chain returns or the ship is destroyed before the copy stage can fill it.
            if (writeCell(reference, seedOffset, seedState) != StructureWriteResult.APPLIED) {
                AcademyCraft.LOGGER.error("Sable capture could not seed its allocated ship for origin {}", origin);
                removeQuietly(container, ship);
                return Optional.empty();
            }
            if (!alignCaptureGrid(ship, origin)) {
                removeQuietly(container, ship);
                return Optional.empty();
            }
            return Optional.of(reference);
        } catch (RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.error("Sable capture allocation failed for origin {}", origin, failure);
            if (ship != null) removeQuietly(container, ship);
            return Optional.empty();
        }
    }

    @Override
    public double estimateMass(ServerLevel host, List<BlockPos> positions) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(positions, "positions");
        SableStructureAccess.requireThread(server);
        double mass = 0.0;
        for (var position : positions) {
            mass += PhysicsBlockPropertyHelper.getMass(host, position, host.getBlockState(position));
        }
        return Double.isFinite(mass) ? mass : Double.NaN;
    }

    @Override
    public int cellBudget() {
        return StructureCaptureRequest.MAXIMUM_BLOCKS;
    }

    @Override
    public Optional<List<RestorationCell>> restorationCells(StructureRef target) {
        var ship = ship(target);
        if (ship == null || SableContainerGuard.inContainerTick()
                || SableStructureJoints.hasAny(ship.getLevel(), ship)
                || ship.getPlot().getBlockEntityActors().iterator().hasNext()
                || !ship.getPlot().getContraptions().isEmpty()) return Optional.empty();
        var plot = ship.getPlot();
        var bounds = plot.getBoundingBox();
        if (bounds == null || bounds.volume() <= 0 || bounds.volume() > 32768) return Optional.empty();
        var cells = new ArrayList<RestorationCell>();
        var seen = new java.util.HashSet<ChunkPos>();
        var manager = ((org.academy.mixin.common.ServerLevelEntityManagerAccessor) ship.getLevel()).academy$getEntityManager();
        @SuppressWarnings("unchecked")
        var sections = ((org.academy.mixin.common.PersistentEntitySectionManagerAccessor<net.minecraft.world.entity.Entity>)
                (Object) manager).academy$getSectionStorage();
        for (var position : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            var chunk = chunk(plot, position);
            if (chunk == null) return Optional.empty();
            if (seen.add(chunk.getPos()) && sections.getExistingSectionsInChunk(chunk.getPos().pack())
                    .anyMatch(section -> section.getEntities().anyMatch(entity -> !entity.isRemoved()))) return Optional.empty();
            var state = chunk.getBlockState(position);
            if (state.isAir()) continue;
            if (state.hasBlockEntity() || !state.getFluidState().isEmpty()
                    || chunk.getBlockEntities().containsKey(position)
                    || cells.size() >= StructureCaptureRequest.MAXIMUM_BLOCKS) return Optional.empty();
            cells.add(new RestorationCell(position.subtract(plot.getCenterBlock()), state));
        }
        return cells.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(cells));
    }

    @Override
    public Optional<net.minecraft.nbt.CompoundTag> restorationEvidence(StructureRef target) {
        var ship = ship(target);
        if (ship == null || SableContainerGuard.inContainerTick()) return Optional.empty();
        var tag = new net.minecraft.nbt.CompoundTag();
        var grid = (org.academy.mixin.common.compat.SableContainerGridAccessor) (Object) container(target.dimension());
        tag.putInt("plot_x", ship.getPlot().plotPos.x() - grid.academy$originX());
        tag.putInt("plot_z", ship.getPlot().plotPos.z() - grid.academy$originZ());
        var pointer = ship.getLastSerializationPointer();
        tag.putBoolean("saved", pointer != null);
        if (pointer != null) {
            tag.putInt("chunk_x", pointer.chunkPos().x());
            tag.putInt("chunk_z", pointer.chunkPos().z());
            tag.putInt("storage", pointer.storageIndex());
            tag.putInt("slot", pointer.subLevelIndex());
        }
        return Optional.of(tag);
    }

    @Override
    public boolean retireRestored(StructureRef target, net.minecraft.nbt.CompoundTag evidence) {
        SableStructureAccess.requireThread(server);
        var container = container(target.dimension());
        if (container == null || SableContainerGuard.inContainerTick()) return false;
        try {
            var ship = container.getSubLevel(target.id());
            // The creation checkpoint always assigns a pointer. Missing ownership evidence
            // cannot authorize backend removal, even if this activation is currently absent.
            if (!evidence.getBoolean("saved").orElseThrow()) return false;
            var position = new ChunkPos(evidence.getInt("chunk_x").orElseThrow(), evidence.getInt("chunk_z").orElseThrow());
            var storage = (org.academy.mixin.common.compat.SableStorageInvoker) (Object)
                    container.getHoldingChunkMap().getStorage();
            int storageIndex = evidence.getInt("storage").orElseThrow(), slot = evidence.getInt("slot").orElseThrow();
            var pointer = new dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer(
                    position, (short) storageIndex, (short) slot);
            var file = storage.academy$storageFile(position, storageIndex);
            var image = file.read(slot);
            boolean ownedImage = image != null && dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData.fromTag(image)
                    .uuid().equals(target.id());
            boolean foreignImage = image != null && !ownedImage;
            // Removing a live source queues its pointer for deletion. Do not create that
            // queue entry if its durable slot has already changed owner or lost its proof.
            if (ship != null && (!(ship instanceof ServerSubLevel live) || !ownedImage
                    || !pointer.equals(live.getLastSerializationPointer()))) return false;
            if (!retireHoldingSource(container, target.id(), pointer, ownedImage, foreignImage)) return false;
            if (ship != null) container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
            if (container.getSubLevel(target.id()) != null) return false;
            // An already-reused slot belongs to a different body; never erase its pointer or content.
            if (foreignImage) return false;
            // Only a positively identified source image authorizes taking over delayed
            // deletion. A reused body's queue entry must survive this old journal's retry.
            if (ownedImage) ((org.academy.mixin.common.compat.SableHoldingDeletionAccessor) (Object) container.getHoldingChunkMap())
                    .academy$queuedDeletion().remove(pointer);
            var region = storage.academy$regionFile(position);
            int index = region.getIndex(position.getRegionLocalX(), position.getRegionLocalZ());
            var holdingTag = region.read(index);
            if (holdingTag != null) {
                var holding = dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk.from(position, holdingTag);
                var local = new dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer((short) storageIndex, (short) slot);
                holding.getSubLevelPointers().remove(local);
                var updated = new net.minecraft.nbt.CompoundTag();
                holding.writeTo(updated);
                region.write(index, updated);
                region.flush();
            }
            // Retire the index before freeing the data slot, preventing stale-pointer reuse.
            file.write(slot, (net.minecraft.nbt.CompoundTag) null);
            file.flush();
            return releaseRestoredPlot(container, target, evidence, ownedImage);
        } catch (java.io.IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Sable marked restoration retirement remains pending for {}", target.id(), failure);
            return false;
        }
    }

    /** Retires the exact cached source too: an unloaded holding image can otherwise respawn it after COMMIT. */
    private static boolean retireHoldingSource(ServerSubLevelContainer container, UUID source,
            dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer pointer,
            boolean ownedImage, boolean foreignImage) {
        var holding = (org.academy.mixin.common.compat.SableHoldingDeletionAccessor) (Object) container.getHoldingChunkMap();
        var indexed = holding.academy$holdingSources().get(source);
        if (indexed != null && !pointer.equals(indexed.pointer())) return false;
        var chunk = holding.academy$holdingChunks().get(pointer.chunkPos().pack());
        if (chunk == null) return indexed == null;
        boolean cachedSource = false;
        boolean foreignCachedPointer = false;
        for (var cached : chunk.getLoadedHoldingSubLevels()) {
            if (cached.data().uuid().equals(source)) {
                if (!pointer.equals(cached.pointer())) return false;
                cachedSource = true;
            } else if (pointer.equals(cached.pointer())) foreignCachedPointer = true;
        }
        if (indexed != null && !cachedSource || !foreignImage && foreignCachedPointer) return false;
        boolean ownsPointer = ownedImage || cachedSource;
        if (!foreignImage && !ownsPointer && chunk.getSubLevelPointers().contains(pointer.local())) return false;
        holding.academy$holdingSources().remove(source);
        chunk.removeHoldingSubLevel(source);
        if (!foreignImage && ownsPointer) chunk.getSubLevelPointers().remove(pointer.local());
        return true;
    }

    private static boolean releaseRestoredPlot(ServerSubLevelContainer container, StructureRef target,
                                               net.minecraft.nbt.CompoundTag evidence, boolean ownedImage) throws java.io.IOException {
        int x = evidence.getInt("plot_x").orElseThrow(), z = evidence.getInt("plot_z").orElseThrow();
        var replacement = container.getSubLevel(x, z);
        if (replacement != null) return false;
        var holding = (org.academy.mixin.common.compat.SableHoldingDeletionAccessor) (Object) container.getHoldingChunkMap();
        for (var cached : holding.academy$holdingSources().values()) {
            var plot = cached.data().fullTag().getCompound("plot").orElse(null);
            if (plot == null || plot.getInt("plot_x").isEmpty() || plot.getInt("plot_z").isEmpty()) return false;
            if (plot.getInt("plot_x").orElseThrow() == x && plot.getInt("plot_z").orElseThrow() == z) return false;
        }
        int index = container.getIndex(x, z);
        // An empty old storage slot does not prove who owns an occupied plot now. A retry
        // with ambiguous ownership keeps its reservation instead of freeing an unloaded body.
        if (!ownedImage && container.getOccupancy().get(index)) return false;
        container.getOccupancy().clear(index);
        SableRestorationDurability.occupancy(container.getLevel());
        return true;
    }

    @Override
    public boolean checkpointRestoration(StructureRef target) {
        var ship = ship(target);
        var container = container(target.dimension());
        if (ship == null || container == null || SableContainerGuard.inContainerTick()) return false;
        // Captures already have a serialization pointer. A never-saved body is retained until
        // Sable assigns one; do not improvise the backend's allocator bookkeeping.
        var pointer = ship.getLastSerializationPointer();
        if (pointer == null) return false;
        try {
            var storage = (org.academy.mixin.common.compat.SableStorageInvoker) (Object) container.getHoldingChunkMap().getStorage();
            var file = storage.academy$storageFile(pointer.chunkPos(), pointer.storageIndex());
            var existing = file.read(pointer.subLevelIndex());
            if (existing != null && !dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData.fromTag(existing).uuid().equals(target.id())) return false;
            var data = dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer.toData(ship, List.of());
            file.write(pointer.subLevelIndex(), data.fullTag());
            file.flush();
            var region = storage.academy$regionFile(pointer.chunkPos());
            int index = region.getIndex(pointer.chunkPos().getRegionLocalX(), pointer.chunkPos().getRegionLocalZ());
            var tag = region.read(index);
            var holding = tag == null
                    ? new dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk(pointer.chunkPos())
                    : dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk.from(pointer.chunkPos(), tag);
            if (!holding.getSubLevelPointers().contains(pointer.local())) holding.getSubLevelPointers().add(pointer.local());
            var updated = new net.minecraft.nbt.CompoundTag();
            holding.writeTo(updated);
            region.write(index, updated);
            region.flush();
            SableRestorationDurability.occupancy(ship.getLevel());
            return true;
        } catch (java.io.IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Sable restoration source checkpoint failed for {}", target.id(), failure);
            return false;
        }
    }

    @Override
    public boolean ready(StructureRef target) {
        return ship(target) != null;
    }

    @Override
    public boolean isolateRestoration(StructureRef target) {
        var source = ship(target);
        return source != null && SableRestorationIsolation.isolate(source.getLevel(), source);
    }

    @Override
    public StructureWriteResult writeCell(StructureRef target, BlockPos localOffset, BlockState state) {
        Objects.requireNonNull(localOffset, "localOffset");
        Objects.requireNonNull(state, "state");
        SableStructureAccess.requireThread(server);
        var ship = ship(target);
        if (ship == null) return StructureWriteResult.NOT_READY;
        var plot = ship.getPlot();
        var position = plotPosition(plot, ship.getLevel(), localOffset);
        if (position == null) return StructureWriteResult.OUT_OF_BOUNDS;
        var chunk = chunk(plot, position);
        if (chunk == null) return StructureWriteResult.NOT_READY;
        var current = chunk.getBlockState(position);
        if (!current.isAir()) return StructureWriteResult.CONFLICT;
        // The real setBlockState path drives Sable mass, collision, heat and bounds updates.
        chunk.setBlockState(position, state, WRITE_FLAGS);
        return chunk.getBlockState(position).equals(state) ? StructureWriteResult.APPLIED
                : StructureWriteResult.REJECTED;
    }

    @Override
    public Optional<BlockState> readCell(StructureRef target, BlockPos localOffset) {
        Objects.requireNonNull(localOffset, "localOffset");
        SableStructureAccess.requireThread(server);
        var ship = ship(target);
        if (ship == null) return Optional.empty();
        var plot = ship.getPlot();
        var position = plotPosition(plot, ship.getLevel(), localOffset);
        if (position == null) return Optional.empty();
        var chunk = chunk(plot, position);
        return chunk == null ? Optional.empty() : Optional.of(chunk.getBlockState(position));
    }

    @Override
    public StructureWriteResult clearCell(StructureRef target, BlockPos localOffset, BlockState expected) {
        Objects.requireNonNull(expected, "expected");
        var current = readCell(target, localOffset).orElse(null);
        if (current == null) return StructureWriteResult.NOT_READY;
        if (current.isAir()) return StructureWriteResult.APPLIED;
        if (!current.equals(expected)) return StructureWriteResult.CONFLICT;
        return writeAir(target, localOffset);
    }

    @Override
    public boolean quarantined(StructureRef target) {
        var ship = ship(target);
        return ship == null || emptyBounds(ship.getPlot());
    }

    @Override
    public void deactivate(StructureRef target) {
        // Sable keeps its loaded ships; there is no unload step for an empty capture target.
    }

    /**
     * Best-effort checkpoint through Sable's own holding chunk map. Sable does not expose a
     * transactional or per-ship save result, and its removal sweep can leave a removed or
     * zero-mass child inside the container, which makes {@code saveAll} throw while serializing;
     * those invalid children are pruned, except this transaction's target, and the checkpoint is
     * retried once. Any remaining failure stays unconfirmed so the coordinator retains the
     * transaction instead of publishing it.
     */
    @Override
    public boolean save(StructureRef target) {
        SableStructureAccess.requireThread(server);
        var container = container(target.dimension());
        if (container == null) return false;
        try {
            container.getHoldingChunkMap().saveAll();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (!removeInvalidBodies(container, target.id())) {
                AcademyCraft.LOGGER.error("Sable capture checkpoint failed for {}", target.id(), failure);
                return false;
            }
            AcademyCraft.LOGGER.warn("Sable capture checkpoint pruned invalid sub-levels and retries for {}",
                    target.id());
            try {
                container.getHoldingChunkMap().saveAll();
                return true;
            } catch (RuntimeException | LinkageError retry) {
                retry.addSuppressed(failure);
                AcademyCraft.LOGGER.error("Sable capture checkpoint failed for {}", target.id(), retry);
                return false;
            }
        }
    }

    /**
     * Sable has no empty-slot concept, so releasing an empty target means removing the whole
     * ship. This runs only for pre-decision rollback or capacity reclamation.
     */
    @Override
    public boolean releaseEmpty(StructureRef target) {
        SableStructureAccess.requireThread(server);
        var container = container(target.dimension());
        if (container == null) return true;
        var subLevel = container.getSubLevel(target.id());
        if (subLevel == null) return true;
        try {
            var executed = SableContainerGuard.containerMutation("sable.capture.release",
                    () -> container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED));
            return executed ? container.getSubLevel(target.id()) == null : true;
        } catch (RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.error("Sable capture could not remove target {}", target.id(), failure);
            return false;
        }
    }

    @Override
    public Optional<StructureHandle> resolve(StructureRef target, boolean recovery) {
        SableStructureAccess.requireThread(server);
        // Sable owns loading; an unloaded ship is never force-loaded by the transaction layer.
        return SableStructureAccess.resolveHandle(server, target).map(handle -> handle);
    }

    @Override
    public boolean verifyCopy(StructureRef target, int expectedCells) {
        var ship = ship(target);
        return ship != null && !emptyBounds(ship.getPlot());
    }

    @Override
    public boolean finalizeTarget(StructureRef target, BlockPos origin, List<BlockPos> sourcePositions) {
        Objects.requireNonNull(origin, "origin");
        SableStructureAccess.requireThread(server);
        var ship = ship(target);
        return ship != null && alignCaptureGrid(ship, origin);
    }

    /**
     * Establishes the capture grid using the actual mass pivot, including seeds away from
     * the selection's minimum corner. Updating only logicalPose would leave Rapier's body
     * at its old position and the first physics step would overwrite the apparent fix.
     */
    private static boolean alignCaptureGrid(ServerSubLevel ship, BlockPos origin) {
        ship.updateMergedMassData(1.0f);
        var mass = ship.getMassTracker();
        if (mass == null || !Double.isFinite(mass.getMass()) || mass.getMass() <= 0
                || mass.getCenterOfMass() == null) return false;
        var center = mass.getCenterOfMass();
        if (!Double.isFinite(center.x()) || !Double.isFinite(center.y()) || !Double.isFinite(center.z())) {
            return false;
        }
        var container = SubLevelContainer.getContainer(ship.getLevel());
        if (container == null) return false;
        var plotOrigin = ship.getPlot().getCenterBlock();
        var position = new Vector3d(
                origin.getX() + (center.x() - plotOrigin.getX()),
                origin.getY() + (center.y() - plotOrigin.getY()),
                origin.getZ() + (center.z() - plotOrigin.getZ()));
        var pose = ship.logicalPose();
        pose.rotationPoint().set(center);
        container.physicsSystem().getPipeline().teleport(ship, position, new Quaterniond());
        ship.updateLastPose();
        ship.updateBoundingBox();
        return true;
    }

    private StructureWriteResult writeAir(StructureRef target, BlockPos localOffset) {
        var ship = ship(target);
        if (ship == null) return StructureWriteResult.NOT_READY;
        var plot = ship.getPlot();
        var position = plotPosition(plot, ship.getLevel(), localOffset);
        if (position == null) return StructureWriteResult.OUT_OF_BOUNDS;
        var chunk = chunk(plot, position);
        if (chunk == null) return StructureWriteResult.NOT_READY;
        chunk.setBlockState(position, Blocks.AIR.defaultBlockState(), WRITE_FLAGS);
        return chunk.getBlockState(position).isAir() ? StructureWriteResult.APPLIED
                : StructureWriteResult.REJECTED;
    }

    private static void prepareChunks(LevelPlot plot, BlockPos plotOrigin) {
        var start = new ChunkPos(plotOrigin.getX() >> 4, plotOrigin.getZ() >> 4);
        var end = plotOrigin.offset(PREFABRICATED_SPAN, 0, PREFABRICATED_SPAN);
        var last = new ChunkPos(end.getX() >> 4, end.getZ() >> 4);
        for (int x = start.x(); x <= last.x(); x++) {
            for (int z = start.z(); z <= last.z(); z++) {
                var global = new ChunkPos(x, z);
                if (plot.getChunkHolder(plot.toLocal(global)) == null) plot.newEmptyChunk(global);
            }
        }
    }

    private static @Nullable BlockPos plotPosition(LevelPlot plot, ServerLevel host, BlockPos localOffset) {
        final BlockPos position;
        try {
            position = SableStructureTransforms.plotPosition(plot.getCenterBlock(), localOffset);
        } catch (ArithmeticException overflow) {
            return null;
        }
        if (host.isOutsideBuildHeight(position) || !plot.contains(position.getX(), position.getZ())) return null;
        return position;
    }

    private static @Nullable LevelChunk chunk(LevelPlot plot, BlockPos position) {
        var global = new ChunkPos(position.getX() >> 4, position.getZ() >> 4);
        var holder = plot.getChunkHolder(plot.toLocal(global));
        return holder == null ? null : holder.getChunk();
    }

    private static boolean emptyBounds(LevelPlot plot) {
        var bounds = plot.getBoundingBox();
        return bounds == null || bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0;
    }

    private void removeQuietly(ServerSubLevelContainer container, ServerSubLevel ship) {
        SableContainerGuard.containerMutation("sable.capture.rollback", () -> {
            try {
                container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
            } catch (RuntimeException | LinkageError failure) {
                AcademyCraft.LOGGER.error("Sable capture could not remove a failed allocation", failure);
            }
        });
    }

    /**
     * Prunes container children whose body Sable already removed or that carry no mass, exactly
     * mirroring its own removal sweep; the capture target is protected because a checkpoint may
     * observe it while its content is still being settled. Returns true when at least one child
     * was removed so the caller may retry the checkpoint once.
     */
    private static boolean removeInvalidBodies(ServerSubLevelContainer container, UUID targetId) {
        var bodies = new ArrayList<CaptureCheckpointPolicy.Body>();
        var ships = new HashMap<UUID, ServerSubLevel>();
        for (var subLevel : List.copyOf(container.getAllSubLevels())) {
            if (subLevel.getUniqueId().equals(targetId)) continue;
            var tracker = subLevel.getMassTracker();
            bodies.add(new CaptureCheckpointPolicy.Body(subLevel.getUniqueId(), subLevel.isRemoved(),
                    tracker != null && !tracker.isInvalid()));
            ships.put(subLevel.getUniqueId(), subLevel);
        }
        var removed = false;
        for (var id : CaptureCheckpointPolicy.invalidBodies(targetId, bodies)) {
            var ship = ships.get(id);
            if (ship == null) continue;
            try {
                if (!ship.isRemoved()) {
                    ship.getPlot().destroyAllBlocks();
                    ship.markRemoved();
                }
                container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
                removed = true;
            } catch (RuntimeException | LinkageError failure) {
                AcademyCraft.LOGGER.error("Sable capture could not prune an invalid sub-level {}", id, failure);
            }
        }
        return removed;
    }

    private @Nullable ServerSubLevel ship(StructureRef target) {
        if (!StructureBackendRouter.SABLE_ID.equals(target.backend()) || target.generation() != 0) return null;
        var container = container(target.dimension());
        if (container == null) return null;
        var subLevel = container.getSubLevel(target.id());
        return subLevel instanceof ServerSubLevel ship && !ship.isRemoved() ? ship : null;
    }

    private @Nullable ServerSubLevelContainer container(Identifier dimension) {
        for (var level : server.getAllLevels()) {
            if (!level.dimension().identifier().equals(dimension)) continue;
            return SubLevelContainer.getContainer(level);
        }
        return null;
    }
}
