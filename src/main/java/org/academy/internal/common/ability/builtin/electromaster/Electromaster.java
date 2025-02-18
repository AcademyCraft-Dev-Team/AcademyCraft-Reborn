package org.academy.internal.common.ability.builtin.electromaster;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityCategoryIdentities;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.builtin.electromaster.skills.Railgun;

public class Electromaster extends AbilityCategory {
    private static final Skill railgun = new Railgun();

    public Electromaster() {
        super(AbilityCategoryIdentities.ELECTROMASTER);
        this.skillList.add(railgun);
    }
}