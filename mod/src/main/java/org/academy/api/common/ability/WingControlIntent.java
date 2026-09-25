package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * Held flight input. Sequence/activation validation belongs to the owning server session.
 */
public record WingControlIntent(int buttons, float yaw, float pitch, float remainingMomentum) {
    public static final StreamCodec<ByteBuf, WingControlIntent> CODEC =
            StreamCodec.of((buf, input) -> {
                buf.writeByte(input.buttons());
                buf.writeFloat(input.yaw());
                buf.writeFloat(input.pitch());
                buf.writeFloat(input.remainingMomentum());
            }, buf -> new WingControlIntent(buf.readUnsignedByte(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
    public static final long HEARTBEAT_NANOS = 200_000_000L;
    public static final long TIMEOUT_NANOS = 500_000_000L;

    public WingControlIntent(int buttons, float yaw, float pitch) {
        this(buttons, yaw, pitch, 1);
    }

    public static final int FRONT = 1, BACK = 2, LEFT = 4, RIGHT = 8, BOOST = 16;
    public static final int ALL = FRONT | BACK | LEFT | RIGHT | BOOST;

    public WingControlIntent {
        if ((buttons & ~ALL) != 0 || !Float.isFinite(yaw) || !Float.isFinite(pitch))
            throw new IllegalArgumentException("Invalid wing control input");
        yaw = (float) Math.IEEEremainder(yaw, 360);
        pitch = Math.clamp(pitch, -90, 90);
        remainingMomentum = WingFlightMotion.clampMomentum(remainingMomentum);
        if ((buttons & BOOST) != 0) buttons = BOOST;
        else {
            if ((buttons & (FRONT | BACK)) == (FRONT | BACK)) buttons &= ~(FRONT | BACK);
            if ((buttons & (LEFT | RIGHT)) == (LEFT | RIGHT)) buttons &= ~(LEFT | RIGHT);
        }
    }

    public boolean has(int button) {
        return (buttons & button) != 0;
    }

    /**
     * Change-driven client sender with a real-time heartbeat (nanosecond timestamps) and wrap-safe heading comparison.
     */
    public static final class Sender {
        private @Nullable WingControlIntent previous;
        private long lastNanos;

        public boolean shouldSend(WingControlIntent input, long nowNanos) {
            if (previous != null && nowNanos == lastNanos) return false;
            if (previous != null && nowNanos >= lastNanos && nowNanos - lastNanos < HEARTBEAT_NANOS
                    && previous.buttons == input.buttons
                    && previous.remainingMomentum == input.remainingMomentum
                    && Math.abs(Math.IEEEremainder(input.yaw - previous.yaw, 360)) < 1
                    && Math.abs(input.pitch - previous.pitch) < 1) return false;
            previous = input;
            lastNanos = nowNanos;
            return true;
        }

        public void reset() {
            previous = null;
        }
    }

    /**
     * The server consumes the newest intention once per game tick, never once per packet.
     */
    public static final class Mailbox {
        private long sequence = -1, receivedNanos;
        private @Nullable WingControlIntent held;

        public boolean accept(long incomingSequence, WingControlIntent input, long nowNanos) {
            if (incomingSequence <= sequence) return false;
            sequence = incomingSequence;
            held = input;
            receivedNanos = nowNanos;
            return true;
        }

        public WingControlIntent sample(long nowNanos, float yaw, float pitch) {
            if (held == null || nowNanos < receivedNanos || nowNanos - receivedNanos >= TIMEOUT_NANOS)
                return new WingControlIntent(0, yaw, pitch, held == null ? 1 : held.remainingMomentum);
            return held;
        }
    }
}
