package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
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
 * Energy spheres are always read from the overworld wireless SavedData.
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

    private record FootprintKey(ResourceKey<Level> dimension, long networkId) {
    }

    private static final Map<FootprintKey, List<Sphere>> FOOTPRINT_CACHE = new HashMap<>();

    private MisakaNetworkCoverage() {
    }

    public static void invalidate() {
        FOOTPRINT_CACHE.clear();
    }

    /**
     * Every wireless node whose {@link WirelessForwardingMisakaNAT#resolveNetworkIdRaw}
     * equals {@code networkId}, each with its configured radius sphere.
     * {@code level} should be the energy-home level (overworld for Misaka).
     */
    public static List<Sphere> footprint(ServerLevel level, BlockPos networkId) {
        if (level == null || networkId == null) {
            return List.of();
        }
        var key = new FootprintKey(level.dimension(), networkId.asLong());
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

    public static boolean isInEnergyCoverage(ServerLevel energyHome, BlockPos networkId, BlockPos pos) {
        if (pos == null || energyHome == null) {
            return false;
        }
        for (var sphere : footprint(energyHome, networkId)) {
            if (sphere.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Energy spheres apply only when the sample is in the energy-home dimension (overworld).
     * Otherwise access is granted only via relay satellites for the sample dimension.
     */
    public static boolean canUseMisakaService(ServerLevel sampleLevel, BlockPos networkId, BlockPos pos) {
        if (sampleLevel == null) {
            return resolveServiceAccess(false, false, MisakaRelayAccess.get().grantsAccess(null, pos, networkId));
        }
        boolean sampleInEnergyHome = false;
        boolean inEnergy = false;
        var server = sampleLevel.getServer();
        if (server != null) {
            var energyHome = server.overworld();
            sampleInEnergyHome = sampleLevel.dimension().equals(energyHome.dimension());
            if (sampleInEnergyHome) {
                inEnergy = isInEnergyCoverage(energyHome, networkId, pos);
            }
        }
        return resolveServiceAccess(
                sampleInEnergyHome,
                inEnergy,
                MisakaRelayAccess.get().grantsAccess(sampleLevel, pos, networkId)
        );
    }

    /**
     * Pure coverage decision used by {@link #canUseMisakaService} (and unit tests).
     * Energy spheres win only when the sample is in the energy-home dimension and inside a footprint.
     */
    public static boolean resolveServiceAccess(
            boolean sampleInEnergyHome,
            boolean inEnergyFootprint,
            boolean relayGrants
    ) {
        return (sampleInEnergyHome && inEnergyFootprint) || relayGrants;
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
     * Resolve the level to use for coverage sampling (loaded entity dim, else lastKnownDimension).
     */
    public static ServerLevel sampleLevel(
            MinecraftServer server,
            MisakaSisterRecord record,
            @Nullable MisakaSisterEntity entity
    ) {
        if (entity != null && !entity.isRemoved()) {
            return (ServerLevel) entity.level();
        }
        ResourceKey<Level> key = record != null && record.lastKnownDimension != null
                ? record.lastKnownDimension
                : Level.OVERWORLD;
        var level = server.getLevel(key);
        return level != null ? level : server.overworld();
    }

    /**
     * Find a loaded sister by UUID across all dimensions (rebuild / manage only; not hot path).
     */
    public static @Nullable MisakaSisterEntity findLoadedSister(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof MisakaSisterEntity sister && !sister.isRemoved()) {
                return sister;
            }
        }
        return null;
    }

    /** One pass over loaded Misaka sister entities in {@code level}. Prefer {@link #findLoadedSister} for cross-dim. */
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
