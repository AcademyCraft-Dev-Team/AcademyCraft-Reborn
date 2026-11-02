package org.academy.api.server.ability.external;

/** Server-owned, single-use preflight result; callers settle their entry-point cost before start. */
public interface ExternalAbilityPlan {
    ExternalAbilityDescriptor descriptor();
    ExternalAbilityRequest request();
    ExternalAbilityExecution start();
}
