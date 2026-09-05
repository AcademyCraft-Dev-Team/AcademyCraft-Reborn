package org.academy.api.server.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.academy.internal.server.config.DimensionEffectsConfig;

/**
 * Authoritative admission for skill effects, shared by skills, programs and non-player actors.
 * Always pass the level where the effect is applied, including delayed and reflected effects.
 * DEFAULT preserves the caller's existing settings; ALLOW overrides personal effect switches,
 * but does not bypass team protection, block permissions or other gameplay requirements.
 */
public final class AbilityEffectPolicy {
    public enum Decision {
        DEFAULT,
        ALLOW,
        DENY
    }

    private AbilityEffectPolicy() {
    }

    public static Decision pvp(Level effectLevel) {
        return decide(effectLevel, true);
    }

    public static Decision blockDestruction(Level effectLevel) {
        return decide(effectLevel, false);
    }

    private static Decision decide(Level effectLevel, boolean pvp) {
        if (!(effectLevel instanceof ServerLevel level)) return Decision.DEFAULT;
        var server = level.getServer().getAcademyCraftServer();
        if (server == null) return Decision.DEFAULT;
        return decide(server.getDimensionEffectsConfig(), level.dimension().identifier().toString(), pvp);
    }

    static Decision decide(DimensionEffectsConfig config, String dimension, boolean pvp) {
        if (!config.enabled) return Decision.DEFAULT;
        var rule = pvp ? config.pvp : config.blockDestruction;
        return rule.allows(dimension) ? Decision.ALLOW : Decision.DENY;
    }
}
