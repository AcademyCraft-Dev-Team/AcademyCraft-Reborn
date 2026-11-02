package org.academy.api.server.ability.external;

import org.academy.api.server.ability.AbilityActorContext;

/** Server-owned admission, rechecked at prepare, start and every running tick. Never charges CP. */
@FunctionalInterface
public interface ExternalAbilityAuthority {
    /** Empty permits this request; otherwise return a diagnostic suitable for the action owner. */
    String rejectionReason(AbilityActorContext actor, ExternalAbilityRequest request);
}
