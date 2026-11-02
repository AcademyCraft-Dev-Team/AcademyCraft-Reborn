package org.academy.api.server.ability.external;

import net.minecraft.resources.Identifier;
import org.academy.api.server.ability.spell.SpatialCastRequest;

import java.util.Objects;

/** A learned content identity, independently of the provider's current executable binding. */
public record ExternalAbilityRequest(Identifier provider, String abilityId, SpatialCastRequest spatial,
                                     int durationTicks) {
    public ExternalAbilityRequest {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(abilityId, "abilityId");
        Objects.requireNonNull(spatial, "spatial");
        if (abilityId.isBlank() || abilityId.length() > 256 || durationTicks < 1 || durationTicks > 1200)
            throw new IllegalArgumentException("Invalid external ability request");
    }
}
