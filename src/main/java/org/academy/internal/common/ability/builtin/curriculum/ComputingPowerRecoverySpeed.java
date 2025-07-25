package org.academy.internal.common.ability.builtin.curriculum;

import org.academy.api.common.curriculum.Curriculum;

public class ComputingPowerRecoverySpeed extends Curriculum {
    public static final ComputingPowerRecoverySpeed INSTANCE = new ComputingPowerRecoverySpeed();

    private ComputingPowerRecoverySpeed() {
        super("computing_power_recovery_speed");
    }
}