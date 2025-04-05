package org.academy.internal.common.curriculum;

import org.academy.api.common.curriculum.Curriculum;

import java.util.ArrayList;
import java.util.List;

public class Curriculums {
    public static final List<Curriculum> CURRICULUM_LIST = new ArrayList<>();

    static {
        CURRICULUM_LIST.add(MaximumComputingPowerCurriculum.INSTANCE);
        CURRICULUM_LIST.add(ComputingPowerRecoverySpeedCurriculum.INSTANCE);
    }

    private Curriculums() {
    }
}