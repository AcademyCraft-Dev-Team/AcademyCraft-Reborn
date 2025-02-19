package org.academy.internal.common.ability.builtin.teleporter;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityCategoryIdentities;
import org.academy.internal.common.ability.builtin.teleporter.skills.SelfTeleport;

public final class Teleporter extends AbilityCategory {
    public static final Teleporter INSTANCE = new Teleporter();

    private Teleporter() {
        super(AbilityCategoryIdentities.TELEPORTER);
        this.skillList.add(SelfTeleport.INSTANCE);
    }
}