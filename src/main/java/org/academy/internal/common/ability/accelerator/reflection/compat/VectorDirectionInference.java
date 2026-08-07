package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public record VectorDirectionInference(
        Vec3 origin,
        Vec3 direction,
        VectorAttackConfidence confidence,
        String reason
) {
    public VectorDirectionInference {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(confidence, "confidence");
        reason = Objects.requireNonNullElse(reason, "unknown");
    }

    public static VectorDirectionInference none(Vec3 fallbackOrigin, String reason) {
        return new VectorDirectionInference(
                fallbackOrigin,
                Vec3.ZERO,
                VectorAttackConfidence.NONE,
                reason
        );
    }
}
