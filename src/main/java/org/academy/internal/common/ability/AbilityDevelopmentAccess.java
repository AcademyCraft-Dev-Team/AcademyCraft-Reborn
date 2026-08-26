package org.academy.internal.common.ability;

import org.academy.api.common.ability.DevelopmentSource;

/**
 * Shared access policy for portable and full-size ability developers.
 */
public final class AbilityDevelopmentAccess {
    public static final int PORTABLE_MIN_SKILL_LEVEL = 1;
    public static final int PORTABLE_MAX_SKILL_LEVEL = 3;
    public static final int PORTABLE_MAX_ABILITY_LEVEL = 3;
    public static final int MAX_ABILITY_LEVEL = 5;

    private AbilityDevelopmentAccess() {
    }

    public static boolean canLearnSkill(DevelopmentSource source, int recommendedLevel) {
        if (source == null) return false;
        return !source.portable()
                || recommendedLevel >= PORTABLE_MIN_SKILL_LEVEL
                && recommendedLevel <= PORTABLE_MAX_SKILL_LEVEL;
    }

    public static boolean canDevelopAbilityLevel(DevelopmentSource source, int targetLevel) {
        if (source == null || targetLevel < 1 || targetLevel > MAX_ABILITY_LEVEL) return false;
        return !source.portable() || targetLevel <= PORTABLE_MAX_ABILITY_LEVEL;
    }
}
