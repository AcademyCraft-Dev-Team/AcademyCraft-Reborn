package org.academy.internal.common.ability.electromaster.skills;

import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.AbilityCategories;

public class MagnetManipulation extends Skill {
    public MagnetManipulation() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get()).level(AbilityLevel.LEVEL3));
    }
}