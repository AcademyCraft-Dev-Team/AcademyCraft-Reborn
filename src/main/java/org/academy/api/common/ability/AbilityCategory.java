package org.academy.api.common.ability;

import java.util.ArrayList;
import java.util.List;

public abstract class AbilityCategory {
    public final String name;
    public final List<Skill> skillList = new ArrayList<>();

    public AbilityCategory(final String name) {
        this.name = name;
    }

    public void init() {
    }
}