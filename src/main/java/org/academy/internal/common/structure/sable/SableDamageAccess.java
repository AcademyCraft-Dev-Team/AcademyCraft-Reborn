package org.academy.internal.common.structure.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer;
import org.academy.internal.common.structure.upgrade.damage.DamageDomain;

import java.util.ArrayList;
import java.util.List;

/** Optional Sable bridge; plot blocks are edited through the ordinary loaded world callbacks. */
public final class SableDamageAccess {
    private SableDamageAccess() {}

    public static List<DamageDomain> nearby(ServerLevel host, AABB area, int limit) {
        var container = SubLevelContainer.getContainer(host);
        if (container == null) return List.of();
        var result = new ArrayList<DamageDomain>();
        int scanned = 0;
        for (var body : container.getAllSubLevels()) {
            if (++scanned > 4096) throw new IllegalStateException("damage_body_scan_budget");
            if (body.isRemoved() || body.getLevel() != host) continue;
            var bounds = body.getPlot().getBoundingBox();
            if (bounds == null || bounds.minX() > bounds.maxX() || bounds.minY() > bounds.maxY() || bounds.minZ() > bounds.maxZ()) continue;
            var plotBox = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
            if (!SableStructureTransforms.worldBounds(body.logicalPose(), plotBox).intersects(area)) continue;
            if (!SableStructureTransforms.unitScale(body.logicalPose()) || !body.getPlot().getContraptions().isEmpty()) {
                throw new IllegalStateException("damage_unsupported_sable_geometry");
            }
            var ref = new StructureRef(body.getUniqueId(), host.dimension().identifier(), SableStructureAccess.ID, 0);
            var handle = SableStructureAccess.resolveHandle(host.getServer(), ref).orElse(null);
            if (handle == null) continue; // restoration/recovery owns hidden sources
            if (result.size() >= limit) throw new IllegalStateException("damage_body_budget");
            var domain = new Domain(handle, body, plotBox.move(Vec3.atLowerCornerOf(handle.plotOrigin()).scale(-1)));
            if (!domain.transactionPending()) result.add(domain);
        }
        return List.copyOf(result);
    }

    private record Domain(SableStructureHandle sable,
                          org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel body,
                          AABB localBounds) implements DamageDomain {
        public StructureHandle handle() { return sable; }
        public ServerLevel storage() { return sable.host(); }
        public BlockPos origin() { return sable.plotOrigin(); }
        public boolean current() {
            return sable.isCurrent() && SableStructureAccess.resolveHandle(storage().getServer(), sable.reference())
                    .filter(value -> value == sable).isPresent() && !transactionPending();
        }
        public long contentRevision() { return sable.contentRevision(); }
        public boolean retiringEmptyCell(BlockPos local, org.academy.api.common.structure.StructurePose pose) {
            // Sable marks an empty plot removed inside the last palette update, before its
            // container releases the plot next tick. Never read an unloaded or reused address.
            var host = storage();
            var container = SubLevelContainer.getContainer(host);
            var plot = body.getPlot();
            if (container == null || host.getServer().getLevel(host.dimension()) != host
                    || container.getSubLevel(sable.reference().id()) != body || !body.isRemoved()
                    || body.getLevel() != host || !origin().equals(plot.getCenterBlock()) || transactionPending()
                    || !pose.equals(SableStructureTransforms.pose(body.logicalPose(), origin()))) return false;
            var bounds = plot.getBoundingBox();
            if (bounds == null || bounds.minX() <= bounds.maxX() && bounds.minY() <= bounds.maxY()
                    && bounds.minZ() <= bounds.maxZ()) return false;
            var position = position(local);
            if (!plot.contains(position.getX(), position.getZ())) return false;
            var holder = plot.getChunkHolder(plot.toLocal(new net.minecraft.world.level.ChunkPos(position.getX() >> 4, position.getZ() >> 4)));
            if (holder == null) return false;
            var chunk = holder.getChunk();
            var state = chunk.getBlockState(position);
            return state.isAir() && state.getFluidState().isEmpty() && !chunk.getBlockEntities().containsKey(position);
        }
        public net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable LevelChunk chunk(BlockPos position) {
            if (!current()) return null;
            var plot = sable.subLevel().getPlot();
            if (!plot.contains(position.getX(), position.getZ())) return null;
            var holder = plot.getChunkHolder(plot.toLocal(new net.minecraft.world.level.ChunkPos(position.getX() >> 4, position.getZ() >> 4)));
            return holder == null ? null : holder.getChunk();
        }
        public void changed() { sable.contentChanged(); }
        public void flush() { /* Sable's block-change observer owns mass, geometry, splitting and sync. */ }
    }
}
