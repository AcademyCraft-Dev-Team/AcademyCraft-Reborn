package org.academy.internal.common.ability.electromaster.skills.lv3;

import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.AbilityCategories;

public class MagnetManipulation extends Skill {
    public MagnetManipulation() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get()).level(AbilityLevel.LEVEL3));
    }
}