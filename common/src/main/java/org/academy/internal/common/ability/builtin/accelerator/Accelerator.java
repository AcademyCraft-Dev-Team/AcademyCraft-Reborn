package org.academy.internal.common.ability.builtin.accelerator;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityCategoryIdentities;
import org.academy.internal.common.ability.builtin.accelerator.skills.VectorManipulation;

public class Accelerator extends AbilityCategory {
    public static final AbilityCategory INSTANCE = new Accelerator();

    private Accelerator() {
        super(AbilityCategoryIdentities.ACCELERATOR, 0.1F);
        this.skillList.add(VectorManipulation.INSTANCE);
    }
}