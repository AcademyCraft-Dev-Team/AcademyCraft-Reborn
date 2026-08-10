package org.academy.internal.client.gui.screen;

import org.academy.api.common.ability.SkillScope;

/** Visibility policy for nodes in the ability developer skill tree. */
public final class SkillTreeVisibility {
    private SkillTreeVisibility() {
    }

    public static boolean shouldDisplay(
            SkillScope scope,
            boolean learned,
            boolean dependenciesSatisfied,
            boolean conditionsSatisfied
    ) {
        // Common skills form a separate course tree. Hiding its locked root also makes every
        // dependent node disappear, leaving the entire page blank. Keep the full common tree
        // visible and let its locked state and server-side development checks enforce conditions.
        return scope == SkillScope.COMMON
                || learned
                || dependenciesSatisfied && conditionsSatisfied;
    }
}
