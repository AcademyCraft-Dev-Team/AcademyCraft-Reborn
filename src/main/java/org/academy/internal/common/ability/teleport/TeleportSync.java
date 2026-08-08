package org.academy.internal.common.ability.teleport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Forces ability-driven teleports to use an absolute entity-position update. */
public final class TeleportSync {
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
        synchronized (PENDING_ABSOLUTE_SYNCS) {
            PENDING_ABSOLUTE_SYNCS.add(entity);
        }
        entity.teleportTo(destination.x, destination.y, destination.z);
        entity.needsSync = true;
        return true;
    }

    public static boolean consumeAbsoluteSync(Entity entity) {
        synchronized (PENDING_ABSOLUTE_SYNCS) {
            return PENDING_ABSOLUTE_SYNCS.remove(entity);
        }
    }
}
