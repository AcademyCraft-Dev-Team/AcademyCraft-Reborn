package org.academy.internal.common.ability.builtin.electromaster;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityCategoryIdentities;
import org.academy.internal.common.ability.builtin.electromaster.skills.ArcGenerate;
import org.academy.internal.common.ability.builtin.electromaster.skills.Railgun;

public final class Electromaster extends AbilityCategory {
    public static final AbilityCategory INSTANCE = new Electromaster();

    private Electromaster() {
        super(AbilityCategoryIdentities.ELECTROMASTER);
        this.skillList.add(Railgun.INSTANCE);
        this.skillList.add(ArcGenerate.INSTANCE);
    }
}