package org.academy.internal.server.world.level.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.misaka.RelaySatelliteEntity;
import org.academy.internal.server.misaka.MisakaRelayOrbits;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Launch / retarget / rebind / crash / spawn lifecycle for {@link MisakaRelayRegistry}.
 */
final class MisakaRelayLifecycle {
    private final MisakaRelayRegistry registry;

    MisakaRelayLifecycle(MisakaRelayRegistry registry) {
        this.registry = registry;
    }

    boolean launch(
            MinecraftServer server,
            BlockPos networkId,
            ResourceKey<Level> dimension,
            boolean hyper,
            BlockPos laserPos,
            ResourceKey<Level> laserDimension,
            BlockPos cabinPos,
            ResourceKey<Level> cabinDimension
    ) {
        if (server == null || networkId == null || dimension == null || laserPos == null || laserDimension == null || cabinPos == null || cabinDimension == null) {
            return false;
        }
        long lk = MisakaRelayRegistry.laserKey(laserDimension, laserPos);
        if (registry.laserOwner.containsKey(lk)) {
            return false;
        }
        UUID satelliteId = UUID.randomUUID();
        var entry = new MisakaRelayEntry(
                satelliteId,
                networkId,
                dimension,
                hyper,
                laserPos,
                laserDimension,
                cabinPos,
                cabinDimension,
                null,
                MisakaRelayEntry.Phase.LAUNCHING,
                true
        );
        registry.byId.put(satelliteId, entry);
        registry.laserOwner.put(lk, satelliteId);
        registry.markPersistentDirty();
        spawnEntity(server, entry, true);
        registry.markComputeDirty(server);
        return true;
    }

    boolean retargetNetwork(MinecraftServer server, UUID satelliteId, BlockPos newNetworkId) {
        var entry = registry.get(satelliteId);
        if (entry == null || newNetworkId == null || !entry.phase.isActive()) {
            return false;
        }
        if (entry.networkId.equals(newNetworkId.immutable())) {
            return true;
        }
        boolean wasPowered = entry.powered;
        if (wasPowered) {
            registry.power.bumpPoweredCount(entry, -1);
        }
        entry.networkId = newNetworkId.immutable();
        if (wasPowered) {
            registry.power.bumpPoweredCount(entry, +1);
        }
        registry.markPersistentDirty();
        registry.markComputeDirty(server);
        return true;
    }

    /**
     * Rebind an orbiting satellite to a free energy laser tower (e.g. after its previous tower was destroyed).
     * Coverage network is unchanged; orbit anchor moves to the new tower.
     */
    boolean rebindLaser(
            MinecraftServer server,
            UUID satelliteId,
            BlockPos newLaserPos,
            ResourceKey<Level> newLaserDimension
    ) {
        var entry = registry.get(satelliteId);
        if (entry == null || newLaserPos == null || newLaserDimension == null || !entry.phase.isActive()) {
            return false;
        }
        long newKey = MisakaRelayRegistry.laserKey(newLaserDimension, newLaserPos.immutable());
        UUID occupying = registry.laserOwner.get(newKey);
        if (occupying != null && !occupying.equals(satelliteId)) {
            return false;
        }
        if (entry.laserBound
                && entry.laserDimension.equals(newLaserDimension)
                && entry.laserPos.equals(newLaserPos.immutable())) {
            return true;
        }
        if (entry.laserBound) {
            registry.laserOwner.remove(MisakaRelayRegistry.laserKey(entry.laserDimension, entry.laserPos));
        }
        entry.laserPos = newLaserPos.immutable();
        entry.laserDimension = newLaserDimension;
        entry.laserBound = true;
        registry.laserOwner.put(newKey, satelliteId);
        registry.markPersistentDirty();
        registry.markComputeDirty(server);
        moveOrbitAnchor(server, entry);
        return true;
    }

    /**
     * Launch ascent finished: satellite has reached its scheduled orbit slot.
     * Orbit phase is registry-only — discard any cosmetic launch entity.
     */
    void completeLaunch(MinecraftServer server, UUID satelliteId) {
        var entry = registry.get(satelliteId);
        if (entry == null || entry.phase != MisakaRelayEntry.Phase.LAUNCHING) {
            return;
        }
        entry.phase = MisakaRelayEntry.Phase.ORBIT;
        RelaySatelliteEntity entity = findEntity(server, entry);
        if (entity != null && !entity.isRemoved()) {
            entity.discard();
        }
        entry.entityUuid = null;
        registry.markPersistentDirty();
    }

