package org.academy.internal.common.ability.teleport;

public final class TeleportDamage {
    private TeleportDamage() {
    }

    public static float threatening(float baseDamage, float weaponBonus, float abilityPower,
                                    float playerMultiplier,
                                    float spaceFoldingMultiplier) {
        if (!Float.isFinite(baseDamage) || !Float.isFinite(weaponBonus)
                || !Float.isFinite(abilityPower) || !Float.isFinite(playerMultiplier)
                || !Float.isFinite(spaceFoldingMultiplier)) {
            return 0.0f;
        }
        var damage = (Math.max(0.0f, baseDamage) + Math.max(0.0f, weaponBonus))
                * Math.max(0.0f, abilityPower)
                * Math.max(0.0f, playerMultiplier);
        damage *= Math.max(0.0f, spaceFoldingMultiplier);
        return Float.isFinite(damage) ? damage : 0.0f;
    }

    public static float fleshRipping(float baseDamage, float maxHealth, float abilityPower,
                                    float spaceFoldingMultiplier) {
        if (!Float.isFinite(baseDamage) || !Float.isFinite(maxHealth)
                || !Float.isFinite(abilityPower) || !Float.isFinite(spaceFoldingMultiplier)) {
            return 0.0f;
        }
        var damage = Math.max(0.0f, baseDamage) * Math.max(0.0f, abilityPower)
                + Math.max(0.0f, maxHealth) * 0.05f;
        damage *= Math.max(0.0f, spaceFoldingMultiplier);
        return Float.isFinite(damage) ? damage : 0.0f;
    }

    /** Compatibility overloads for callers and tests that still express the passive as a boolean. */
    public static float threatening(float baseDamage, float weaponBonus, float playerMultiplier,
                                    boolean spaceFolding) {
        return threatening(baseDamage, weaponBonus, 1.0f, playerMultiplier,
                spaceFolding ? 1.25f : 1.0f);
    }

    public static float fleshRipping(float baseDamage, float maxHealth, boolean spaceFolding) {
        return fleshRipping(baseDamage, maxHealth, 1.0f, spaceFolding ? 1.25f : 1.0f);
    }
}
