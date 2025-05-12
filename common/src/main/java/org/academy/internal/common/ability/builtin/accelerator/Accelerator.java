package org.academy.internal.common.ability.builtin.accelerator;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.builtin.AbilityCategoryNames;
import org.academy.internal.common.ability.builtin.accelerator.skills.*;

public class Accelerator extends AbilityCategory {
    public static final AbilityCategory INSTANCE = new Accelerator();

    private Accelerator() {
        super(AbilityCategoryNames.ACCELERATOR, 0.1F);
        skillList.add(VectorReflection.INSTANCE);
        skillList.add(BloodflowReverse.INSTANCE);
        skillList.add(StormWing.INSTANCE);
        skillList.add(PlasmaGeneration.INSTANCE);
        skillList.add(KineticEnergyApplied.INSTANCE);
        skillList.add(DirStrike.INSTANCE);
    }
}