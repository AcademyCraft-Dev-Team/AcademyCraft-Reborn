package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public record VectorAttackDescriptor(
        ServerPlayer defender,
        DamageSource source,
        Vec3 origin,
        Vec3 direction,
        double range,
        double radius,
        float damage,
        VectorAttackAttribution attribution,
        VectorCompatibilityTier tier,
        VectorAttackConfidence confidence,
        VectorExecutionPolicy executionPolicy,
        long fingerprint
) {
    public VectorAttackDescriptor {
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(attribution, "attribution");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(confidence, "confidence");
        executionPolicy = executionPolicy == null
                ? VectorExecutionPolicy.safeDefault()
                : executionPolicy;
        direction = normalizeFinite(direction);
        if (!Double.isFinite(range) || range < 0.0) range = 0.0;
        range = Math.min(range, executionPolicy.maximumRange());
        if (!Double.isFinite(radius) || radius < 0.0) radius = 0.0;
        if (!Float.isFinite(damage) || damage < 0.0f) damage = 0.0f;
    }

    private static Vec3 normalizeFinite(Vec3 value) {
        var lengthSqr = value.lengthSqr();
        if (!Double.isFinite(value.x)
                || !Double.isFinite(value.y)
                || !Double.isFinite(value.z)
                || !Double.isFinite(lengthSqr)
                || lengthSqr < 1.0E-8) {
            return Vec3.ZERO;
        }
        return value.normalize();
    }

    public boolean hasConfirmedDirection() {
        return direction.lengthSqr() > 1.0E-8
                && confidence.atLeast(VectorAttackConfidence.MEDIUM);
    }
}
