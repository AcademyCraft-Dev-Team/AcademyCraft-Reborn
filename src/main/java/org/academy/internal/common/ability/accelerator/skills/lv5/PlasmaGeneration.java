package org.academy.internal.common.ability.accelerator.skills.lv5;

import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;

public class PlasmaGeneration extends Skill {
    public PlasmaGeneration() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL5)
                .dependsOn(Skills.VECTOR_REFLECTION)
        );
    }

    public static final class Client {
    }

    public static final class Server {
    }
}