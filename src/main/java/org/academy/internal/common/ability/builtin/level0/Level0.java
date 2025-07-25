package org.academy.internal.common.ability.builtin.level0;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.builtin.AbilityCategoryNames;

public class Level0 extends AbilityCategory {
    public static final Level0 INSTANCE = new Level0();

    private Level0() {
        super(AbilityCategoryNames.LEVEL0, 100);
    }
}