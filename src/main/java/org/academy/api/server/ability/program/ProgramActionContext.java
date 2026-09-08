package org.academy.api.server.ability.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.ability.Skill;
import java.util.List;

/** Server-owned identity and validated targets for one action. This API is not a Java sandbox. */
public record ProgramActionContext(ServerPlayer caster, Identifier category, Skill skill, int nodeId,
                                   List<LivingEntity> targets) {
    public ProgramActionContext {
        targets = List.copyOf(targets);
    }
}
