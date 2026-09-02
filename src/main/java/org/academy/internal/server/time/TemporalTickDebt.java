package org.academy.internal.server.time;

/** Fixed-rate wall-clock debt accounting independent of Minecraft state. */
final class TemporalTickDebt {
    private final long tickNanos;
    private final long maxDebtNanos;
    private final int maxTicksPerPass;
    private long lastCheckNanos;
    private long debtNanos;
    private int lastTickCount;

    TemporalTickDebt(
            long tickNanos,
            long maxDebtNanos,
            int maxTicksPerPass,
            long lastCheckNanos,
            int lastTickCount
    ) {
        if (tickNanos <= 0L || maxDebtNanos < tickNanos || maxTicksPerPass <= 0) {
            throw new IllegalArgumentException("Invalid temporal debt limits.");
        }
        this.tickNanos = tickNanos;
        this.maxDebtNanos = maxDebtNanos;
        this.maxTicksPerPass = maxTicksPerPass;
        this.lastCheckNanos = lastCheckNanos;
        this.lastTickCount = lastTickCount;
    }

    int update(long nowNanos, int tickCount) {
        var elapsed = Math.max(0L, nowNanos - lastCheckNanos);
        var advancedTicks = Math.max(0, tickCount - lastTickCount);
        lastCheckNanos = nowNanos;
        lastTickCount = tickCount;
        debtNanos = Math.min(
                maxDebtNanos,
                Math.max(0L, debtNanos + elapsed - advancedTicks * tickNanos)
        );
        return (int) Math.min(maxTicksPerPass, debtNanos / tickNanos);
    }

    void consumeForcedTick(int tickCount) {
        debtNanos = Math.max(0L, debtNanos - tickNanos);
        lastTickCount = tickCount;
    }

    long debtNanos() {
        return debtNanos;
    }
}
