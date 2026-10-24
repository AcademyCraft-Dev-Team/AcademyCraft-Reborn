package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.academy.internal.common.structure.transaction.StructureTransactionJournal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Phase 1 debug capture bridge: copies a bounded host selection into a plot, removes the
 * source only after the SubLevel body exists, and restores the source on any failure.
 * This is not yet the permission-aware skill transaction; it is the first real plot writer.
 */
public final class SubLevelCaptureService {
    public static final int MAX_BLOCKS = 16_384;
    public static final int MAX_SPAN = 24;
    public static final int TRANSACTION_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_ON_PLACE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SUPPRESS_DROPS;

    private SubLevelCaptureService() {
    }

    public static Result capture(ServerLevel host, SubLevelRuntime runtime, List<BlockPos> positions) {
        return capture(host, runtime, positions, (level, position) -> true);
    }

    public static Result capture(ServerLevel host, SubLevelRuntime runtime, List<BlockPos> positions,
                                 java.util.function.BiPredicate<ServerLevel, BlockPos> permission) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(permission, "permission");
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Capture requires the server thread");
        if (host != runtime.host()) throw new IllegalArgumentException("Host/runtime dimension mismatch");
        if (host == runtime.storage().level() || NativeStructureDimensions.isStorageLevel(host.dimension())) {
            throw new IllegalArgumentException("Cannot capture from a storage/plot dimension");
        }
        var normalized = new LinkedHashSet<BlockPos>();
        for (var position : positions) {
            Objects.requireNonNull(position, "position");
            normalized.add(position.immutable());
        }
        if (normalized.isEmpty() || normalized.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("Capture must contain 1.." + MAX_BLOCKS + " positions");
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var position : normalized) {
            minX = Math.min(minX, position.getX()); minY = Math.min(minY, position.getY());
            minZ = Math.min(minZ, position.getZ()); maxX = Math.max(maxX, position.getX());
            maxY = Math.max(maxY, position.getY()); maxZ = Math.max(maxZ, position.getZ());
        }
        if (maxX - minX >= MAX_SPAN || maxY - minY >= MAX_SPAN || maxZ - minZ >= MAX_SPAN) {
            throw new IllegalArgumentException("Capture span exceeds " + MAX_SPAN);
        }
        for (var position : normalized) {
            if (!host.hasChunkAt(position) || !host.isInWorldBounds(position)) {
                throw new IllegalArgumentException("source_unavailable:" + position);
            }
            if (!permission.test(host, position)) throw new IllegalArgumentException("permission_denied:" + position);
        }
        var lease = runtime.storage().allocate().orElse(null);
        if (lease == null) throw new IllegalStateException("No free plot");
        var copied = new ArrayList<Copied>(normalized.size());
        var removed = new ArrayList<Copied>(copied.size());
        SubLevelId id = null;
        StructureTransactionJournal journal = null;
        StructureTransactionJournal.Record journalRecord = null;
        try {
            for (var position : normalized) {
                var state = host.getBlockState(position);
                if (state.isAir()) continue;
                var local = new BlockPos(position.getX() - minX, position.getY() - minY, position.getZ() - minZ);
                var sourceEntity = host.getBlockEntity(position);
                var tag = sourceEntity == null ? null : sourceEntity.saveWithFullMetadata(host.registryAccess());
                var placed = runtime.storage().setBlock(lease.plot(), local, state, TRANSACTION_FLAGS);
                if (!placed && !runtime.storage().blockState(lease.plot(), local).equals(state)) {
                    throw new IllegalStateException("Could not copy captured source block " + position);
                }
                AcademyCraft.LOGGER.debug("SubLevel capture block copy at {} source={} placed={} plot={}",
                        local, state, placed, runtime.storage().blockState(lease.plot(), local));
                if (tag != null) {
                    var plotPosition = runtime.storage().worldPosition(lease.plot(), local);
                    var loaded = BlockEntity.loadStatic(plotPosition, state, tag, runtime.storage().level().registryAccess());
                    if (loaded == null) throw new IllegalStateException("Could not restore block entity " + position);
                    runtime.storage().level().setBlockEntity(loaded);
                    loaded.setChanged();
                    var observed = runtime.storage().blockEntity(lease.plot(), local);
                    AcademyCraft.LOGGER.debug("SubLevel plot BE copy at {} state={} loaded={} observed={}",
                            local, runtime.storage().blockState(lease.plot(), local), loaded.getClass().getSimpleName(),
                            observed == null ? "null" : observed.getClass().getSimpleName());
                    if (observed == null) throw new IllegalStateException("Plot block entity was not attached " + position);
                }
                copied.add(new Copied(position, state, tag));
            }
            if (copied.isEmpty()) throw new IllegalArgumentException("Capture contains no non-air blocks");
            var bounds = new AABB(0, 0, 0, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
            var geometry = PlotGeometry.capture(runtime.storage(), lease.plot(), bounds);
            if (geometry.boxes().isEmpty()) throw new IllegalArgumentException("Capture has no collision geometry");
            id = SubLevelId.random();
            journal = SubLevelStaticizeJournal.journal(host.getServer()).orElse(null);
            if (journal == null) throw new IllegalStateException("Capture journal unavailable");
            var sources = copied.stream().map(entry -> new SubLevelCaptureJournal.Source(
                    entry.position(), entry.state(), entry.tag())).toList();
            journalRecord = SubLevelCaptureJournal.begin(journal, host,
                    new BlockPos(minX, minY, minZ), id, sources);
            var bodyPose = new PhysicsPose(new PhysicsVector(minX, minY, minZ), PhysicsQuaternion.IDENTITY);
            runtime.createInLease(id, lease, bounds, bodyPose, geometry.boxes());
            for (var entry : copied) {
                if (!host.setBlock(entry.position(), Blocks.AIR.defaultBlockState(), TRANSACTION_FLAGS)) {
                    throw new IllegalStateException("Could not remove captured source block " + entry.position());
                }
                removed.add(entry);
            }
            org.academy.internal.common.structure.StructureHostBlockUpdates.publish(host,
                    copied.stream().map(Copied::position).toList());
            runtime.persist();
            SubLevelCaptureJournal.commit(journal, journalRecord);
            SubLevelStaticizeJournal.finish(journal, journalRecord.transactionId());
            var blockEntities = (int) copied.stream().filter(entry -> entry.tag() != null).count();
            return new Result(id, lease.plot(), copied.size(), geometry.boxes().size(), blockEntities,
                    new Vec3(minX, minY, minZ));
        } catch (RuntimeException | Error | java.io.IOException failure) {
            // A failed source restore leaves the plot and journal as the remaining recovery copy.
            // Completed rollbacks must retire their journal before subsequent source edits can
            // accidentally be overwritten by startup recovery.
            boolean restored = restore(host, removed);
            if (restored) {
                try {
                    if (id != null && runtime.get(id).isPresent()) runtime.remove(id);
                    else runtime.storage().release(lease);
                    if (journalRecord != null) {
                        runtime.persist();
                        journal.delete(journalRecord.transactionId());
                    }
                } catch (RuntimeException | java.io.IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                    AcademyCraft.LOGGER.error("SubLevel capture rollback requires journal recovery for {}", id, cleanupFailure);
                }
            } else {
                AcademyCraft.LOGGER.error("SubLevel capture source rollback incomplete; retaining plot {} and journal for {}",
                        lease.plot(), id);
            }
            if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("Capture journal failed", failure);
        }
    }

