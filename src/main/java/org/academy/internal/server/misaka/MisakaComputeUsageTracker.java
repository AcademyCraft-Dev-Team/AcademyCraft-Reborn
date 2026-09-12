package org.academy.internal.server.misaka;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MSk usage ledger for the current settle window (accumulates across ticks until
 * {@link MisakaComputeContribution#settleAndApply} clears it every
 * {@link MisakaComputeContribution#SETTLE_INTERVAL_TICKS} ticks).
 * CP occupations convert to MSk via {@code cpAmount / cpPerMsk}.
 */
public final class MisakaComputeUsageTracker {
    private static final Map<UUID, Float> USAGE_MSK = new HashMap<>();

    private MisakaComputeUsageTracker() {
    }

    public static void addUsage(UUID playerUuid, float msk) {
        if (playerUuid == null || !(msk > 0.0f) || !Float.isFinite(msk)) {
            return;
        }
        USAGE_MSK.merge(playerUuid, msk, Float::sum);
    }

    public static float usedThisTick(UUID playerUuid) {
        if (playerUuid == null) {
            return 0.0f;
        }
        return USAGE_MSK.getOrDefault(playerUuid, 0.0f);
    }

    /** Alias for budget checks (same as usedThisTick within the settle window). */
    public static float peekBudget(UUID playerUuid) {
        return usedThisTick(playerUuid);
    }

    public static Map<UUID, Float> snapshot() {
        return Map.copyOf(USAGE_MSK);
    }

    public static void clear() {
        USAGE_MSK.clear();
    }

    public static void clearPlayer(UUID playerUuid) {
        if (playerUuid != null) {
            USAGE_MSK.remove(playerUuid);
        }
    }
}