    /**
     * Satellite's bound energy laser tower was destroyed: clear laser ownership and drop into the
     * normal unpowered countdown ({@code misakaRelayCrashTicks}). Stay in orbit until rebound or timeout crash.
     */
    void unbindLaser(MinecraftServer server, UUID satelliteId) {
        var entry = registry.get(satelliteId);
        if (entry == null || !entry.laserBound) {
            return;
        }
        registry.laserOwner.remove(MisakaRelayRegistry.laserKey(entry.laserDimension, entry.laserPos));
        entry.laserBound = false;
        if (entry.powered) {
            entry.powered = false;
            registry.power.bumpPoweredCount(entry, -1);
        }
        // Enter / continue the same unpowered countdown used when a laser stops feeding.
        registry.markPersistentDirty();
        registry.markComputeDirty(server);
    }

    void beginCrash(MinecraftServer server, UUID satelliteId) {
        var entry = registry.get(satelliteId);
        if (entry == null || entry.phase == MisakaRelayEntry.Phase.CRASHING) {
            return;
        }
        entry.forceCrashCountdownTicks = 0;
        if (entry.powered) {
            entry.powered = false;
            registry.power.bumpPoweredCount(entry, -1);
        }
        entry.phase = MisakaRelayEntry.Phase.CRASHING;
        registry.markPersistentDirty();
        registry.markComputeDirty(server);

        RelaySatelliteEntity entity = findEntity(server, entry);
        if (entity == null) {
            entity = spawnCrashEntity(server, entry);
        }
        if (entity != null) {
            entity.beginCrash();
        } else {
            completeCrash(server, satelliteId);
        }
    }

    void completeCrash(MinecraftServer server, UUID satelliteId) {
        var entry = registry.get(satelliteId);
        if (entry == null) {
            return;
        }
        if (entry.powered) {
            entry.powered = false;
            registry.power.bumpPoweredCount(entry, -1);
        }
        if (entry.laserBound) {
            registry.laserOwner.remove(MisakaRelayRegistry.laserKey(entry.laserDimension, entry.laserPos));
        }
        registry.byId.remove(satelliteId);
        registry.markPersistentDirty();
        registry.markComputeDirty(server);
        RelaySatelliteEntity entity = findEntity(server, entry);
        if (entity != null && !entity.isRemoved()) {
            entity.discard();
        }
    }

    void onLaserRemoved(MinecraftServer server, ResourceKey<Level> laserDim, BlockPos laserPos) {
        UUID id = registry.laserOwner.get(MisakaRelayRegistry.laserKey(laserDim, laserPos));
        if (id != null) {
            unbindLaser(server, id);
        }
    }

    void moveOrbitAnchor(MinecraftServer server, MisakaRelayEntry entry) {
        if (server == null) {
            return;
        }
        RelaySatelliteEntity entity = findEntity(server, entry);
        if (entity == null) {
            // ORBIT has no resident entity; LAUNCHING/CRASHING keep their own spawn paths.
            return;
        }
        var level = server.getLevel(entry.dimension);
        if (level == null) {
            return;
        }
        double orbitY = MisakaRelayOrbits.visualOrbitY(level, server);
        entity.setOrbitAnchor(entry.laserPos.getX() + 0.5, orbitY, entry.laserPos.getZ() + 0.5);
    }

    void tryRespawnMissing(MinecraftServer server) {
        for (var entry : registry.byId.values()) {
            if (entry.phase == MisakaRelayEntry.Phase.ORBIT) {
                continue;
            }
            if (entry.phase == MisakaRelayEntry.Phase.LAUNCHING) {
                if (findEntity(server, entry) == null) {
                    // Unload mid-ascent: snap to abstract orbit, do not replay launch or spawn.
                    entry.phase = MisakaRelayEntry.Phase.ORBIT;
                    entry.entityUuid = null;
                    registry.markPersistentDirty();
                }
                continue;
            }
            if (entry.phase == MisakaRelayEntry.Phase.CRASHING) {
                if (findEntity(server, entry) != null) {
                    continue;
                }
                var spawned = spawnCrashEntity(server, entry);
                if (spawned == null) {
                    completeCrash(server, entry.satelliteId);
                } else {
                    spawned.beginCrash();
                }
            }
        }
    }

