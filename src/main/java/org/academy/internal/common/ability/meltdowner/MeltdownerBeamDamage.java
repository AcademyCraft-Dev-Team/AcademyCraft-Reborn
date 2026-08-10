package org.academy.internal.common.ability.meltdowner;

import org.academy.internal.common.ability.meltdowner.skills.RadiationIntensify;

public final class MeltdownerBeamDamage {
    private MeltdownerBeamDamage() {
    }

    public static float calculate(
            float baseDamage,
            float maxHealthRatio,
            float targetMaxHealth,
            float playerMultiplier,
            boolean marked
    ) {
        if (!Float.isFinite(baseDamage)
                || !Float.isFinite(maxHealthRatio)
                || !Float.isFinite(targetMaxHealth)
                || !Float.isFinite(playerMultiplier)) {
            return 0.0f;
        }
        return calculate(baseDamage, maxHealthRatio, targetMaxHealth, playerMultiplier,
                marked, RadiationIntensify.MARK_DAMAGE_MULTIPLIER);
    }

    public static float calculate(
            float baseDamage,
            float maxHealthRatio,
            float targetMaxHealth,
            float playerMultiplier,
            boolean marked,
            float markedMultiplier
    ) {
        if (!Float.isFinite(baseDamage) || !Float.isFinite(maxHealthRatio)
                || !Float.isFinite(targetMaxHealth) || !Float.isFinite(playerMultiplier)
                || !Float.isFinite(markedMultiplier)) return 0.0f;
        var ordinary = Math.max(0.0f, baseDamage) * (marked ? Math.max(1.0f, markedMultiplier) : 1.0f);
        var maximumHealth = Math.max(0.0f, targetMaxHealth) * Math.max(0.0f, maxHealthRatio);
        return (ordinary + maximumHealth) * Math.max(0.0f, playerMultiplier);
    }

    public static float amplify(float damage, boolean marked) {
        if (!Float.isFinite(damage)) return 0.0f;
        return Math.max(0.0f, damage) * (marked ? RadiationIntensify.MARK_DAMAGE_MULTIPLIER : 1.0f);
    }
}
