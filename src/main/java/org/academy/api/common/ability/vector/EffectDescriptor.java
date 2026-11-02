package org.academy.api.common.ability.vector;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Set;

/** Provider evidence for a particular effect; this is not an actor's learning or authorization. */
public record EffectDescriptor(Identifier effectId, Identifier providerId,
                               Set<EffectOperation> operations, EffectFidelity fidelity, String detail) {
    public EffectDescriptor {
        Objects.requireNonNull(effectId);
        Objects.requireNonNull(providerId);
        operations = Set.copyOf(operations);
        Objects.requireNonNull(fidelity);
        Objects.requireNonNull(detail);
        if (!operations.contains(EffectOperation.OBSERVE)) {
            throw new IllegalArgumentException("An effect must be observable");
        }
        if (fidelity == EffectFidelity.OBSERVATION_ONLY && operations.size() != 1) {
            throw new IllegalArgumentException("Observation alone cannot authorize mutation");
        }
    }

    public boolean supports(EffectOperation operation) {
        return operations.contains(operation);
    }
}
