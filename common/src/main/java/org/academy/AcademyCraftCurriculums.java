package org.academy;

import org.academy.api.common.curriculum.Curriculum;
import org.academy.internal.common.curriculum.ComputingPowerRecoverySpeedCurriculum;
import org.academy.internal.common.curriculum.MaximumComputingPowerCurriculum;

import java.util.ArrayList;
import java.util.List;

public class AcademyCraftCurriculums {
    public static final List<Curriculum> CURRICULUM_LIST = new ArrayList<>();

    static {
        CURRICULUM_LIST.add(MaximumComputingPowerCurriculum.INSTANCE);
        CURRICULUM_LIST.add(ComputingPowerRecoverySpeedCurriculum.INSTANCE);
    }

    private AcademyCraftCurriculums() {
    }
}