package org.academy.internal.common.ability.electromaster;

import org.academy.api.common.ability.AbilityCategory;

public final class Electromaster extends AbilityCategory {
    public static final AbilityCategory INSTANCE = new Electromaster();

    public Electromaster() {
        super(0.1F);
    }
}