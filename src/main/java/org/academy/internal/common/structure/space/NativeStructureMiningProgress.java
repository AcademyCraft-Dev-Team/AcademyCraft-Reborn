package org.academy.internal.common.structure.space;

/** Server-tick accounting. Client heartbeats and STOP never grant progress. */
final class NativeStructureMiningProgress {
    static final long LEASE_TICKS = 40;
    private long lastTick = Long.MIN_VALUE;
    private long lastHold;
    private double progress;
    private boolean stopRequested;

    NativeStructureMiningProgress(long tick) { lastHold = tick; }

    void hold(long tick) { if (tick >= lastHold) lastHold = tick; }
    void stop() { stopRequested = true; }
    boolean expired(long tick) { return tick < lastHold || tick - lastHold > LEASE_TICKS; }
    boolean ready() { return progress >= 1; }
    double value() { return progress; }
    boolean shouldFinish() { return stopRequested && ready(); }
    int stage() { return Math.min(9, (int) (progress * 10)); }

    boolean advance(long tick, boolean actionTick, double delta) {
        if (tick <= lastTick) return false;
        lastTick = tick;
        if (!actionTick || !Double.isFinite(delta) || delta <= 0 || ready()) return false;
        progress = Math.min(1, progress + delta);
        return true;
    }

    static double increment(float hardness, float speed, boolean harvest) {
        if (!Float.isFinite(hardness) || hardness < 0 || !Float.isFinite(speed) || speed < 0) return 0;
        if (hardness == 0) return 1;
        return (double) speed / hardness / (harvest ? 30 : 100);
    }
}
