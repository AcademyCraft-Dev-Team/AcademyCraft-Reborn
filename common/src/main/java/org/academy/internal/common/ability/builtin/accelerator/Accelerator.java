package org.academy.internal.common.ability.builtin.accelerator;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityCategoryIdentities;
import org.academy.internal.common.ability.builtin.accelerator.skills.BloodflowReverse;
import org.academy.internal.common.ability.builtin.accelerator.skills.StormWing;
import org.academy.internal.common.ability.builtin.accelerator.skills.VectorReflection;

public class Accelerator extends AbilityCategory {
    public static final AbilityCategory INSTANCE = new Accelerator();

    private Accelerator() {
        super(AbilityCategoryIdentities.ACCELERATOR, 0.1F);
        skillList.add(VectorReflection.INSTANCE);
        skillList.add(BloodflowReverse.INSTANCE);
        skillList.add(StormWing.INSTANCE);
    }
}