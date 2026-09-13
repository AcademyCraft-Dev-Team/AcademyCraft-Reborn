package org.academy.internal.common.ability.aeromanip;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.server.ability.SkillTuning;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.config.AbilityConfig;

public final class AeromanipConfig {
    private AeromanipConfig() {
    }

    public static AbilityConfig.AeromanipSettings settings(ServerPlayer player) {
        var server = player.level().getServer();
        if (!(server instanceof MinecraftServerContext context)) {
            return new AbilityConfig.AeromanipSettings();
        }
        return context.getAcademyCraftServer().getAbilityConfig().aeromanip;
    }

    /** Skill-specific extras (compressed air costs, timings) that have no canonical numeric meaning. */
    public static float skillFloat(ServerPlayer player, String skillId, String key, float fallback) {
        var config = abilityConfig(player);
        if (config == null) return fallback;
        var skill = config.skillSettings(skillId);
        if (skill == null) return fallback;
        var value = skill.floatMap.getOrDefault(key, fallback);
        return Float.isFinite(value) ? value : fallback;
    }

    public static float rangeMultiplier(ServerPlayer player, String skillId) {
        return clamp(SkillTuning.rangeMultiplier(abilityConfig(player), skillId), 0.1f, 4.0f);
    }

    public static float durationMultiplier(ServerPlayer player, String skillId) {
        return clamp(skillFloat(player, skillId, "durationMultiplier", 1.0f), 0.1f, 4.0f);
    }

    public static float pvpForce(ServerPlayer player) {
        return clamp(settings(player).pvpForceMultiplier, 0.0f, 1.0f);
    }

    public static float pvpDuration(ServerPlayer player) {
        return clamp(settings(player).pvpControlDurationMultiplier, 0.0f, 1.0f);
    }

    private static AbilityConfig abilityConfig(ServerPlayer player) {
        var server = player.level().getServer();
        return server instanceof MinecraftServerContext context
                ? context.getAcademyCraftServer().getAbilityConfig()
                : null;
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.clamp(value, min, max);
    }
}
