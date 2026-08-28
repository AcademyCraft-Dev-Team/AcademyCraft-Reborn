package org.academy.internal.common.ability.teleport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;

/** Posted only after {@link TeleportSync#teleportInstantly} has moved an entity successfully. */
public final class TeleportCompletedEvent extends Event {
    private final Entity entity;
    private final ServerLevel sourceLevel;
    private final ServerLevel destinationLevel;
    private final Vec3 origin;
    private final Vec3 destination;
    private final float preTeleportYaw;
    private final float preTeleportPitch;

    TeleportCompletedEvent(
            Entity entity,
            ServerLevel sourceLevel,
            ServerLevel destinationLevel,
            Vec3 origin,
            Vec3 destination,
            float preTeleportYaw,
            float preTeleportPitch
    ) {
        this.entity = entity;
        this.sourceLevel = sourceLevel;
        this.destinationLevel = destinationLevel;
        this.origin = origin;
        this.destination = destination;
        this.preTeleportYaw = preTeleportYaw;
        this.preTeleportPitch = preTeleportPitch;
    }

    public Entity entity() {
        return entity;
    }

    public ServerLevel sourceLevel() {
        return sourceLevel;
    }

    public ServerLevel destinationLevel() {
        return destinationLevel;
    }

    public Vec3 origin() {
        return origin;
    }

    public Vec3 destination() {
        return destination;
    }

    public float preTeleportYaw() {
        return preTeleportYaw;
    }

    public float preTeleportPitch() {
        return preTeleportPitch;
    }
}
