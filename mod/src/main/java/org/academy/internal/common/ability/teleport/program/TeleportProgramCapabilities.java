package org.academy.internal.common.ability.teleport.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.SkillNames;

/**
 * Learned-skill capabilities required by Teleport action nodes.
 */
public final class TeleportProgramCapabilities {
    public static final Identifier SELF_TELEPORT =
            AcademyCraft.academy(SkillNames.SELF_TELEPORT);
    public static final Identifier ENTITY_TELEPORT =
            AcademyCraft.academy(SkillNames.QUICK_LOCATION_TELEPORT);

    private TeleportProgramCapabilities() {
    }
}
