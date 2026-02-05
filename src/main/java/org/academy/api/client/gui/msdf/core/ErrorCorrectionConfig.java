package org.academy.api.client.gui.msdf.core;

public class ErrorCorrectionConfig {
    public static final double defaultMinDeviationRatio = 1.11111111111111111;
    public static final double defaultMinImproveRatio = 1.11111111111111111;
    public Mode mode;
    public DistanceCheckMode distanceCheckMode;
    public double minDeviationRatio;
    public double minImproveRatio;
    public byte[] buffer;

    public ErrorCorrectionConfig() {
        this(Mode.EDGE_PRIORITY, DistanceCheckMode.CHECK_DISTANCE_AT_EDGE, defaultMinDeviationRatio, defaultMinImproveRatio, null);
    }

    public ErrorCorrectionConfig(Mode mode, DistanceCheckMode distanceCheckMode, double minDeviationRatio, double minImproveRatio, byte[] buffer) {
        this.mode = mode;
        this.distanceCheckMode = distanceCheckMode;
        this.minDeviationRatio = minDeviationRatio;
        this.minImproveRatio = minImproveRatio;
        this.buffer = buffer;
    }

    public enum Mode {
        DISABLED,
        INDISCRIMINATE,
        EDGE_PRIORITY,
        EDGE_ONLY
    }

    public enum DistanceCheckMode {
        DO_NOT_CHECK_DISTANCE,
        CHECK_DISTANCE_AT_EDGE,
        ALWAYS_CHECK_DISTANCE
    }
}
