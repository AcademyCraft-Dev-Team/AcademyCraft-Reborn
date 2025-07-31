package org.academy.internal.common.ability.accelerator;

import org.academy.api.common.ability.AbilityCategory;

public final class Accelerator extends AbilityCategory {
    public static final AbilityCategory INSTANCE = new Accelerator();

    public Accelerator() {
        super(0.1F);
    }
}