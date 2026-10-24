package org.academy.api.server.ability;

import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.ability.AbilityActorState;

/** Adapts identity/state ownership without copying an entity's state into a second store. */
public interface AbilityActorProvider {
    @org.jspecify.annotations.Nullable AbilityActorState state(LivingEntity actor);
    void setState(LivingEntity actor, AbilityActorState state);
    default void changed(LivingEntity actor) {}
    default boolean canAct(LivingEntity actor) { return actor.isAlive() && !actor.isSleeping(); }
    default boolean mayTarget(LivingEntity actor, LivingEntity target) {
        return org.academy.api.server.ability.HostileTargets.isHostile(actor, target);
    }
}
