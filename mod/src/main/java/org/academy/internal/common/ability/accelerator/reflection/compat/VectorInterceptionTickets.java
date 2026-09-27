package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public final class VectorInterceptionTickets {
    private static final Map<UUID, Ticket> TICKETS = new HashMap<>();

    private VectorInterceptionTickets() {
    }

    public static boolean wasCommitted(ServerPlayer defender, long fingerprint) {
        var bucket = TICKETS.get(defender.getUUID());
        if (bucket == null) return false;
        var gameTime = defender.level().getGameTime();
        if (bucket.gameTime != gameTime) {
            TICKETS.remove(defender.getUUID());
            return false;
        }
        return bucket.fingerprints.contains(fingerprint);
    }

    public static void commit(ServerPlayer defender, long fingerprint) {
        var gameTime = defender.level().getGameTime();
        var bucket = TICKETS.compute(defender.getUUID(), (_, current) ->
                current == null || current.gameTime != gameTime
                        ? new Ticket(gameTime, new HashSet<>())
                        : current
        );
        bucket.fingerprints.add(fingerprint);
    }

    public static void clear(ServerPlayer defender) {
        if (defender != null) TICKETS.remove(defender.getUUID());
    }

    private record Ticket(long gameTime, Set<Long> fingerprints) {
    }
}