    private static boolean restore(ServerLevel host, List<Copied> entries) {
        boolean complete = true;
        for (var entry : entries) {
            try {
                if (!host.setBlock(entry.position(), entry.state(), TRANSACTION_FLAGS)
                        && host.getBlockState(entry.position()) != entry.state()) {
                    throw new IllegalStateException("Could not restore captured block state");
                }
                if (entry.tag() != null) {
                    var restored = BlockEntity.loadStatic(entry.position(), entry.state(), entry.tag(),
                            host.registryAccess());
                    if (restored == null) throw new IllegalStateException("Could not reload block entity");
                    host.setBlockEntity(restored);
                    restored.setChanged();
                }
            } catch (RuntimeException failure) {
                complete = false;
                AcademyCraft.LOGGER.error("Could not restore captured block {}", entry.position(), failure);
            }
        }
        org.academy.internal.common.structure.StructureHostBlockUpdates.publish(host,
                entries.stream().map(Copied::position).toList());
        return complete;
    }

    public record Result(SubLevelId id, PlotPos plot, int blocks, int boxes, int blockEntities, Vec3 origin) {
        public Result {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(plot, "plot");
            Objects.requireNonNull(origin, "origin");
        }
    }

    private record Copied(BlockPos position, BlockState state, CompoundTag tag) {
    }
}
