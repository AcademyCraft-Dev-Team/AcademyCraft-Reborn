package org.academy.internal.common.ability.aeromanip;

import net.minecraft.server.level.ServerPlayer;
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

    public static float skillFloat(ServerPlayer player, String skillId, String key, float fallback) {
        var server = player.level().getServer();
        if (!(server instanceof MinecraftServerContext context)) return fallback;
        var skill = context.getAcademyCraftServer().getAbilityConfig().skills.get(skillId);
        if (skill == null) return fallback;
        var value = skill.floatMap.getOrDefault(key, fallback);
        return Float.isFinite(value) ? value : fallback;
    }

    public static float damageMultiplier(ServerPlayer player, String skillId) {
        return clamp(skillFloat(player, skillId, "damageMultiplier", 1.0f), 0.0f, 4.0f);
    }

    public static float rangeMultiplier(ServerPlayer player, String skillId) {
        return clamp(skillFloat(player, skillId, "rangeMultiplier", 1.0f), 0.1f, 4.0f);
    }

    public static float durationMultiplier(ServerPlayer player, String skillId) {
        return clamp(skillFloat(player, skillId, "durationMultiplier", 1.0f), 0.1f, 4.0f);
    }

    public static float cpMultiplier(ServerPlayer player, String skillId) {
        return clamp(skillFloat(player, skillId, "cpMultiplier", 1.0f), 0.1f, 4.0f);
    }

    public static float pvpForce(ServerPlayer player) {
        return clamp(settings(player).pvpForceMultiplier, 0.0f, 1.0f);
    }

    public static float pvpDuration(ServerPlayer player) {
        return clamp(settings(player).pvpControlDurationMultiplier, 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
