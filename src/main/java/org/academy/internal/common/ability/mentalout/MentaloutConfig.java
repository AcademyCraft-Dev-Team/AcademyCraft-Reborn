package org.academy.internal.common.ability.mentalout;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.config.AbilityConfig;

public final class MentaloutConfig {
    private MentaloutConfig() {
    }

    public static boolean allowPlayerRoster(ServerPlayer player) {
        return settings(player).allowPlayerRoster;
    }

    public static boolean allowMentalTakeover(ServerPlayer player) {
        return settings(player).allowMentalTakeover;
    }

    public static float mentalInterventionCost(ServerPlayer player) {
        return nonNegative(settings(player).mentalInterventionCost, 10.0f);
    }

    public static float targetMisidentificationCost(ServerPlayer player) {
        return nonNegative(settings(player).targetMisidentificationCost, 40.0f);
    }

    public static float mentalStuporCost(ServerPlayer player) {
        return nonNegative(settings(player).mentalStuporCostPerTarget, 10.0f);
    }

    public static float impressionManipulationCost(ServerPlayer player) {
        return nonNegative(settings(player).impressionManipulationCostPerTarget, 10.0f);
    }

    public static float commandPositioningCost(ServerPlayer player) {
        return nonNegative(settings(player).commandPositioningCostPerTarget, 10.0f);
    }

    public static float precisionStuporCost(ServerPlayer player) {
        return nonNegative(settings(player).precisionStuporCostPerTarget, 10.0f);
    }

    public static float precisionImpressionCost(ServerPlayer player) {
        return nonNegative(settings(player).precisionImpressionCostPerTarget, 10.0f);
    }

    public static float precisionMisidentificationCost(ServerPlayer player) {
        return nonNegative(settings(player).precisionMisidentificationCostPerTarget, 20.0f);
    }

    public static float precisionPathCost(ServerPlayer player) {
        return nonNegative(settings(player).precisionPathCostPerTarget, 5.0f);
    }

    public static float precisionViewCost(ServerPlayer player) {
        return nonNegative(settings(player).precisionViewCostPerTarget, 5.0f);
    }

    public static float precisionGuardCost(ServerPlayer player) {
        return nonNegative(settings(player).precisionGuardCostPerTarget, 10.0f);
    }

    public static float precisionSensoryCost(ServerPlayer player, int level) {
        var settings = settings(player);
        return switch (Mth.clamp(level, 0, 2)) {
            case 0 -> nonNegative(settings.precisionSensoryCostLevel0, 20.0f);
            case 1 -> nonNegative(settings.precisionSensoryCostLevel1, 15.0f);
            default -> nonNegative(settings.precisionSensoryCostLevel2, 10.0f);
        };
    }

    public static float precisionIntrusionCost(ServerPlayer player, int level) {
        var settings = settings(player);
        return switch (Mth.clamp(level, 0, 2)) {
            case 0 -> nonNegative(settings.precisionIntrusionCostLevel0, 20.0f);
            case 1 -> nonNegative(settings.precisionIntrusionCostLevel1, 15.0f);
            default -> nonNegative(settings.precisionIntrusionCostLevel2, 10.0f);
        };
    }

    public static float bossCostMultiplier(ServerPlayer player) {
        return Math.max(1.0f, finite(settings(player).bossCostMultiplier, 2.0f));
    }

    public static float playerControlCostMultiplier(ServerPlayer player) {
        return Math.max(1.0f, finite(settings(player).playerControlCostMultiplier, 3.0f));
    }

    public static float mentalTakeoverOccupation(ServerPlayer player) {
        return nonNegative(settings(player).mentalTakeoverOccupation, 100.0f);
    }

    public static int playerControlResistanceTicks(ServerPlayer player) {
        return Mth.clamp(settings(player).playerControlResistanceTicks, 0, 20 * 60 * 10);
    }

    public static float mentalIntrusionCost(ServerPlayer player, int level) {
        return scaled(settings(player).mentalIntrusionMaintenanceCost, 20.0f, level, 0.85f);
    }

    public static float sensoryDistortionCost(ServerPlayer player, int level) {
        return scaled(settings(player).sensoryDistortionMaintenanceCost, 30.0f, level, 5.0f / 6.0f);
    }

    public static double mentalIntrusionRange(ServerPlayer player, int level) {
        var maximum = Mth.clamp(finite(settings(player).mentalIntrusionRange, 32.0f), 1.0f, 32.0f);
        return maximum * switch (Mth.clamp(level, 0, 2)) {
            case 0 -> 0.5;
            case 1 -> 0.75;
            default -> 1.0;
        };
    }

    public static int playerIntrusionDuration(ServerPlayer player, int level) {
        var maximum = Mth.clamp(settings(player).playerIntrusionMaxTicks, 20, 200);
        return Math.max(20, Math.round(maximum * switch (Mth.clamp(level, 0, 2)) {
            case 0 -> 0.6f;
            case 1 -> 0.8f;
            default -> 1.0f;
        }));
    }

    public static int playerIntrusionCooldown(ServerPlayer player) {
        return Mth.clamp(settings(player).playerIntrusionCooldownTicks, 20, 1200);
    }

    public static double intrusionMaximumDistance(ServerPlayer player) {
        return Mth.clamp(finite(settings(player).mentalIntrusionMaxDistance, 96.0f), 16.0f, 256.0f);
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
        return base * switch (Mth.clamp(level, 0, 2)) {
            case 0 -> 1.0f;
            case 1 -> intermediateScale;
            default -> 0.7f;
        };
    }
}
