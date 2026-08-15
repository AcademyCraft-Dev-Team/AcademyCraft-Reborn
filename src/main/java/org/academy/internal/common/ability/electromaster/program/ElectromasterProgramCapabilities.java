package org.academy.internal.common.ability.electromaster.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.SkillNames;

/** Learned-skill capabilities required by Electromaster action nodes. */
public final class ElectromasterProgramCapabilities {
    public static final Identifier ARC_DISCHARGE =
            AcademyCraft.academy(SkillNames.ARC_GENERATE);
    public static final Identifier MAGNETIC_MOVE =
            AcademyCraft.academy(SkillNames.MAGNET_MANIPULATION);
    public static final Identifier CURRENT_RECHARGE =
            AcademyCraft.academy(SkillNames.CURRENT_RECHARGE);

    private ElectromasterProgramCapabilities() {
    }
}
