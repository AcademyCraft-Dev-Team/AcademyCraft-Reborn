package org.academy.internal.common.ability.builtin;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.builtin.accelerator.Accelerator;
import org.academy.internal.common.ability.builtin.electromaster.Electromaster;
import org.academy.internal.common.ability.builtin.level0.Level0;
import org.academy.internal.common.ability.builtin.meltdowner.Meltdowner;
import org.academy.internal.common.ability.builtin.teleport.Teleport;

import java.util.ArrayList;
import java.util.List;

public class AbilityCategories {
    public static final List<AbilityCategory> ABILITY_CATEGORY_LIST = new ArrayList<>();

    static {
        ABILITY_CATEGORY_LIST.add(Level0.INSTANCE);
        ABILITY_CATEGORY_LIST.add(Electromaster.INSTANCE);
        ABILITY_CATEGORY_LIST.add(Teleport.INSTANCE);
        ABILITY_CATEGORY_LIST.add(Accelerator.INSTANCE);
        ABILITY_CATEGORY_LIST.add(Meltdowner.INSTANCE);
    }

    private AbilityCategories() {
    }
}