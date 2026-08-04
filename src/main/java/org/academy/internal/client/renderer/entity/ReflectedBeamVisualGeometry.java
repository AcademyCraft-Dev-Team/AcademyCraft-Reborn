package org.academy.internal.client.renderer.entity;

import net.minecraft.world.phys.Vec3;

final class ReflectedBeamVisualGeometry {
    private static final double DIRECTION_EPSILON_SQUARED = 1.0e-12;

    private ReflectedBeamVisualGeometry() {
    }

    static float safeLength(float length) {
        return Float.isFinite(length) ? Math.max(0.0f, length) : 0.0f;
    }

    static Vec3 fullReturnEnd(Vec3 reflectionPoint, Vec3 forwardDirection, float length) {
        var returnLength = safeLength(length);
        if (returnLength == 0.0f) return reflectionPoint;

        var directionLengthSqr = forwardDirection.lengthSqr();
        if (!(directionLengthSqr > DIRECTION_EPSILON_SQUARED) || !Double.isFinite(directionLengthSqr)) {
            return reflectionPoint;
        }

        return reflectionPoint.subtract(forwardDirection.normalize().scale(returnLength));
    }
}
