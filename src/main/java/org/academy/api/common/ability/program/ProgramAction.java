package org.academy.api.common.ability.program;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.ability.program.ProgramActionContext;
import java.util.List;

/**
 * A staged addon action. Declarations are snapshotted when submitted; validate must have no effects.
 * apply is called once after validation and charging. Irreversible effects cannot be rolled back.
 */
public interface ProgramAction {
    ResourceKey<Skill> requiredSkill();
    float cpCost();
    default List<LivingEntity> targets() { return List.of(); }
    default void validate(ProgramActionContext context) throws Exception { }
    ProgramEffect apply(ProgramActionContext context) throws Exception;
}
