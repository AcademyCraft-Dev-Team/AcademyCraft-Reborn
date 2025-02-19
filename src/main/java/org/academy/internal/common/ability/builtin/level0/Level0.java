package org.academy.internal.common.ability.builtin.level0;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityCategoryIdentities;

public class Level0 extends AbilityCategory {
    public static final Level0 INSTANCE = new Level0();

    private Level0() {
        super(AbilityCategoryIdentities.LEVEL0, 10);
    }
}