package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.misaka.MisakaRelayAccess;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Academy City energy-network footprint for a Misaka network topology component.
 * Footprints are lazily cached and cleared on topology / radius invalidation.
 */
public final class MisakaNetworkCoverage {
    public record Sphere(BlockPos nodePos, double radiusSqr) {
        public Sphere {
            nodePos = nodePos == null ? BlockPos.ZERO : nodePos.immutable();
            radiusSqr = Math.max(0.0, radiusSqr);
        }

        public boolean contains(BlockPos pos) {
            return pos != null && nodePos.distSqr(pos) <= radiusSqr;
        }
    }

    private static final Map<Long, List<Sphere>> FOOTPRINT_CACHE = new HashMap<>();

    private MisakaNetworkCoverage() {
    }

    public static void invalidate() {
        FOOTPRINT_CACHE.clear();
    }

    /**
     * Every wireless node whose {@link WirelessForwardingMisakaNAT#resolveNetworkIdRaw}
     * equals {@code networkId}, each with its configured radius sphere.
     */
    public static List<Sphere> footprint(ServerLevel level, BlockPos networkId) {
        if (level == null || networkId == null) {
            return List.of();
        }
        var key = networkId.asLong();
        var cached = FOOTPRINT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        var data = WirelessNetworkData.get(level);
        var immutableNetwork = networkId.immutable();
        var spheres = new ArrayList<Sphere>();
        for (var entry : data.getAllNodes().entrySet()) {
            var nodePos = entry.getKey();
            var resolved = WirelessForwardingMisakaNAT.resolveNetworkIdRaw(level, nodePos);
            if (!resolved.equals(immutableNetwork)) {
                continue;
            }
            double r = Math.max(0, entry.getValue().radius);
            spheres.add(new Sphere(nodePos.immutable(), r * r));
        }
        var built = List.copyOf(spheres);
        FOOTPRINT_CACHE.put(key, built);
        return built;
    }

    public static boolean isInEnergyCoverage(ServerLevel level, BlockPos networkId, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        for (var sphere : footprint(level, networkId)) {
            if (sphere.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean canUseMisakaService(ServerLevel level, BlockPos networkId, BlockPos pos) {
        if (isInEnergyCoverage(level, networkId, pos)) {
            return true;
        }
        return MisakaRelayAccess.get().grantsAccess(level, pos, networkId);
    }

    /**
     * Sample position for coverage checks.
     * Loaded entity uses block pos; unloaded uses chunk center XZ + bound node Y;
     * missing both falls back to bound node (in coverage of at least that node).
     */
    public static BlockPos samplePos(MisakaSisterRecord record, @Nullable Entity entity) {
        if (entity != null && !entity.isRemoved()) {
            return entity.blockPosition();
        }
        if (record == null) {
            return BlockPos.ZERO;
        }
        if (record.lastKnownChunk != null) {
            ChunkPos chunk = record.lastKnownChunk;
            int y = record.networkNodePos != null ? record.networkNodePos.getY() : 64;
            return new BlockPos(chunk.getMiddleBlockX(), y, chunk.getMiddleBlockZ());
        }
        if (record.networkNodePos != null) {
            return record.networkNodePos.immutable();
        }
        return BlockPos.ZERO;
    }

    /**
     * One pass over loaded Misaka sister entities in {@code level} (for index rebuild / manage UI).
     */
    public static Map<UUID, MisakaSisterEntity> loadedSistersByUuid(ServerLevel level) {
        if (level == null) {
            return Map.of();
        }
        var border = level.getWorldBorder();
        var box = new AABB(
                border.getMinX(),
                level.dimensionType().minY(),
                border.getMinZ(),
                border.getMaxX(),
                level.dimensionType().minY() + level.dimensionType().logicalHeight(),
                border.getMaxZ()
        );
        var map = new HashMap<UUID, MisakaSisterEntity>();
        for (var sister : level.getEntitiesOfClass(MisakaSisterEntity.class, box)) {
            var uuid = sister.getMisakaUuid();
            if (uuid != null) {
                map.put(uuid, sister);
            }
        }
        return map;
    }
}
