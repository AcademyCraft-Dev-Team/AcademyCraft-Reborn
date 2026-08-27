package org.academy.internal.common.ability.meltdowner.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.SkillNames;

/**
 * Learned-skill capabilities required by Meltdowner action nodes.
 */
public final class MeltdownerProgramCapabilities {
    public static final Identifier ELECTRON_BEAM =
            AcademyCraft.academy(SkillNames.SINGLE_HIGH_SPEED_ELECTRON_BEAM);
    public static final Identifier MINING_BEAM = AcademyCraft.academy(SkillNames.MINING_BEAM);
    public static final Identifier ATOMIC_JET = AcademyCraft.academy(SkillNames.JET_STRIKE);

    private MeltdownerProgramCapabilities() {
    }
}
