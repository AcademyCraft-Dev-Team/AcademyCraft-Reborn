package org.academy.api.common.ability.vector;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/** An expiring server reference, never a permission token. Unloading/replacing the instance invalidates it. */
public record EffectHandle(UUID serverSession, ResourceKey<Level> dimension, UUID entityId, long generation) {
    public EffectHandle {
        Objects.requireNonNull(serverSession);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(entityId);
        if (generation <= 0) throw new IllegalArgumentException("Invalid effect generation");
    }
}
