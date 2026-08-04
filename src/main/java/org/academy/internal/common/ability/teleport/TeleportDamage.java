package org.academy.internal.common.ability.teleport;

public final class TeleportDamage {
    private TeleportDamage() {
    }

    public static float threatening(float baseDamage, float weaponBonus, float playerMultiplier,
                                    boolean spaceFolding) {
        if (!Float.isFinite(baseDamage) || !Float.isFinite(weaponBonus) || !Float.isFinite(playerMultiplier)) {
            return 0.0f;
        }
        var damage = (Math.max(0.0f, baseDamage) + Math.max(0.0f, weaponBonus))
                * Math.max(0.0f, playerMultiplier);
        if (spaceFolding) {
            damage *= 1.25f;
        }
        return Float.isFinite(damage) ? damage : 0.0f;
    }

    public static float fleshRipping(float baseDamage, float maxHealth, boolean spaceFolding) {
        if (!Float.isFinite(baseDamage) || !Float.isFinite(maxHealth)) {
            return 0.0f;
        }
        var damage = Math.max(0.0f, baseDamage) + Math.max(0.0f, maxHealth) * 0.05f;
        if (spaceFolding) {
            damage *= 1.25f;
        }
        return Float.isFinite(damage) ? damage : 0.0f;
    }
}
