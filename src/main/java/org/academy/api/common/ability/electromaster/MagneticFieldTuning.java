package org.academy.api.common.ability.electromaster;

/** Magnetic field values shared by skills, programs and entity controllers. */
public final class MagneticFieldTuning {
    public static final double TARGET_PULL_SPEED_PER_TICK = 2.3;
    public static final double FLIGHT_SPEED_PER_TICK = 1.8;

    private MagneticFieldTuning() {}

    public static double supportRadius(int milestone) {
        return switch (Math.clamp(milestone, 0, 3)) {
            case 1 -> 6;
            case 2 -> 8;
            case 3 -> 16;
            default -> 4;
        };
    }

    public static double targetPullRange(int milestone) { return milestone >= 2 ? 60 : 48; }
    public static double targetPullSpeed(int milestone) {
        return TARGET_PULL_SPEED_PER_TICK * (milestone >= 2 ? 1.15 : 1.0);
    }
}
