package org.academy.api.common.vfx;

/** Tick-based staggered sampling, independent of FPS and invalidated by scope or position changes. */
public final class EffectUpdateCadence {
    private Object scope;
    private long lastTick = Long.MIN_VALUE, nextTick = Long.MIN_VALUE, sampleKey;
    private int period;
    private boolean dirty = true;
    public void invalidate() { dirty = true; }
    public boolean due(Object scope, long tick, long sampleKey, int interval, int stagger) {
        int period = Math.max(1, interval);
        boolean changed = dirty || this.scope != scope || tick < lastTick || this.sampleKey != sampleKey || this.period != period;
        lastTick = tick;
        if (!changed && tick < nextTick) return false;
        this.scope = scope; this.sampleKey = sampleKey; this.period = period; dirty = false;
        nextTick = tick + period - Math.floorMod(tick - stagger, period);
        return true;
    }
}
