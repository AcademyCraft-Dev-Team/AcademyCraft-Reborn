package org.academy.internal.common.structure.sable;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.transaction.CaptureTarget;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

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
    public Optional<StructureRef> allocate(ServerLevel host, BlockPos origin) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(origin, "origin");
        SableStructureAccess.requireThread(server);
        var container = SubLevelContainer.getContainer(host);
        if (container == null) return Optional.empty();
        ServerSubLevel ship = null;
        try {
            // The rotation point is only known after the plot is assigned, so the ship starts
            // with a placeholder anchor and is re-anchored before the first cell write. The
            // final pair below yields transform(p) = p + (origin - plotOrigin) for every plot
            // position p with unit scale and identity rotation, so local (0,0,0) = plot origin
            // maps exactly onto the world origin of the native capture grid.
            var position = new Vector3d(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
            var pose = new Pose3d(position, new Quaterniond(), new Vector3d(), new Vector3d(1.0, 1.0, 1.0));
            var allocated = container.allocateNewSubLevel(pose);
            if (!(allocated instanceof ServerSubLevel serverSubLevel)) return Optional.empty();
            ship = serverSubLevel;
            var plot = ship.getPlot();
            var plotOrigin = plot.getCenterBlock();
            ship.logicalPose().rotationPoint().set(plotOrigin.getX() + 0.5, plotOrigin.getY() + 0.5,
                    plotOrigin.getZ() + 0.5);
            prepareChunks(plot, plotOrigin);
            return Optional.of(new StructureRef(ship.getUniqueId(), host.dimension().identifier(),
                    StructureBackendRouter.SABLE_ID, 0));
        } catch (RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.error("Sable capture allocation failed for origin {}", origin, failure);
            if (ship != null) removeQuietly(container, ship);
            return Optional.empty();
        }
    }

    @Override
    public int cellBudget() {
        return StructureCaptureRequest.MAXIMUM_BLOCKS;
    }

    @Override
    public boolean ready(StructureRef target) {
        return ship(target) != null;
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
     * transactional save result and reports its own failures through its logs; this adapter
     * treats any thrown failure as an unconfirmed checkpoint so the coordinator retains the
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
            AcademyCraft.LOGGER.error("Sable capture checkpoint failed for {}", target.id(), failure);
            return false;
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
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        } catch (RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.error("Sable capture could not remove target {}", target.id(), failure);
            return false;
        }
        return container.getSubLevel(target.id()) == null;
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
        try {
            container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
        } catch (RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.error("Sable capture could not remove a failed allocation", failure);
        }
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
