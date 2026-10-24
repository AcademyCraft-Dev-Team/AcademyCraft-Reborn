package org.academy.api.server.ability;

import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.ability.Skill;
import java.util.function.Predicate;

/** Shared effect adapter. The runtime alone owns resource payment and cooldowns. */
public interface AbilitySkillExecutor {
    Skill skill();
    int minimumLevel();
    int cooldownTicks();
    double range();
    default boolean requiresTarget() { return true; }
    default boolean permanent() { return false; }
    /** Return null for an instantaneous effect, a stoppable handle for a sustained effect. */
    @org.jspecify.annotations.Nullable AbilityExecutionHandle start(AbilityActorContext actor, AbilityCastRequest request,
                                 Predicate<LivingEntity> permittedTarget);
}
