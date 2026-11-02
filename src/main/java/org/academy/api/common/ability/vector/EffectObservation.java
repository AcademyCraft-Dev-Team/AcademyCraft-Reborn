package org.academy.api.common.ability.vector;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable server sample. Positions are world blocks; velocity is world blocks per game tick. */
public record EffectObservation(EffectHandle handle, EffectDescriptor descriptor, long gameTime,
                                Vec3 position, Vec3 velocity, Optional<UUID> ownerId) {
    public EffectObservation {
        Objects.requireNonNull(handle);
        Objects.requireNonNull(descriptor);
        Objects.requireNonNull(position);
        Objects.requireNonNull(velocity);
        Objects.requireNonNull(ownerId);
    }
}
