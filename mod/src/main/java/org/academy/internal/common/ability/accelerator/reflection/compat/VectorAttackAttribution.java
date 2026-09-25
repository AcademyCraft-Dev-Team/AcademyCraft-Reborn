package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public record VectorAttackAttribution(
        @Nullable Entity originalAttacker,
        @Nullable Entity directEntity,
        String damageTypeId,
        boolean nativeExact
) {
    public VectorAttackAttribution {
        damageTypeId = Objects.requireNonNullElse(damageTypeId, "unknown");
    }

    public Optional<Entity> originalAttackerEntity() {
        return Optional.ofNullable(originalAttacker);
    }

    public Optional<Entity> directEntityOptional() {
        return Optional.ofNullable(directEntity);
    }
}
