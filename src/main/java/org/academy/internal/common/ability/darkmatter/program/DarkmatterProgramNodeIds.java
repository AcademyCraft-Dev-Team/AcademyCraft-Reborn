package org.academy.internal.common.ability.darkmatter.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

/** Stable identifiers for Darkmatter program nodes. */
public final class DarkmatterProgramNodeIds {
    public static final Identifier CASTER = id("target/caster");
    public static final Identifier LOOK_TARGET = id("target/look_target");
    public static final Identifier DISASSEMBLE_BLOCK = id("action/disassemble_block");
    public static final Identifier DISASSEMBLE_ENTITY = id("action/disassemble_entity");
    public static final Identifier DARKMATTER_CUT = id("action/darkmatter_cut");
    public static final Identifier CREATE_BEETLE = id("action/create_beetle");

    private DarkmatterProgramNodeIds() {
    }

    private static Identifier id(String path) {
        return AcademyCraft.academy("program/darkmatter/" + path);
    }
}
