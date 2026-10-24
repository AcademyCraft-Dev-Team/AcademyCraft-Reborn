package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.server.MinecraftServer;
import org.academy.internal.server.config.GenericConfig;

/** Server-owned settings; bounded work is retained even with malformed administrator values. */
public record StructureRestSettings(boolean enabled, boolean allowOfflineOwners, int restTicks,
                                    int checkIntervalTicks, int candidatesPerTick, int retryTicks) {
    public static StructureRestSettings get(MinecraftServer server) {
        return from(server.getAcademyCraftServerConfig().<GenericConfig>getConfig(GenericConfig.KEY)
                .structureRestoration);
    }

    static StructureRestSettings from(GenericConfig.StructureRestorationSettings config) {
        if (config == null) return new StructureRestSettings(false, false, 100, 20, 4, 100);
        return new StructureRestSettings(config.enabled, config.allowOfflineOwners,
                clamp(config.restTicks, 20, 72_000), clamp(config.checkIntervalTicks, 1, 200),
                clamp(config.candidatesPerTick, 1, 32), clamp(config.retryTicks, 20, 12_000));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
