package org.academy.internal.common.ability.builtin;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.builtin.electromaster.Electromaster;
import org.academy.internal.common.ability.builtin.level0.Level0;

import java.util.ArrayList;
import java.util.List;

public class AcademyCraftAbilityCategories {
    public static final List<AbilityCategory> ABILITY_CATEGORY_LIST = new ArrayList<>();
    public static final AbilityCategory LEVEL0 = new Level0();
    public static final AbilityCategory ELECTROMASTER = new Electromaster();

    static {
        ABILITY_CATEGORY_LIST.add(LEVEL0);
        ABILITY_CATEGORY_LIST.add(ELECTROMASTER);
    }

    private AcademyCraftAbilityCategories() {
    }
}
