package org.academy.api.server.ability.external;

import net.minecraft.resources.Identifier;
import org.academy.api.server.ability.AbilityActorContext;
import org.academy.api.server.ability.AbilityExecutionHandle;

import java.util.Optional;

/**
 * Shared player/NPC/precision-operation contract. Describe and prepare must not change the world.
 * Analysis, compilation and retransformation happen before this API's execution path. Providers
 * preserve native costs, ownership and hit admission; Academy CP belongs to the calling entry point.
 */
public interface ExternalAbilityProvider {
    Identifier id();
    Optional<ExternalAbilityDescriptor> describe(AbilityActorContext actor, String abilityId);
    Prepared prepare(AbilityActorContext actor, ExternalAbilityRequest request);

    interface Prepared {
        /** Must equal the generation advertised by describe when prepared. */
        long generation();
        /** Called once on the server thread. Clean up owned resources if starting throws. */
        AbilityExecutionHandle start();
    }
}
