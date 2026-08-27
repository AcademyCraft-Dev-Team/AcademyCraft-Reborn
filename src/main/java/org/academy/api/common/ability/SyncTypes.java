package org.academy.api.common.ability;

import net.minecraft.resources.Identifier;

import static org.academy.AcademyCraft.academy;

public final class SyncTypes {
    public static final Identifier CP_DATA = academy("cp_data");
    public static final Identifier ABILITY_CATEGORY = academy("ability_category");
    public static final Identifier SKILL_DATA = academy("skill_data");
    public static final Identifier PROPS_DATA = academy("props_data");
    public static final Identifier DARKMATTER_STATE = academy("darkmatter_state");
    /** Tick-only subsystem route; compressed air itself is synchronized through CP_DATA. */
    public static final Identifier AEROMANIP_RESOURCE = academy("aeromanip_resource");

    private SyncTypes() {
    }
}
