package org.academy.internal.common.ability.builtin.meltdowner;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.builtin.AbilityCategoryNames;
import org.academy.internal.common.ability.builtin.meltdowner.skills.SingleHighSpeedElectronBeam;

public class Meltdowner extends AbilityCategory {
    public static final AbilityCategory INSTANCE = new Meltdowner();

    private Meltdowner() {
        super(AbilityCategoryNames.MELTDOWNER);
        this.skillList.add(SingleHighSpeedElectronBeam.INSTANCE);
    }
}