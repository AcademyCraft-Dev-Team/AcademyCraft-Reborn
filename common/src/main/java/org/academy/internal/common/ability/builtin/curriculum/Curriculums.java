package org.academy.internal.common.ability.builtin.curriculum;

import org.academy.api.common.curriculum.Curriculum;

import java.util.ArrayList;
import java.util.List;

public class Curriculums {
    public static final List<Curriculum> CURRICULUM_LIST = new ArrayList<>();

    static {
        CURRICULUM_LIST.add(MaximumComputingPower.INSTANCE);
        CURRICULUM_LIST.add(ComputingPowerRecoverySpeed.INSTANCE);
    }

    private Curriculums() {
    }
}