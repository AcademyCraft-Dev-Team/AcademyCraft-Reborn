package org.academy.internal.common.ability.builtin;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.builtin.electromaster.Electromaster;

import java.util.ArrayList;
import java.util.List;

public class AcademyCraftAbilityCategoryList {
    public static final List<AbilityCategory> ABILITY_CATEGORY_LIST = new ArrayList<>();
    public static final AbilityCategory ELECTROMASTER = new Electromaster();

    static {
        ABILITY_CATEGORY_LIST.add(ELECTROMASTER);
    }

    private AcademyCraftAbilityCategoryList() {
    }
}
