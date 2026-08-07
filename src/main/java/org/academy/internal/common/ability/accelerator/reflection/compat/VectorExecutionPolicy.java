package org.academy.internal.common.ability.accelerator.reflection.compat;

public record VectorExecutionPolicy(
        boolean piercing,
        boolean continuous,
        boolean safeMotionRedirect,
        VectorBlockPolicy blockPolicy,
        VectorVisualStyle visualStyle,
        int maximumTargets,
        double maximumRange
) {
    public static final double DEFAULT_MAXIMUM_RANGE = 96.0;
    public static final double HARD_MAXIMUM_RANGE = 128.0;
    public static final int HARD_MAXIMUM_TARGETS = 64;

    public VectorExecutionPolicy {
        blockPolicy = blockPolicy == null ? VectorBlockPolicy.CLIP_NO_BREAK : blockPolicy;
        visualStyle = visualStyle == null ? VectorVisualStyle.ENERGY : visualStyle;
        maximumTargets = Math.clamp(maximumTargets, 1, HARD_MAXIMUM_TARGETS);
        if (!Double.isFinite(maximumRange) || maximumRange <= 0.0) {
            maximumRange = DEFAULT_MAXIMUM_RANGE;
        }
        maximumRange = Math.min(maximumRange, HARD_MAXIMUM_RANGE);
    }

    public static VectorExecutionPolicy safeDefault() {
        return new VectorExecutionPolicy(
                false,
                false,
                false,
                VectorBlockPolicy.CLIP_NO_BREAK,
                VectorVisualStyle.ENERGY,
                1,
                DEFAULT_MAXIMUM_RANGE
        );
    }
}
