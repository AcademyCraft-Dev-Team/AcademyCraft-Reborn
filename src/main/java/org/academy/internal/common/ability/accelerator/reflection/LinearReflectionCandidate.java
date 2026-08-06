package org.academy.internal.common.ability.accelerator.reflection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public record LinearReflectionCandidate(
        ServerPlayer reflector,
        Vec3 mirrorPoint,
        double progress,
        double expandedEntryProgress,
        float expectedDamage,
        Vec3 incomingDirection,
        Mode mode
) {
    public LinearReflectionCandidate {
        Objects.requireNonNull(reflector, "reflector");
        Objects.requireNonNull(mirrorPoint, "mirrorPoint");
        Objects.requireNonNull(incomingDirection, "incomingDirection");
        Objects.requireNonNull(mode, "mode");
    }

    public enum Mode {
        REFLECTION,
        REFRACTION,
        ELECTROMAGNETIC_SHIELD_REFRACTION,
        LIGHT_SHIELD_REFRACTION
    }
}
