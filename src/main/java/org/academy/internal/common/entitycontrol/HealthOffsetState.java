package org.academy.internal.common.entitycontrol;

import java.util.concurrent.ThreadLocalRandom;

/** Instance-owned state. Encoding is obfuscation, not a JVM security boundary. */
public final class HealthOffsetState {
    public final long mask = ThreadLocalRandom.current().nextLong() | 1L;
    public long encodedOffset;
    public double maximum;
    public long lastHit;
    public long advancedAt;
    public boolean dead;

    public HealthOffsetState(double maximum, double ceiling, long now) {
        this.maximum = maximum;
        encodedOffset = Double.doubleToRawLongBits(Math.max(0, maximum - ceiling)) ^ mask;
        lastHit = now;
        advancedAt = now;
    }

    /** No shared decode/set helper: each mutation owns its XOR and numeric validation. */
    public void hit(double ceiling, double maximum, long now) {
        this.maximum = maximum;
        encodedOffset = Double.doubleToRawLongBits(Math.max(0, maximum - ceiling)) ^ mask;
        lastHit = now;
        advancedAt = now;
    }

    /** Incremental linear release preserves the original deadline after healing/max changes. */
    public void advance(long now, double newMaximum) {
        if (dead) return;
        double offset = Double.longBitsToDouble(encodedOffset ^ mask);
        if (!Double.isFinite(offset) || offset < 0) offset = 0;
        double ceiling = Math.max(0, maximum - offset);
        if (Double.isFinite(newMaximum) && newMaximum > 0) {
            maximum = newMaximum;
            offset = Math.max(0, maximum - ceiling);
        }
        long end = lastHit + 400;
        long from = Math.max(lastHit + 200, advancedAt);
        if (now >= end) offset = 0;
        else if (now > from) offset *= (double) (end - now) / (end - from);
        encodedOffset = Double.doubleToRawLongBits(offset) ^ mask;
        advancedAt = Math.max(advancedAt, now);
    }

    public void heal(double amount) {
        if (dead || !Double.isFinite(amount) || amount <= 0) return;
        double offset = Double.longBitsToDouble(encodedOffset ^ mask);
        if (!Double.isFinite(offset) || offset < 0) return;
        encodedOffset = Double.doubleToRawLongBits(Math.max(0, offset - amount)) ^ mask;
    }
}
