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
        var damage = (Math.max(0.0f, baseDamage)
                + Math.max(0.0f, targetMaxHealth) * Math.max(0.0f, maxHealthRatio))
                * Math.max(0.0f, playerMultiplier);
        return amplify(damage, marked);
    }

    public static float amplify(float damage, boolean marked) {
        if (!Float.isFinite(damage)) return 0.0f;
        return Math.max(0.0f, damage) * (marked ? RadiationIntensify.MARK_DAMAGE_MULTIPLIER : 1.0f);
    }
}
