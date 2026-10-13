package org.academy.internal.common.structure.physics;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.UUID;

/** Exact runtime identity; a replacement entity with the same UUID cannot inherit control. */
final class NativeStructureControlActor {
    private final Entity actor;
    private final UUID originalId;
    private final ServerLevel host;
    private final MinecraftServer server;

    NativeStructureControlActor(Entity actor, ServerLevel host, MinecraftServer server) {
        this.actor = Objects.requireNonNull(actor, "actor");
        originalId = actor.getUUID();
        this.host = host;
        this.server = server;
        if (!available()) {
            throw new UnavailableActor();
        }
    }

    static final class UnavailableActor extends IllegalArgumentException {
        private UnavailableActor() {
            super("Physics controller must be the exact live registered entity in this host");
        }
    }

    boolean is(Entity candidate) {
        return actor == candidate;
    }

    boolean available() {
        if (!actor.isAlive() || actor.isRemoved() || actor.level() != host || !originalId.equals(actor.getUUID())
                || host.getServer() != server || server.getLevel(host.dimension()) != host
                || host.getEntity(originalId) != actor) return false;
        return !(actor instanceof ServerPlayer player)
                || server.getPlayerList().getPlayer(originalId) == player && player.connection != null
                && player.connection.isAcceptingMessages() && !player.hasDisconnected();
    }
}
