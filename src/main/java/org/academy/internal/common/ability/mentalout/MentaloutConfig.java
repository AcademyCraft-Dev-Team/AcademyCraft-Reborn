package org.academy.internal.common.ability.mentalout;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.config.AbilityConfig;

public final class MentaloutConfig {
    private MentaloutConfig() {
    }

    public static float mentalInterventionCost(ServerPlayer player) {
        return nonNegative(settings(player).mentalInterventionCost, 10.0f);
    }

    public static float targetMisidentificationCost(ServerPlayer player) {
        return nonNegative(settings(player).targetMisidentificationCost, 40.0f);
    }

    public static float mentalStuporCost(ServerPlayer player) {
        return nonNegative(settings(player).mentalStuporCostPerTarget, 30.0f);
    }

    public static float impressionManipulationCost(ServerPlayer player) {
        return nonNegative(settings(player).impressionManipulationCostPerTarget, 20.0f);
    }

    public static float bossCostMultiplier(ServerPlayer player) {
        return Math.max(1.0f, finite(settings(player).bossCostMultiplier, 2.0f));
    }

    private static AbilityConfig.MentaloutSettings settings(ServerPlayer player) {
        var server = player.level().getServer();
        if (!(server instanceof MinecraftServerContext context)) {
            return new AbilityConfig.MentaloutSettings();
        }
        var settings = context.getAcademyCraftServer().getAbilityConfig().mentalout;
        return settings == null ? new AbilityConfig.MentaloutSettings() : settings;
    }

    private static float nonNegative(float value, float fallback) {
        return Math.max(0.0f, finite(value, fallback));
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
