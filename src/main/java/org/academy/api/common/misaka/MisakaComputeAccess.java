package org.academy.api.common.misaka;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime façade for Misaka compute budgets and ACTUAL-mode spending.
 * Production impl is installed from server bootstrap; default is {@link NoopMisakaComputeAccess}.
 */
public interface MisakaComputeAccess {
    AtomicReference<@Nullable MisakaComputeAccess> INSTALLED = new AtomicReference<>();
    AtomicReference<@Nullable MisakaComputeAccess> TESTING_OVERRIDE = new AtomicReference<>();

    static MisakaComputeAccess get() {
        var override = TESTING_OVERRIDE.get();
        if (override != null) {
            return override;
        }
        var installed = INSTALLED.get();
        return installed != null ? installed : NoopMisakaComputeAccess.INSTANCE;
    }

    static void install(@Nullable MisakaComputeAccess access) {
        INSTALLED.set(access);
    }

    static void testingInstall(@Nullable MisakaComputeAccess access) {
        TESTING_OVERRIDE.set(access);
    }

    /**
     * Spend from this player's ACTUAL pool for {@code deviceId}.
     *
     * @param mskPerSecond desired rate; converted to per-tick internally
     * @return MSk granted this tick (0 on reject)
     */
    float consumeRate(ServerPlayer player, Identifier deviceId, float mskPerSecond);

    /** Player budget MSk/s for the current settle window (0 if none). */
    float playerBudgetMskPerSecond(ServerPlayer player);

    /** Effective allocation percent for a present device (0 if absent / unallocated). */
    int effectivePercent(ServerPlayer player, Identifier deviceId);

    /** Last remembered percent (persisted); 0 if never set. */
    int lastPercent(ServerPlayer player, Identifier deviceId);

    /** Free percent slots among present effective allocations (0..100). */
    int remainingPercentSlots(ServerPlayer player);

    /** MSk → CP ratio used by FORCE devices that convert compute. */
    float cpPerMsk(MinecraftServer server);

    static float consumeRateStatic(ServerPlayer player, Identifier deviceId, float mskPerSecond) {
        return get().consumeRate(player, deviceId, mskPerSecond);
    }

    static float cpPerMskStatic(MinecraftServer server) {
        return get().cpPerMsk(server);
    }
}
