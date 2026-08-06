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


    public static float precisionPathCost(ServerPlayer player) {
        return nonNegative(settings(player).precisionPathCostPerTarget, 10.0f);
    }

    public static float precisionViewCost(ServerPlayer player) {
        return nonNegative(settings(player).precisionViewCostPerTarget, 8.0f);
    }
    public static float bossCostMultiplier(ServerPlayer player) {
        return Math.max(1.0f, finite(settings(player).bossCostMultiplier, 2.0f));
    }

    public static float mentalIntrusionCost(ServerPlayer player, int level) {
        return scaled(settings(player).mentalIntrusionMaintenanceCost, 20.0f, level, 0.85f);
    }

    public static float sensoryDistortionCost(ServerPlayer player, int level) {
        return scaled(settings(player).sensoryDistortionMaintenanceCost, 30.0f, level, 5.0f / 6.0f);
    }

    public static double mentalIntrusionRange(ServerPlayer player, int level) {
        var maximum = Math.clamp(finite(settings(player).mentalIntrusionRange, 16.0f), 1.0f, 16.0f);
        return maximum * switch (Math.clamp(level, 0, 2)) {
            case 0 -> 0.75;
            case 1 -> 0.875;
            default -> 1.0;
        };
    }

    public static int playerIntrusionDuration(ServerPlayer player, int level) {
        var maximum = Math.clamp(settings(player).playerIntrusionMaxTicks, 20, 200);
        return Math.max(20, Math.round(maximum * switch (Math.clamp(level, 0, 2)) {
            case 0 -> 0.6f;
            case 1 -> 0.8f;
            default -> 1.0f;
        }));
    }

    public static int playerIntrusionCooldown(ServerPlayer player) {
        return Math.clamp(settings(player).playerIntrusionCooldownTicks, 20, 1200);
    }

    public static double intrusionMaximumDistance(ServerPlayer player) {
        return Math.clamp(finite(settings(player).mentalIntrusionMaxDistance, 96.0f), 16.0f, 256.0f);
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

    private static float scaled(float configured, float fallback, int level, float intermediateScale) {
        var base = nonNegative(configured, fallback);
        return base * switch (Math.clamp(level, 0, 2)) {
            case 0 -> 1.0f;
            case 1 -> intermediateScale;
            default -> 0.7f;
        };
    }
}
