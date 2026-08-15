package org.academy.internal.common.ability.darkmatter.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.SkillNames;

/** Learned-skill capabilities required by Darkmatter action nodes. */
public final class DarkmatterProgramCapabilities {
    public static final Identifier DISASSEMBLE_BLOCK =
            AcademyCraft.academy(SkillNames.DARKMATTER_DISASSEMBLE);
    public static final Identifier DISASSEMBLE_ENTITY =
            AcademyCraft.academy(SkillNames.DARKMATTER_DISASSEMBLE);
    public static final Identifier DARKMATTER_CUT =
            AcademyCraft.academy(SkillNames.DARKMATTER_CUT);
    public static final Identifier CREATE_BEETLE =
            AcademyCraft.academy(SkillNames.DARKMATTER_CREATION);

    private DarkmatterProgramCapabilities() {
    }
}
