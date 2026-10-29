package org.academy.internal.common.ability.teleport.chunk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureTeleports;
import org.academy.internal.common.ability.teleport.targeting.TeleportStructures;
import org.academy.internal.common.structure.upgrade.control.TranslationSweep;
import org.academy.internal.common.structure.teleport.LiveStructureTeleport;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.api.common.structure.StructureRef;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Whole rigid bodies may travel with one atomic chunk pair; partial ownership is refused. */
final class ChunkStructureSwap {
    private ChunkStructureSwap() {}

    static final class Refusal extends IllegalArgumentException {
        private final String translationKey;
        Refusal(String translationKey) { super(translationKey); this.translationKey = translationKey; }
        String translationKey() { return translationKey; }
    }

    static List<StructureTeleports.Request> plan(ServerLevel levelA, ChunkPos chunkA,
            ServerLevel levelB, ChunkPos chunkB, ChunkVerticalBand band, Vec3 offset) {
        var result = new ArrayList<StructureTeleports.Request>();
        collect(levelA, chunkA, band, offset, result);
        collect(levelB, chunkB, band, offset.scale(-1), result);
        if (!result.isEmpty() && levelA != levelB) throw new Refusal("chunk_leap.reason.structure_dimension");
        if (result.size() > 8) throw new Refusal("chunk_leap.reason.structure_budget");
        return List.copyOf(result);
    }

    private static void collect(ServerLevel host, ChunkPos chunk, ChunkVerticalBand band, Vec3 offset,
                                List<StructureTeleports.Request> result) {
        var selection = new AABB(chunk.getMinBlockX(), band.minBlockY(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1., band.maxBlockY() + 1., chunk.getMaxBlockZ() + 1.);
        for (var body : TeleportStructures.intersecting(host, selection)) {
            var bounds = body.localBoxes().stream().map(box -> TranslationSweep.Box.of(box, body.motion().pose()).bounds())
                    .reduce(AABB::minmax).orElseThrow();
            if (!bounds.intersects(selection)) continue;
            if (bounds.minX < selection.minX || bounds.maxX > selection.maxX
                    || bounds.minY < selection.minY || bounds.maxY > selection.maxY
                    || bounds.minZ < selection.minZ || bounds.maxZ > selection.maxZ) {
                throw new Refusal("chunk_leap.reason.structure_partial_chunk");
            }
            var center = body.motion().pose().localToWorldPoint(body.motion().localCenterOfMass());
            result.add(new StructureTeleports.Request(body.reference(), body.runtimeId(), center.add(offset), null));
        }
    }

    static java.util.Map<StructureRef, LiveStructureTeleport.CarriedTerrain> captureTerrain(ServerLevel host, List<StructureTeleports.Request> moves) {
        var result = new java.util.HashMap<StructureRef, LiveStructureTeleport.CarriedTerrain>();
        for (var request : moves) {
            var body = StructureBackendRouter.orientationAccess(host, request.reference()).orElseThrow()
                    .observe(host, request.reference()).orElseThrow();
            var center = body.motion().pose().localToWorldPoint(body.motion().localCenterOfMass());
            var offset = request.destinationCenter().subtract(center);
            var bounds = body.localBoxes().stream().map(box -> TranslationSweep.Box.of(box, body.motion().pose()).bounds())
                    .reduce(AABB::minmax).orElseThrow().inflate(1E-8);
            var shapes = new java.util.HashSet<AABB>();
            var min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
            var max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
            long cells = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
            if (cells > LiveStructureTeleport.MAX_CELLS) throw new IllegalArgumentException("Carried contact observation exceeds budget");
            for (var pos : BlockPos.betweenClosed(min, max)) {
                if (host.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                    throw new IllegalArgumentException("Carried terrain observation is not loaded");
                }
                for (var box : host.getBlockState(pos).getCollisionShape(host, pos).toAabbs()) shapes.add(box.move(pos).move(offset));
            }
            result.put(request.reference(), new LiveStructureTeleport.CarriedTerrain(body.runtimeId(), body.motion().pose(), offset, shapes));
        }
        return java.util.Map.copyOf(result);
    }

    static void move(ServerLevel host, ServerPlayer actor, List<StructureTeleports.Request> moves,
                     java.util.Map<StructureRef, LiveStructureTeleport.CarriedTerrain> terrain) {
        if (moves.isEmpty()) return;
        if (actor == null || actor.level() != host) throw new IllegalArgumentException("Physical structure chunk actor unavailable");
        var result = LiveStructureTeleport.teleportCarryingTerrain(host, actor, moves, terrain);
        if (result != StructureTeleports.Result.TELEPORTED) throw new Refusal(result == StructureTeleports.Result.BUDGET_EXCEEDED
                ? "chunk_leap.reason.structure_budget" : "chunk_leap.reason.structure_unsafe");
    }
}
