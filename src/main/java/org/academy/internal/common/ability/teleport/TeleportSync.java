package org.academy.internal.common.ability.teleport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
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
    static final Set<Relative> PRESERVED_VIEW_ROTATION =
            Set.of(Relative.Y_ROT, Relative.X_ROT);
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
        return teleportInstantly(entity, destinationLevel, destination, 0.0f, 0.0f, true);
    }

    /** Teleports with an explicit, absolute view direction, including the owning client. */
    public static boolean teleportInstantly(
            Entity entity, ServerLevel destinationLevel, Vec3 destination, float yaw, float pitch
    ) {
        return teleportInstantly(entity, destinationLevel, destination, yaw, pitch, false);
    }

    private static boolean teleportInstantly(
            Entity entity, ServerLevel destinationLevel, Vec3 destination,
            float yaw, float pitch, boolean preserveViewRotation
    ) {
        if (entity == null || destinationLevel == null || destination == null
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || !(entity.level() instanceof ServerLevel)
                || EntityMotionGuard.shouldBlockTeleport(entity, destination)) {
            return false;
        }
        var sourceLevel = (ServerLevel) entity.level();
        var origin = entity.getBoundingBox().getCenter();
        var preTeleportYaw = entity.getYRot();
        var preTeleportPitch = entity.getXRot();
        synchronized (PENDING_ABSOLUTE_SYNCS) {
            PENDING_ABSOLUTE_SYNCS.add(entity);
        }
        if (!entity.teleportTo(
                destinationLevel,
                destination.x,
                destination.y,
                destination.z,
                preserveViewRotation ? PRESERVED_VIEW_ROTATION : Set.of(),
                yaw,
                pitch,
                false
        )) {
            synchronized (PENDING_ABSOLUTE_SYNCS) {
                PENDING_ABSOLUTE_SYNCS.remove(entity);
            }
            return false;
        }
        if (!preserveViewRotation && entity instanceof LivingEntity living) {
            living.setYHeadRot(yaw);
        }
        entity.needsSync = true;
        var packet = new InstantTeleportSyncPacket(
                entity.getId(), entity.position(), entity.getYRot(), entity.getXRot(),
                preserveViewRotation);
        for (var observer : destinationLevel.players()) {
            if (observer == entity
                    || observer.position().distanceToSqr(destination) <= CLIENT_SNAP_RANGE_SQUARED) {
                MisakaNetworkServer.send(observer, packet);
            }
        }
        NeoForge.EVENT_BUS.post(new TeleportCompletedEvent(
                entity,
                sourceLevel,
                destinationLevel,
                origin,
                entity.getBoundingBox().getCenter(),
                preTeleportYaw,
                preTeleportPitch
        ));
        return true;
    }

    public static boolean consumeAbsoluteSync(Entity entity) {
        synchronized (PENDING_ABSOLUTE_SYNCS) {
            return PENDING_ABSOLUTE_SYNCS.remove(entity);
        }
    }
}
