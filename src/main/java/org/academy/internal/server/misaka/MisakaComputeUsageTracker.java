package org.academy.internal.server.misaka;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MSk usage ledger for the current settle window (accumulates across ticks until
 * {@link MisakaComputeContribution#settleAndApply} clears it every
 * {@link MisakaComputeContribution#SETTLE_INTERVAL_TICKS} ticks).
 * <ul>
 *   <li>Charge usage: ACTUAL {@code consumeRate} grants + FORCE {@code onCompute} totals → burn supply.</li>
 *   <li>Network usage: direct network draws → burn supply before budget settle.</li>
 * </ul>
 * Skill CP demand is no longer tracked here; compute is device-only.
 */
public final class MisakaComputeUsageTracker {
    private static final Map<UUID, Float> USAGE_MSK = new HashMap<>();
    private static final Map<UUID, Float> CHARGE_USAGE_MSK = new HashMap<>();
    private static final Map<UUID, Float> NETWORK_USAGE_MSK = new HashMap<>();

    private MisakaComputeUsageTracker() {
    }

    public static void addUsage(UUID playerUuid, float msk) {
        if (playerUuid == null || !(msk > 0.0f) || !Float.isFinite(msk)) {
            return;
        }
        USAGE_MSK.merge(playerUuid, msk, Float::sum);
    }

    /** ACTUAL/FORCE device spend this settle window (burns network supply; no CP grant). */
    public static void addChargeUsage(UUID playerUuid, float msk) {
        if (playerUuid == null || !Float.isFinite(msk) || msk == 0.0f) {
            return;
        }
        CHARGE_USAGE_MSK.merge(playerUuid, msk, Float::sum);
        float next = CHARGE_USAGE_MSK.getOrDefault(playerUuid, 0.0f);
        if (next <= 0.0f) {
            CHARGE_USAGE_MSK.remove(playerUuid);
        }
    }

    /** Replace this player's charge burn for the settle window (used after ACTUAL re-flush). */
    public static void setChargeUsage(UUID playerUuid, float msk) {
        if (playerUuid == null) {
            return;
        }
        if (!Float.isFinite(msk) || msk <= 0.0f) {
            CHARGE_USAGE_MSK.remove(playerUuid);
        } else {
            CHARGE_USAGE_MSK.put(playerUuid, msk);
        }
    }

    public static void addNetworkUsage(UUID networkId, float msk) {
        if (networkId == null || !(msk > 0.0f) || !Float.isFinite(msk)) {
            return;
        }
        NETWORK_USAGE_MSK.merge(networkId, msk, Float::sum);
    }

    public static float usedThisTick(UUID playerUuid) {
        if (playerUuid == null) {
            return 0.0f;
        }
        return USAGE_MSK.getOrDefault(playerUuid, 0.0f);
    }

    public static float chargeUsed(UUID playerUuid) {
        if (playerUuid == null) {
            return 0.0f;
        }
        return CHARGE_USAGE_MSK.getOrDefault(playerUuid, 0.0f);
    }

    public static float networkUsed(UUID networkId) {
        if (networkId == null) {
            return 0.0f;
        }
        return NETWORK_USAGE_MSK.getOrDefault(networkId, 0.0f);
    }

    public static float peekBudget(UUID playerUuid) {
        return usedThisTick(playerUuid);
    }

    public static Map<UUID, Float> snapshot() {
        return Map.copyOf(USAGE_MSK);
    }

    public static Map<UUID, Float> chargeSnapshot() {
        return Map.copyOf(CHARGE_USAGE_MSK);
    }

    public static Map<UUID, Float> networkSnapshot() {
        return Map.copyOf(NETWORK_USAGE_MSK);
    }

    public static void clear() {
        USAGE_MSK.clear();
        CHARGE_USAGE_MSK.clear();
        NETWORK_USAGE_MSK.clear();
    }

    public static void clearPlayer(UUID playerUuid) {
        if (playerUuid != null) {
            USAGE_MSK.remove(playerUuid);
            CHARGE_USAGE_MSK.remove(playerUuid);
        }
    }
}
