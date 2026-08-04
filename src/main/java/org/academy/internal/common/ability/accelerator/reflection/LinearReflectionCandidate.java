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
        Vec3 incomingDirection
) {
    public LinearReflectionCandidate {
        Objects.requireNonNull(reflector, "reflector");
        Objects.requireNonNull(mirrorPoint, "mirrorPoint");
        Objects.requireNonNull(incomingDirection, "incomingDirection");
    }
}
