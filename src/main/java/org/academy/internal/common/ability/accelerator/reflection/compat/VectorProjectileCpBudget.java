package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.server.ability.AbilitySystemServer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared rolling CP charge limit for reflected and refracted projectiles. */
public final class VectorProjectileCpBudget {
    public static final double MAX_CP_PER_SECOND_RATIO = 0.40D;
    public static final long WINDOW_TICKS = 20L;
    private static final Map<UUID, Window> WINDOWS = new HashMap<>();

    private VectorProjectileCpBudget() {
    }

    public static float limitBaseCost(ServerPlayer player, float requestedBaseCost) {
        if (player == null) return Float.NaN;
        var system = AbilitySystemServer.getSystem(player);
        var playerId = player.getUUID();
        var gameTime = player.level().getGameTime();
        var window = WINDOWS.computeIfAbsent(playerId, _ -> new Window());
        var spentActualCp = window.spentSince(gameTime, gameTime - WINDOW_TICKS + 1L);
        return limitBaseCost(
                requestedBaseCost,
                system.getPlayerCalculationIntensity(playerId),
                system.getPlayerMaxCP(playerId),
                spentActualCp
        );
    }

    static float limitBaseCost(
            float requestedBaseCost,
            float calculationIntensity,
            float maximumCp,
            float spentActualCp
    ) {
        if (!Float.isFinite(requestedBaseCost) || requestedBaseCost < 0.0f
                || !Float.isFinite(calculationIntensity) || !(calculationIntensity > 0.0f)
                || !Float.isFinite(maximumCp) || !(maximumCp > 0.0f)
                || !Float.isFinite(spentActualCp) || spentActualCp < 0.0f) {
            return Float.NaN;
        }
        var maximumActualCp = (double) maximumCp * MAX_CP_PER_SECOND_RATIO;
        var remainingActualCp = Math.max(0.0, maximumActualCp - spentActualCp);
        var requestedActualCp = (double) requestedBaseCost * calculationIntensity;
        var allowedActualCp = Math.min(requestedActualCp, remainingActualCp);
        var allowedBaseCost = (float) (allowedActualCp / calculationIntensity);
        return Float.isFinite(allowedBaseCost) ? allowedBaseCost : Float.NaN;
    }

    public static void record(ServerPlayer player, float actualCpCost) {
        if (player == null || !(actualCpCost > 0.0f) || !Float.isFinite(actualCpCost)) return;
        WINDOWS.computeIfAbsent(player.getUUID(), _ -> new Window())
                .record(player.level().getGameTime(), actualCpCost);
    }

    public static void clear(ServerPlayer player) {
        if (player != null) WINDOWS.remove(player.getUUID());
    }

    static final class Window {
        private final ArrayDeque<Charge> charges = new ArrayDeque<>();
        private long lastGameTime = Long.MIN_VALUE;
        private float spentActualCp;

        float spentSince(long gameTime, long earliestIncludedTick) {
            if (gameTime < lastGameTime) {
                charges.clear();
                spentActualCp = 0.0f;
            }
            lastGameTime = gameTime;
            while (!charges.isEmpty() && charges.getFirst().gameTime < earliestIncludedTick) {
                spentActualCp -= charges.removeFirst().actualCpCost;
            }
            if (spentActualCp < 0.0f) spentActualCp = 0.0f;
            return spentActualCp;
        }

        void record(long gameTime, float actualCpCost) {
            spentSince(gameTime, gameTime - WINDOW_TICKS + 1L);
            charges.addLast(new Charge(gameTime, actualCpCost));
            spentActualCp += actualCpCost;
        }
    }

    private record Charge(long gameTime, float actualCpCost) {
    }
}
