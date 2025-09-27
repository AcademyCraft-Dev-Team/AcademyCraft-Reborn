package org.academy.api.common.ability;

import net.minecraft.resources.ResourceLocation;

import static org.academy.AcademyCraft.academy;

public final class SyncTypes {
    public static final ResourceLocation LEVEL = academy("level");
    public static final ResourceLocation COMPUTING_POWER = academy("computing_power");
    public static final ResourceLocation MAX_COMPUTING_POWER = academy("max_computing_power");
    public static final ResourceLocation ABILITY_CATEGORY = academy("ability_category");
    public static final ResourceLocation SKILL_LIST = academy("skill_list");

    private SyncTypes() {
    }
}