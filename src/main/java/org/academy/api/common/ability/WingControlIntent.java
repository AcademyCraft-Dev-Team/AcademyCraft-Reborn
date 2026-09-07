package org.academy.api.common.ability;

/** Held flight input. Sequence/activation validation belongs to the owning server session. */
public record WingControlIntent(int buttons, float yaw, float pitch) {
    public static final int FRONT = 1, BACK = 2, LEFT = 4, RIGHT = 8, BOOST = 16;
    public static final int ALL = FRONT | BACK | LEFT | RIGHT | BOOST;
    public WingControlIntent {
        if ((buttons & ~ALL) != 0 || !Float.isFinite(yaw) || !Float.isFinite(pitch))
            throw new IllegalArgumentException("Invalid wing control input");
        yaw = (float) Math.IEEEremainder(yaw, 360);
        pitch = Math.clamp(pitch, -90, 90);
        if ((buttons & BOOST) != 0) buttons = BOOST;
        else {
            if ((buttons & (FRONT | BACK)) == (FRONT | BACK)) buttons &= ~(FRONT | BACK);
            if ((buttons & (LEFT | RIGHT)) == (LEFT | RIGHT)) buttons &= ~(LEFT | RIGHT);
        }
    }
    public boolean has(int button) { return (buttons & button) != 0; }

    /** Change-driven client sender with a four-tick heartbeat and wrap-safe heading comparison. */
    public static final class Sender {
        private WingControlIntent previous;
        private long lastTick;
        public boolean shouldSend(WingControlIntent input, long tick) {
            if (previous != null && tick == lastTick) return false;
            if (previous != null && tick >= lastTick && tick - lastTick < 4
                    && previous.buttons == input.buttons
                    && Math.abs(Math.IEEEremainder(input.yaw - previous.yaw, 360)) < 1
                    && Math.abs(input.pitch - previous.pitch) < 1) return false;
            previous = input;
            lastTick = tick;
            return true;
        }
        public void reset() { previous = null; }
    }

    /** The server consumes the newest intention once per game tick, never once per packet. */
    public static final class Mailbox {
        private long sequence = -1, receivedTick;
        private WingControlIntent held;
        public boolean accept(long incomingSequence, WingControlIntent input, long tick) {
            if (incomingSequence <= sequence) return false;
            sequence = incomingSequence;
            held = input;
            receivedTick = tick;
            return true;
        }
        public WingControlIntent sample(long tick, float yaw, float pitch) {
            if (held == null || tick < receivedTick || tick - receivedTick >= 10)
                return new WingControlIntent(0, yaw, pitch);
            return held;
        }
    }
}
