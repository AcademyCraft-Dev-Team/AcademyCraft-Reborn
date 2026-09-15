package org.academy.api.common.ability.electromaster;

/**
 * Gameplay tuning for magnetic levitation, supplied by skills, programs and entity controllers.
 *
 * <p>Height control is modelled on the clearance between the mover's feet and the ground reference below
 * it, not on a fixed anchor point. Without vertical input the solver settles back to
 * {@link #restClearance()}; vertical input shifts the target clearance by
 * {@link #inputClearanceOffset()}, so holding jump climbs and releasing lets it settle again. The
 * clearance error becomes vertical speed through {@link #followGain()}, clamped asymmetrically: rising is
 * allowed to be quicker than falling, because the ground rising into the mover is the case that must never
 * clip through.</p>
 *
 * <p>{@link #graceTicks()} is how long a mover that lost its field keeps its gravity lease and a bounded
 * sink instead of dropping immediately, giving it a chance to steer back into range.</p>
 *
 * <p>Values that the solver needs in order to do anything are required to be positive: a zero
 * {@code followGain}, climb, descent or vertical speed, or a zero input offset, would silently leave the
 * mover with no altitude correction at all — the exact failure this tuning exists to prevent — so those
 * fall back to the built-in default instead of being accepted from hand-edited config. A zero
 * {@code sinkSpeed} is allowed, because holding altitude while degraded is a legitimate choice.</p>
 */
public record LevitationTuning(
        double restClearance,
        double inputClearanceOffset,
        double followGain,
        double maxClimbSpeed,
        double maxDescentSpeed,
        double inputVerticalSpeed,
        double sinkSpeed,
        double graceSpeedFactor,
        int maxSupportSamples,
        int graceTicks
) {
    /**
     * Must stay at or above the probe's own default: a smaller budget would exhaust the sample allowance
     * before the outermost rays of a large radius are reached and report "no support" while support is
     * still within range.
     */
    public static final LevitationTuning DEFAULT = new LevitationTuning(
            1.5, 4.0, 0.40, 0.50, 0.30, 0.30, 0.25, 0.60, 1152, 6);

    public LevitationTuning {
        restClearance = nonNegative(restClearance, 1.5);
        inputClearanceOffset = positive(inputClearanceOffset, 4.0);
        followGain = positive(followGain, 0.4);
        maxClimbSpeed = positive(maxClimbSpeed, 0.5);
        maxDescentSpeed = positive(maxDescentSpeed, 0.3);
        inputVerticalSpeed = positive(inputVerticalSpeed, 0.3);
        sinkSpeed = nonNegative(sinkSpeed, 0.25);
        // Reduced but non-zero authority, so a degraded mover can still steer back into its field.
        graceSpeedFactor = Double.isFinite(graceSpeedFactor) ? Math.clamp(graceSpeedFactor, 0.0, 1.0) : 0.6;
        maxSupportSamples = Math.max(16, maxSupportSamples);
        graceTicks = Math.max(0, graceTicks);
        if (restClearance < MagneticMovement.MIN_CLEARANCE) restClearance = MagneticMovement.MIN_CLEARANCE;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double nonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0 ? value : fallback;
    }
}
