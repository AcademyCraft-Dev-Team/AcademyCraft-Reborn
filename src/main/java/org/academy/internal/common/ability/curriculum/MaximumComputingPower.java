package org.academy.internal.common.ability.curriculum;

import org.academy.api.common.curriculum.Curriculum;

public class MaximumComputingPower extends Curriculum {
    public static final MaximumComputingPower INSTANCE = new MaximumComputingPower();

    private MaximumComputingPower() {
        super("max_computing_power");
    }
}