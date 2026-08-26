package org.academy.internal.common.ability.accelerator.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.SkillNames;

/**
 * Learned-skill capabilities required by vector-manipulation action nodes.
 */
public final class AcceleratorProgramCapabilities {
    public static final Identifier APPLY_VECTOR =
            AcademyCraft.academy(SkillNames.VECTOR_ACCEL);
    public static final Identifier KINETIC_IMPACT =
            AcademyCraft.academy(SkillNames.KINETIC_ENERGY_APPLIED);
    public static final Identifier KINETIC_SHOCKWAVE = KINETIC_IMPACT;
    public static final Identifier REDIRECT_PROJECTILE =
            AcademyCraft.academy(SkillNames.VECTOR_REFLECTION);
    public static final Identifier DISPLACE_ENTITY = APPLY_VECTOR;
    public static final Identifier DISPLACE_BLOCK = KINETIC_IMPACT;

    private AcceleratorProgramCapabilities() {
    }
}
