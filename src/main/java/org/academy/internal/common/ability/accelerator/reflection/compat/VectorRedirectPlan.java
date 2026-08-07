package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public record VectorRedirectPlan(
        VectorAttackDescriptor attack,
        ServerPlayer redirector,
        VectorRedirectKind kind,
        Vec3 mirrorPoint,
        Vec3 redirectedDirection,
        double redirectedLength,
        boolean damageOnly
) {
    public VectorRedirectPlan {
        Objects.requireNonNull(attack, "attack");
        Objects.requireNonNull(redirector, "redirector");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(mirrorPoint, "mirrorPoint");
        Objects.requireNonNull(redirectedDirection, "redirectedDirection");
        if (!Double.isFinite(redirectedLength) || redirectedLength < 0.0) {
            redirectedLength = 0.0;
        }
        redirectedLength = Math.min(
                redirectedLength,
                attack.executionPolicy().maximumRange()
        );
    }

    public boolean hasWorldPath() {
        return !damageOnly
                && redirectedLength > 1.0E-6
                && redirectedDirection.lengthSqr() > 1.0E-8;
    }
}
