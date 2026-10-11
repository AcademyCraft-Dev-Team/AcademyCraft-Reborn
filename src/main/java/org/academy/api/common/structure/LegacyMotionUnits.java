package org.academy.api.common.structure;

import net.minecraft.world.phys.Vec3;

/**
 * Explicit conversions at the legacy motion boundary, using the nominal 20 game ticks/second.
 * These are unit conversions, not a wall-clock tick-rate or ability time-scale adjustment.
 * Legacy force values also depend on the mass model and must be calibrated separately.
 */
public final class LegacyMotionUnits {
    public static final double TICKS_PER_SECOND = 20.0;
    private static final double TICKS_PER_SECOND_SQUARED = TICKS_PER_SECOND * TICKS_PER_SECOND;

    private LegacyMotionUnits() {
    }

    public static double velocityPerSecond(double blocksPerTick) {
        return convert(blocksPerTick, TICKS_PER_SECOND);
    }

    public static Vec3 velocityPerSecond(Vec3 blocksPerTick) {
        return convert(blocksPerTick, TICKS_PER_SECOND);
    }

    public static double velocityPerTick(double blocksPerSecond) {
        return convert(blocksPerSecond, 1.0 / TICKS_PER_SECOND);
    }

    public static Vec3 velocityPerTick(Vec3 blocksPerSecond) {
        return convert(blocksPerSecond, 1.0 / TICKS_PER_SECOND);
    }

    public static double accelerationPerSecondSquared(double blocksPerTickSquared) {
        return convert(blocksPerTickSquared, TICKS_PER_SECOND_SQUARED);
    }

    public static Vec3 accelerationPerSecondSquared(Vec3 blocksPerTickSquared) {
        return convert(blocksPerTickSquared, TICKS_PER_SECOND_SQUARED);
    }

    public static double accelerationPerTickSquared(double blocksPerSecondSquared) {
        return convert(blocksPerSecondSquared, 1.0 / TICKS_PER_SECOND_SQUARED);
    }

    public static Vec3 accelerationPerTickSquared(Vec3 blocksPerSecondSquared) {
        return convert(blocksPerSecondSquared, 1.0 / TICKS_PER_SECOND_SQUARED);
    }

    private static double convert(double value, double factor) {
        var result = value * factor;
        if (!Double.isFinite(value) || !Double.isFinite(result)) {
            throw new IllegalArgumentException("Motion values and their conversions must be finite");
        }
        return result;
    }

    private static Vec3 convert(Vec3 value, double factor) {
        StructurePose.requireFinite(value, "motion");
        return new Vec3(convert(value.x, factor), convert(value.y, factor), convert(value.z, factor));
    }
}
