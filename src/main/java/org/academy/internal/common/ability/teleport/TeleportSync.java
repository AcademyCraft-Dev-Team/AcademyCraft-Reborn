package org.academy.internal.common.ability.teleport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.misaka.MisakaNetworkServer;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Forces ability-driven teleports to use an absolute entity-position update.
 */
public final class TeleportSync {
    private static final double CLIENT_SNAP_RANGE_SQUARED = 256.0 * 256.0;
    private static final Set<Entity> PENDING_ABSOLUTE_SYNCS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private TeleportSync() {
    }

    public static boolean teleportInstantly(Entity entity, Vec3 destination) {
        if (entity == null || destination == null
                || !(entity.level() instanceof ServerLevel)
                || EntityMotionGuard.shouldBlockTeleport(entity)) {
            return false;
        }
        return teleportInstantly(entity, (ServerLevel) entity.level(), destination);
    }

    public static boolean teleportInstantly(
            Entity entity,
            ServerLevel destinationLevel,
            Vec3 destination
    ) {
        if (entity == null || destinationLevel == null || destination == null
                || !(entity.level() instanceof ServerLevel)
                || EntityMotionGuard.shouldBlockTeleport(entity, destination)) {
            return false;
        }
        synchronized (PENDING_ABSOLUTE_SYNCS) {
            PENDING_ABSOLUTE_SYNCS.add(entity);
        }
        if (!entity.teleportTo(
                destinationLevel,
                destination.x,
                destination.y,
                destination.z,
                Set.of(),
                entity.getYRot(),
                entity.getXRot(),
                false
        )) {
            synchronized (PENDING_ABSOLUTE_SYNCS) {
                PENDING_ABSOLUTE_SYNCS.remove(entity);
            }
            return false;
        }
        entity.needsSync = true;
        var packet = new InstantTeleportSyncPacket(
                entity.getId(), entity.position(), entity.getYRot(), entity.getXRot());
        for (var observer : destinationLevel.players()) {
            if (observer == entity
                    || observer.position().distanceToSqr(destination) <= CLIENT_SNAP_RANGE_SQUARED) {
                MisakaNetworkServer.send(observer, packet);
            }
        }
        return true;
    }

    public static boolean consumeAbsoluteSync(Entity entity) {
        synchronized (PENDING_ABSOLUTE_SYNCS) {
            return PENDING_ABSOLUTE_SYNCS.remove(entity);
        }
    }
}
