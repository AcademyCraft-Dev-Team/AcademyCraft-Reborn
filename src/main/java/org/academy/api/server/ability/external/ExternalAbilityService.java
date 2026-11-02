package org.academy.api.server.ability.external;

import org.academy.api.server.ability.AbilityActorContext;
import org.academy.internal.common.ability.external.ExternalAbilityRuntime;

import java.util.Optional;

/** Public facade for learned native abilities. No method charges Academy resources. */
public final class ExternalAbilityService {
    private ExternalAbilityService() { }

    public static void registerProvider(ExternalAbilityProvider provider) {
        ExternalAbilityRuntime.register(provider);
    }

    public static Optional<ExternalAbilityDescriptor> describe(AbilityActorContext actor,
                                                               ExternalAbilityRequest request) {
        return ExternalAbilityRuntime.describe(actor, request);
    }

    public static ExternalAbilityPlan prepare(AbilityActorContext actor, ExternalAbilityAuthority authority,
                                              ExternalAbilityRequest request) {
        return ExternalAbilityRuntime.prepare(actor, authority, request);
    }
}
