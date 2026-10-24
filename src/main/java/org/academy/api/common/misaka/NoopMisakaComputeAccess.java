package org.academy.api.common.misaka;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Stub access used before server install and as a safe default. */
public final class NoopMisakaComputeAccess implements MisakaComputeAccess {
    public static final NoopMisakaComputeAccess INSTANCE = new NoopMisakaComputeAccess();

    private NoopMisakaComputeAccess() {
    }

    @Override
    public float consumeRate(ServerPlayer player, Identifier deviceId, float mskPerSecond) {
        return 0.0f;
    }

    @Override
    public float playerBudgetMskPerSecond(ServerPlayer player) {
        return 0.0f;
    }

    @Override
    public int effectivePercent(ServerPlayer player, Identifier deviceId) {
        return 0;
    }

    @Override
    public int lastPercent(ServerPlayer player, Identifier deviceId) {
        return 0;
    }

    @Override
    public int remainingPercentSlots(ServerPlayer player) {
        return 100;
    }

    @Override
    public float cpPerMsk(MinecraftServer server) {
        return 1.0f;
    }
}
