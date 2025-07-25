package org.academy.internal.common.ability.builtin.teleport;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.builtin.AbilityCategoryNames;
import org.academy.internal.common.ability.builtin.teleport.skills.SelfTeleport;

public final class Teleport extends AbilityCategory {
    public static final Teleport INSTANCE = new Teleport();

    private Teleport() {
        super(AbilityCategoryNames.TELEPORT);
        this.skillList.add(SelfTeleport.INSTANCE);
    }
}