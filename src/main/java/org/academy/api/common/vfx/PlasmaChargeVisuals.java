package org.academy.api.common.vfx;

/** Shared charge/launch shape, independent of client rendering and entity ownership. */
public final class PlasmaChargeVisuals {
    public static final float FOCUS_START = 0.025f;
    public static final float CONVERGENCE_END = 0.25f;
    public static final float FORMATION_AT_CONVERGENCE = 0.62f;

    private PlasmaChargeVisuals() {}

    public static float convergence(float progress) {
        return smooth((progress - FOCUS_START) / (CONVERGENCE_END - FOCUS_START));
    }

    public static float formation(float progress) {
        if (progress <= CONVERGENCE_END) return convergence(progress) * FORMATION_AT_CONVERGENCE;
        return FORMATION_AT_CONVERGENCE + (1f - FORMATION_AT_CONVERGENCE)
                * smooth((progress - CONVERGENCE_END) / (1f - CONVERGENCE_END));
    }

    private static float smooth(float value) {
        float t = Float.isFinite(value) ? Math.clamp(value, 0f, 1f) : 0f;
        return t * t * (3f - 2f * t);
    }
}
