package org.academy.internal.common.structure.sable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** A replacement entity with the same persistent UUID never inherits a live control. */
final class SableControlActor {
    private final Entity actor;
    private final UUID identity;
    private final ServerLevel host;
    private final MinecraftServer server;

    SableControlActor(Entity actor, ServerLevel host) {
        this.actor = actor;
        identity = actor.getUUID();
        this.host = host;
        server = host.getServer();
    }

    boolean is(Entity candidate) { return actor == candidate; }

    boolean available() {
        if (!actor.isAlive() || actor.isRemoved() || actor.level() != host || !identity.equals(actor.getUUID())
                || host.getServer() != server || server.getLevel(host.dimension()) != host
                || host.getEntity(identity) != actor) return false;
        return !(actor instanceof ServerPlayer player)
                || server.getPlayerList().getPlayer(identity) == player && player.connection != null
                && player.connection.isAcceptingMessages() && !player.hasDisconnected();
    }
}
