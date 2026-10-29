package org.academy.internal.common.structure.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel;
import org.academy.internal.common.structure.transaction.StructureCellPayload;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;

import java.util.*;

/** Exact loaded split witnesses; uncertain mappings retain the ordinary missing-support behavior. */
final class SableNozzleFractures {
    private static final int MAX_NOZZLES = 64;
    private SableNozzleFractures() {}

    record Mount(HighSpeedJetNozzle nozzle, UUID owner, HighSpeedJetNozzle.StructureMount expected,
                 BlockPos plotOrigin, BlockPos storagePosition, Vec3 center, Vec3 normal, StructureBlockData payload) {}

    static List<Mount> capture(ServerLevel host, ServerSubLevel parent, StructureRef reference) {
        if (parent == null || reference == null) return List.of();
        try {
            var handle = SableStructureAccess.resolveHandle(host.getServer(), reference).orElse(null);
            var box = parent.getPlot().getBoundingBox();
            if (handle == null || box == null) return List.of();
            var area = SableStructureTransforms.worldBounds(parent.logicalPose(), new AABB(
                    box.minX(), box.minY(), box.minZ(), box.maxX()+1.0, box.maxY()+1.0, box.maxZ()+1.0));
            if (area.getXsize() > 128 || area.getYsize() > 128 || area.getZsize() > 128) return List.of();
            var candidates = new ArrayList<HighSpeedJetNozzle>();
            int[] visited = {0};
            ((org.academy.mixin.common.ServerLevelEntityManagerAccessor)host).academy$getEntityManager().getEntityGetter()
                    .get(net.minecraft.world.level.entity.EntityTypeTest.forClass(HighSpeedJetNozzle.class), area.inflate(32), nozzle -> {
                        if (++visited[0] > 256) return net.minecraft.util.AbortableIterationConsumer.Continuation.ABORT;
                        if (nozzle.structureMount().map(value -> same(value.reference(), reference)).orElse(false)) candidates.add(nozzle);
                        return candidates.size() > MAX_NOZZLES ? net.minecraft.util.AbortableIterationConsumer.Continuation.ABORT
                                : net.minecraft.util.AbortableIterationConsumer.Continuation.CONTINUE;
                    });
            if (candidates.size() > MAX_NOZZLES || visited[0] > 256) return List.of();
            var result = new ArrayList<Mount>();
            var images = new HashMap<BlockPos, StructureBlockData>();
            long bytes = 0;
            var pose = handle.pose();
            for (var nozzle : candidates) {
                var mount = nozzle.structureMount().orElseThrow();
                var image = images.get(mount.offset());
                if (image == null) {
                    image = StructureCellPayload.normalize(handle.world().read(mount.offset()));
                    var tag = image.blockEntityData(); bytes += tag == null ? 0 : tag.sizeInBytes();
                    if (bytes > 4_194_304) break;
                    images.put(mount.offset(), image);
                }
                if (image.state().isAir()) continue;
                result.add(new Mount(nozzle, nozzle.ownerUuid(), mount, handle.plotOrigin(), handle.plotOrigin().offset(mount.offset()),
                        pose.localToWorldPoint(Vec3.atCenterOf(mount.offset())),
                        pose.localToWorldDirection(Vec3.atLowerCornerOf(mount.face().getUnitVec3i())), image));
            }
            return List.copyOf(result);
        } catch (RuntimeException unavailable) { return List.of(); }
    }

    static void moved(ServerLevel host, ServerSubLevel parent, ServerSubLevel child, List<Mount> mounts) {
        if (mounts.isEmpty()) return;
        var handle = SableStructureAccess.resolveHandle(host.getServer(), SableProvenanceLifecycle.reference(child)).orElse(null);
        if (handle == null) return;
        for (var snapshot : mounts) {
            try {
                if (snapshot.nozzle().isRemoved() || !Objects.equals(snapshot.owner(), snapshot.nozzle().ownerUuid())
                        || !snapshot.nozzle().structureMount().filter(snapshot.expected()::equals).isPresent()
                        || !sourceMoved(parent, snapshot)) continue;
                var local = handle.pose().worldToLocalPoint(snapshot.center());
                var offset = BlockPos.containing(local);
                if (local.distanceToSqr(Vec3.atCenterOf(offset)) > 1E-12) continue;
                var normal = handle.pose().worldToLocalDirection(snapshot.normal());
                Direction face = null;
                for (var candidate : Direction.values()) if (normal.distanceToSqr(Vec3.atLowerCornerOf(candidate.getUnitVec3i())) < 1E-12) face = candidate;
                if (face == null || !StructureCellPayload.normalize(handle.world().read(offset)).equals(snapshot.payload())) continue;
                snapshot.nozzle().rebindFragment(snapshot.expected(), snapshot.owner(), handle.reference(), offset, face);
            } catch (RuntimeException uncertain) { /* No guessed replacement, ownership or activation. */ }
        }
    }

    private static boolean sourceMoved(ServerSubLevel parent, Mount snapshot) {
        var plot = parent.getPlot();
        if (!plot.getCenterBlock().equals(snapshot.plotOrigin())) return false;
        var box = plot.getBoundingBox();
        if (parent.isRemoved()) return box == null || box.minX() > box.maxX() || box.minY() > box.maxY() || box.minZ() > box.maxZ();
        var container = org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer.getContainer(parent.getLevel());
        if (container == null || container.getSubLevel(parent.getUniqueId()) != parent) return false;
        var holder = plot.getChunkHolder(plot.toLocal(new ChunkPos(snapshot.storagePosition().getX() >> 4, snapshot.storagePosition().getZ() >> 4)));
        return holder != null && holder.getChunk().getBlockState(snapshot.storagePosition()).isAir()
                && !holder.getChunk().getBlockEntities().containsKey(snapshot.storagePosition());
    }

    private static boolean same(StructureRef first, StructureRef second) {
        return first.id().equals(second.id()) && first.backend().equals(second.backend()) && first.dimension().equals(second.dimension());
    }
}
