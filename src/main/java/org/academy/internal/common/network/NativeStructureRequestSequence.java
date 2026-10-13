package org.academy.internal.common.network;

/** One connection's monotonic request watermark; consume before invoking any action callback. */
public final class NativeStructureRequestSequence {
    private long highest;
    private long acknowledged;

    public NativeStructureRequestSequence() {}

    NativeStructureRequestSequence(long highest) {
        if (highest < 0) throw new IllegalArgumentException("Invalid request watermark");
        this.highest = highest;
    }

    public long next() {
        if (highest == Long.MAX_VALUE) throw new IllegalStateException("Structure request sequence exhausted");
        return ++highest;
    }

    public boolean consume(long requestId) {
        if (requestId <= highest || requestId <= 0) return false;
        highest = requestId;
        return true;
    }

    public long highest() { return highest; }

    /** Reject unsolicited, repeated or out-of-order replies without changing the watermark. */
    public boolean acknowledge(long requestId) {
        if (requestId <= acknowledged || requestId > highest) return false;
        acknowledged = requestId;
        return true;
    }
}
