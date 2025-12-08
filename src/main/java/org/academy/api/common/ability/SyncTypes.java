package org.academy.api.common.ability;

import net.minecraft.resources.Identifier;

import static org.academy.AcademyCraft.academy;

public final class SyncTypes {
    public static final Identifier LEVEL = academy("level");
    public static final Identifier COMPUTING_POWER = academy("computing_power");
    public static final Identifier MAX_COMPUTING_POWER = academy("max_computing_power");
    public static final Identifier ABILITY_CATEGORY = academy("ability_category");
    public static final Identifier SKILL_DATA = academy("skill_data");

    private SyncTypes() {
    }
}