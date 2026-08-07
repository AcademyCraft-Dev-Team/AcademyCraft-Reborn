package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VectorContinuousInterceptionLeases {
    public static final int LEASE_HITS = 5;
    private static final long LEASE_TICKS = 5L;
    private static final Map<UUID, Map<Long, Lease>> LEASES = new HashMap<>();

    private VectorContinuousInterceptionLeases() {
    }

    public static boolean consume(
            ServerPlayer defender,
            long leaseKey,
            VectorRedirectKind kind,
            float damage
    ) {
        if (!(damage > 0.0f) || !Float.isFinite(damage)) return false;
        var playerLeases = LEASES.get(defender.getUUID());
        if (playerLeases == null) return false;
        var lease = playerLeases.get(leaseKey);
        var gameTime = defender.level().getGameTime();
        if (lease == null
                || lease.kind != kind
                || lease.expiresAt < gameTime
                || lease.remainingDamageBudget + 1.0E-5f < damage) {
            playerLeases.remove(leaseKey);
            if (playerLeases.isEmpty()) LEASES.remove(defender.getUUID());
            return false;
        }
        lease.remainingDamageBudget -= damage;
        return true;
    }

    public static void create(
            ServerPlayer defender,
            long leaseKey,
            VectorRedirectKind kind,
            float prepaidDamage,
            float firstHitDamage
    ) {
        var remaining = Math.max(0.0f, prepaidDamage - firstHitDamage);
        if (!(remaining > 0.0f) || !Float.isFinite(remaining)) return;
        LEASES.computeIfAbsent(defender.getUUID(), _ -> new HashMap<>())
                .put(leaseKey, new Lease(
                        kind,
                        defender.level().getGameTime() + LEASE_TICKS,
                        remaining
                ));
    }

    public static void clear(ServerPlayer defender) {
        if (defender != null) LEASES.remove(defender.getUUID());
    }

    private static final class Lease {
        private final VectorRedirectKind kind;
        private final long expiresAt;
        private float remainingDamageBudget;

        private Lease(VectorRedirectKind kind, long expiresAt, float remainingDamageBudget) {
            this.kind = kind;
            this.expiresAt = expiresAt;
            this.remainingDamageBudget = remainingDamageBudget;
        }
    }
}
