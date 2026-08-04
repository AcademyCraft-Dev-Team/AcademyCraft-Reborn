package org.academy.internal.common.ability.accelerator.reflection;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public record LinearSegment(Vec3 start, Vec3 end) {
    private static final double MIN_LENGTH_SQR = 1.0E-12;

    public LinearSegment {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
    }

    public Vec3 delta() {
        return end.subtract(start);
    }

    public double lengthSqr() {
        return delta().lengthSqr();
    }

    public double length() {
        return Math.sqrt(lengthSqr());
    }

    public Vec3 direction() {
        var delta = delta();
        var lengthSqr = delta.lengthSqr();
        if (!(lengthSqr > MIN_LENGTH_SQR) || !Double.isFinite(lengthSqr)) return Vec3.ZERO;
        return delta.scale(1.0 / Math.sqrt(lengthSqr));
    }

    public Vec3 pointAt(double progress) {
        if (!Double.isFinite(progress)) return start;
        return start.add(delta().scale(Math.clamp(progress, 0.0, 1.0)));
    }

    public LinearSegment reversed() {
        return new LinearSegment(end, start);
    }

    public LinearSegment limitedTo(double maximumLength) {
        var currentLength = length();
        if (!Double.isFinite(maximumLength)
                || !Double.isFinite(currentLength)
                || maximumLength >= currentLength) {
            return this;
        }
        var safeLength = Math.max(0.0, maximumLength);
        return new LinearSegment(start, start.add(direction().scale(safeLength)));
    }

    public boolean isFinite() {
        var lengthSqr = lengthSqr();
        return hasFiniteCoordinates()
                && Double.isFinite(lengthSqr)
                && lengthSqr > MIN_LENGTH_SQR;
    }

    public boolean hasFiniteCoordinates() {
        return finite(start) && finite(end);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