    void spawnEntity(MinecraftServer server, MisakaRelayEntry entry, boolean withLaunchAnim) {
        var level = server.getLevel(entry.dimension);
        if (level == null) {
            return;
        }
        double orbitY = MisakaRelayOrbits.visualOrbitY(level, server);
        var entity = new RelaySatelliteEntity(level);
        entity.setSatelliteId(entry.satelliteId);
        entity.setOrbitAnchor(entry.laserPos.getX() + 0.5, orbitY, entry.laserPos.getZ() + 0.5);
        entity.setHyper(entry.hyper);

        if (withLaunchAnim) {
            var start = resolveLaunchStart(level, entry, orbitY);
            int chunkX = BlockPos.containing(start).getX() >> 4;
            int chunkZ = BlockPos.containing(start).getZ() >> 4;
            if (!level.hasChunk(chunkX, chunkZ)) {
                // Cannot play ascent — abstract orbit immediately, no resident entity.
                entry.phase = MisakaRelayEntry.Phase.ORBIT;
                entry.entityUuid = null;
                registry.markPersistentDirty();
                return;
            }
            entity.setPos(start.x, start.y, start.z);
            entity.beginLaunch();
        } else {
            // Non-launch spawns are only for crash recovery.
            int chunkX = entry.laserPos.getX() >> 4;
            int chunkZ = entry.laserPos.getZ() >> 4;
            if (!level.hasChunk(chunkX, chunkZ)) {
                return;
            }
            entity.setPos(
                    entry.laserPos.getX() + 0.5 + RelaySatelliteEntity.ORBIT_RADIUS,
                    orbitY,
                    entry.laserPos.getZ() + 0.5
            );
        }

        level.addFreshEntity(entity);
        entry.entityUuid = entity.getUUID();
        registry.markPersistentDirty();
    }

    @Nullable RelaySatelliteEntity spawnCrashEntity(MinecraftServer server, MisakaRelayEntry entry) {
        if (server == null) {
            return null;
        }
        var level = server.getLevel(entry.dimension);
        if (level == null) {
            return null;
        }
        int chunkX = entry.laserPos.getX() >> 4;
        int chunkZ = entry.laserPos.getZ() >> 4;
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        double orbitY = MisakaRelayOrbits.visualOrbitY(level, server);
        var entity = new RelaySatelliteEntity(level);
        entity.setSatelliteId(entry.satelliteId);
        entity.setOrbitAnchor(entry.laserPos.getX() + 0.5, orbitY, entry.laserPos.getZ() + 0.5);
        entity.setHyper(entry.hyper);
        entity.setPos(
                entry.laserPos.getX() + 0.5 + RelaySatelliteEntity.ORBIT_RADIUS,
                orbitY,
                entry.laserPos.getZ() + 0.5
        );
        level.addFreshEntity(entity);
        entry.entityUuid = entity.getUUID();
        registry.markPersistentDirty();
        return entity;
    }

    static Vec3 resolveLaunchStart(
            net.minecraft.server.level.ServerLevel level,
            MisakaRelayEntry entry,
            double orbitY
    ) {
        if (entry.cabinDimension.equals(level.dimension())) {
            var cabinChunkX = entry.cabinPos.getX() >> 4;
            var cabinChunkZ = entry.cabinPos.getZ() >> 4;
            if (level.hasChunk(cabinChunkX, cabinChunkZ)) {
                return new Vec3(
                        entry.cabinPos.getX() + 0.5,
                        entry.cabinPos.getY() + 2.0,
                        entry.cabinPos.getZ() + 0.5
                );
            }
        }
        double startY = Math.min(entry.laserPos.getY() + 2.0, orbitY - 32.0);
        startY = Math.max(startY, level.dimensionType().minY() + 8.0);
        return new Vec3(entry.laserPos.getX() + 0.5, startY, entry.laserPos.getZ() + 0.5);
    }

    static @Nullable RelaySatelliteEntity findEntity(MinecraftServer server, MisakaRelayEntry entry) {
        if (server == null || entry.entityUuid == null) {
            return null;
        }
        var level = server.getLevel(entry.dimension);
        if (level == null) {
            return null;
        }
        var entity = level.getEntity(entry.entityUuid);
        return entity instanceof RelaySatelliteEntity sat ? sat : null;
    }
}
