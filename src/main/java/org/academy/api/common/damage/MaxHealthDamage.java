package org.academy.api.common.damage;

/**
 * Unscaled base and maximum-health terms of a skill hit.
 * Rebalance before applying the skill's existing power, player, mark or secondary-hit multipliers.
 * The 200-point limit applies only to the converted bonus, not the original base or final damage.
 */
public record MaxHealthDamage(float baseDamage, float maxHealthRatio) {
    public static final float MAX_HEALTH_RATIO = 0.05f;
    public static final float DAMAGE_PER_PERCENTAGE_POINT = 5.0f;
    public static final float MAX_CONVERTED_DAMAGE = 200.0f;

    public static MaxHealthDamage rebalance(float baseDamage, float maxHealthRatio) {
        if (!Float.isFinite(baseDamage) || !Float.isFinite(maxHealthRatio)) {
            throw new IllegalArgumentException("Damage profile values must be finite");
        }
        var ratio = Math.max(0.0f, maxHealthRatio);
        var converted = Math.min(MAX_CONVERTED_DAMAGE,
                Math.max(0.0f, ratio * 100.0f - 5.0f) * DAMAGE_PER_PERCENTAGE_POINT);
        return new MaxHealthDamage(Math.max(0.0f, baseDamage) + converted,
                Math.min(MAX_HEALTH_RATIO, ratio));
    }

    public float calculate(float maximumHealth) {
        return calculate(maximumHealth, 1.0f);
    }

    /** Only the base term receives ability/player multipliers; maximum-health damage stays separate. */
    public float calculate(float maximumHealth, float baseMultiplier) {
        if (!Float.isFinite(maximumHealth) || !Float.isFinite(baseMultiplier)) return 0.0f;
        return baseDamage * Math.max(0.0f, baseMultiplier)
                + Math.max(0.0f, maximumHealth) * maxHealthRatio;
    }
}
