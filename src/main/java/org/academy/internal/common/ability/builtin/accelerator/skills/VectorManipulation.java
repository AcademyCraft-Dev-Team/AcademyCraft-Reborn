package org.academy.internal.common.ability.builtin.accelerator.skills;

import org.academy.api.common.ability.Skill;

public class VectorManipulation extends Skill {
    public static final VectorManipulation INSTANCE = new VectorManipulation();

    public VectorManipulation() {
        super("vec_manipulation", 1);
    }
}