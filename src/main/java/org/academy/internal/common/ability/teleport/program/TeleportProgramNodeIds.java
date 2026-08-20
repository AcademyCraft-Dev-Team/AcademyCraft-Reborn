package org.academy.internal.common.ability.teleport.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

/** Stable identifiers for Teleport program nodes. */
public final class TeleportProgramNodeIds {
    public static final Identifier CASTER = id("target/caster");
    public static final Identifier LOOK_TARGET = id("target/look_target");
    public static final Identifier SELF_TELEPORT = id("action/self_teleport");
    public static final Identifier ENTITY_TELEPORT = id("action/entity_teleport");
    public static final Identifier BLOCK_ITEM_TELEPORT = id("action/block_item_teleport");
    public static final Identifier SPACE_SAFETY = id("logic/space_safety");

    private TeleportProgramNodeIds() {
    }

    private static Identifier id(String path) {
        return AcademyCraft.academy("program/teleport/" + path);
    }
}
