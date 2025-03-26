package org.academy.internal.common.ability.builtin.meltdowner;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityCategoryIdentities;
import org.academy.internal.common.ability.builtin.meltdowner.skills.SingleHighSpeedElectronBeam;

public class Meltdowner extends AbilityCategory {
    public static final AbilityCategory INSTANCE = new Meltdowner();

    private Meltdowner() {
        super(AbilityCategoryIdentities.MELTDOWNER);
        this.skillList.add(SingleHighSpeedElectronBeam.INSTANCE);
    }
}