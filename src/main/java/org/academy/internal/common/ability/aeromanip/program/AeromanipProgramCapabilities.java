package org.academy.internal.common.ability.aeromanip.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.SkillNames;

/**
 * Learned-skill capabilities required by Aeromanip action nodes.
 */
public final class AeromanipProgramCapabilities {
    public static final Identifier AIRFLOW_PUSH =
            AcademyCraft.academy(SkillNames.PNEUMATIC_GRASP);
    public static final Identifier LAMINAR_CUT =
            AcademyCraft.academy(SkillNames.LAMINAR_CUTTER);

    private AeromanipProgramCapabilities() {
    }
}
