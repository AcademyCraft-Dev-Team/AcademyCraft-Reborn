package org.academy.internal.common.structure.sable;

import org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.academy.api.common.structure.*;

import java.util.Objects;
import java.util.UUID;

/** Read-only view of one exact, already loaded Sable activation. */
final class SableStructureHandle implements StructureHandle, StructureWorldAccess {
    private final ServerLevel host;
    private final ServerSubLevel subLevel;
    private final StructureRef reference;
    private final UUID runtimeId = UUID.randomUUID();
    private final BlockPos plotOrigin;
    private boolean invalidated;
    private long revision;
    private long contentRevision;
    private long poseRevision;
    private StructurePose observedPose;

    SableStructureHandle(ServerLevel host, ServerSubLevel subLevel) {
        this.host = host;
        this.subLevel = subLevel;
        reference = new StructureRef(subLevel.getUniqueId(), host.dimension().identifier(), SableStructureAccess.ID, 0);
        plotOrigin = subLevel.getPlot().getCenterBlock().immutable();
    }

    ServerLevel host() { requireThread(); return host; }
    ServerSubLevel subLevel() { requireActive(); return subLevel; }
    BlockPos plotOrigin() { requireThread(); return plotOrigin; }
    boolean refersTo(SubLevel candidate) { requireThread(); return subLevel == candidate; }

    boolean isCurrent() {
        requireThread();
        var container = SubLevelContainer.getContainer(host);
        return !invalidated && host.getServer().getLevel(host.dimension()) == host
                && container != null && container.getSubLevel(reference.id()) == subLevel
                && !subLevel.isRemoved() && subLevel.getLevel() == host
                && reference.id().equals(subLevel.getUniqueId())
                && plotOrigin.equals(subLevel.getPlot().getCenterBlock());
    }

    void invalidate() { requireThread(); invalidated = true; }
    void contentChanged() {
        requireThread();
        revision = Math.incrementExact(revision);
        contentRevision = Math.incrementExact(contentRevision);
    }

    long contentRevision() { requireActive(); return contentRevision; }
    long poseRevision() { requireActive(); pose(); return poseRevision; }

    @Override public StructureRef reference() { requireThread(); return reference; }
    @Override public UUID runtimeId() { requireThread(); return runtimeId; }
    @Override public StructureLifecycle lifecycle() {
        return isCurrent() ? StructureLifecycle.ACTIVE : StructureLifecycle.UNLOADED;
    }

    @Override public long geometryRevision() {
        requireActive();
        pose();
        return revision;
    }

    @Override public StructurePose pose() {
        requireActive();
        var current = SableStructureTransforms.pose(subLevel.logicalPose(), plotOrigin);
        if (observedPose == null) observedPose = current;
        else if (!observedPose.equals(current)) {
            observedPose = current;
            revision = Math.incrementExact(revision);
            poseRevision = Math.incrementExact(poseRevision);
        }
        return current;
    }

    @Override public void setPose(StructurePose pose) {
        requireActive();
        Objects.requireNonNull(pose, "pose");
        throw new IllegalStateException("Sable owns this body's physics; direct pose writes are unsupported");
    }

    @Override public StructureWorldAccess world() { requireThread(); return this; }

    @Override public StructureBlockData read(BlockPos localPosition) {
        requireActive();
        var position = checkedPlotPosition(localPosition);
        var plot = subLevel.getPlot();
        var holder = plot.getChunkHolder(plot.toLocal(new ChunkPos(position.getX() >> 4, position.getZ() >> 4)));
        if (holder == null) {
            var bounds = plot.getBoundingBox();
            if (bounds != null && position.getX() >= bounds.minX() && position.getX() <= bounds.maxX()
                    && position.getY() >= bounds.minY() && position.getY() <= bounds.maxY()
                    && position.getZ() >= bounds.minZ() && position.getZ() <= bounds.maxZ()) {
                throw new IllegalStateException("Sable structure content is unavailable at " + localPosition);
            }
            return new StructureBlockData(Blocks.AIR.defaultBlockState());
        }
        var chunk = holder.getChunk();
        var state = chunk.getBlockState(position);
        var entity = chunk.getBlockEntity(position);
        var tag = entity == null ? null : entity.saveWithFullMetadata(host.registryAccess());
        // Public values never expose the private storage address, including BE coordinates.
        if (tag != null) {
            tag.putInt("x", localPosition.getX());
            tag.putInt("y", localPosition.getY());
            tag.putInt("z", localPosition.getZ());
        }
        requireActive();
        return new StructureBlockData(state, tag);
    }

    @Override public StructureWriteResult compareAndSet(BlockPos localPosition, StructureBlockData expected,
                                                        StructureBlockData replacement) {
        requireThread();
        Objects.requireNonNull(localPosition, "localPosition");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(replacement, "replacement");
        if (!isCurrent()) return StructureWriteResult.NOT_READY;
        // The Sable bridge deliberately exposes no unjournalled content mutation.
        return StructureWriteResult.REJECTED;
    }

    private BlockPos checkedPlotPosition(BlockPos localPosition) {
        Objects.requireNonNull(localPosition, "localPosition");
        final BlockPos position;
        try { position = SableStructureTransforms.plotPosition(plotOrigin, localPosition); }
        catch (ArithmeticException overflow) { throw new IllegalArgumentException("Cell is outside the Sable plot", overflow); }
        if (host.isOutsideBuildHeight(position)
                || !subLevel.getPlot().contains(position.getX(), position.getZ())) {
            throw new IllegalArgumentException("Cell is outside the Sable plot");
        }
        return position;
    }

    private void requireActive() {
        if (!isCurrent()) throw new IllegalStateException("Sable structure activation is no longer loaded");
    }

    private void requireThread() { SableStructureAccess.requireThread(host.getServer()); }

}
