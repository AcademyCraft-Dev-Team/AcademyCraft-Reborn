package org.academy.internal.client.renderer.entity;

import net.minecraft.world.phys.Vec3;

public final class ReflectedBeamVisualGeometry {
    private static final double DIRECTION_EPSILON_SQUARED = 1.0e-12;

    private ReflectedBeamVisualGeometry() {
    }

    public static float safeLength(float length) {
        return Float.isFinite(length) ? Math.max(0.0f, length) : 0.0f;
    }

    public static Vec3 directionalEnd(Vec3 reflectionPoint, Vec3 direction, float length) {
        var returnLength = safeLength(length);
        if (returnLength == 0.0f) return reflectionPoint;
        var directionLengthSqr = direction.lengthSqr();
        if (!(directionLengthSqr > DIRECTION_EPSILON_SQUARED) || !Double.isFinite(directionLengthSqr)) {
            return reflectionPoint;
        }
        return reflectionPoint.add(direction.normalize().scale(returnLength));
    }
}
