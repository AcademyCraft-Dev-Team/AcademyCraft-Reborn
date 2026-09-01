package org.academy.api.server.time;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.academy.api.server.vanilla.MinecraftServerContext;

/** Access to the time-control service associated with a Minecraft server. */
public final class TemporalApi {
    private TemporalApi() {
    }

    public static TemporalService get(MinecraftServer server) {
        var context = (MinecraftServerContext) server;
        if (!context.hasAcademyCraftServer()) {
            throw new IllegalStateException("AcademyCraftServer has not been initialized.");
        }
        return context.getAcademyCraftServer().getTemporalService();
    }

    public static TemporalService get(Entity entity) {
        var server = entity.level().getServer();
        if (server == null) {
            throw new IllegalArgumentException("Entity is not attached to a server level.");
        }
        return get(server);
    }
}
