package org.academy.internal.common.ability.teleport;

public final class TeleportDamage {
    private TeleportDamage() {
    }

    public static float threatening(float baseDamage, float weaponBonus, float playerMultiplier,
                                    float spaceFoldingMultiplier) {
        if (!Float.isFinite(baseDamage) || !Float.isFinite(weaponBonus) || !Float.isFinite(playerMultiplier)) {
            return 0.0f;
        }
        var damage = (Math.max(0.0f, baseDamage) + Math.max(0.0f, weaponBonus))
                * Math.max(0.0f, playerMultiplier);
        damage *= Math.max(0.0f, spaceFoldingMultiplier);
        return Float.isFinite(damage) ? damage : 0.0f;
    }

    public static float fleshRipping(float baseDamage, float maxHealth, float spaceFoldingMultiplier) {
        if (!Float.isFinite(baseDamage) || !Float.isFinite(maxHealth)) {
            return 0.0f;
        }
        var damage = Math.max(0.0f, baseDamage) + Math.max(0.0f, maxHealth) * 0.05f;
        damage *= Math.max(0.0f, spaceFoldingMultiplier);
        return Float.isFinite(damage) ? damage : 0.0f;
    }

    /** Compatibility overloads for callers and tests that still express the passive as a boolean. */
    public static float threatening(float baseDamage, float weaponBonus, float playerMultiplier,
                                    boolean spaceFolding) {
        return threatening(baseDamage, weaponBonus, playerMultiplier, spaceFolding ? 1.25f : 1.0f);
    }

    public static float fleshRipping(float baseDamage, float maxHealth, boolean spaceFolding) {
        return fleshRipping(baseDamage, maxHealth, spaceFolding ? 1.25f : 1.0f);
    }
}
