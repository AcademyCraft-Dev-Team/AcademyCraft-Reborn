package org.academy.internal.common.ability.program;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DuplicatedByteBuf;
import io.netty.buffer.Unpooled;
import org.misaka.api.common.network.NetworkSystem;
import org.misaka.api.common.network.packet.C2SPacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.S2CPacket;

import java.lang.reflect.Field;

/** Real Misaka envelope codecs with explicit fixture ownership of all temporary/body buffers. */
final class ProgramNetworkEnvelopeProbe {
    private static final Field S2C_BODY = field(S2CPacket.class, "byteBuf");
    private static final Field C2S_BODY = field(C2SPacket.class, "byteBuf");
    private static final Field S2C_ID = field(S2CPacket.class, "id");
    private static final Field C2S_ID = field(C2SPacket.class, "id");

    private ProgramNetworkEnvelopeProbe() {}
    record Observation(int payloadBytes, int envelopeBytes, Packet<?, ?> packet) {}

    static Observation roundTrip(Packet<?, ?> packet) {
        Object sent = wrap(packet), received = null;
        var buffer = new OwnedCopyBuffer(Unpooled.buffer());
        try {
            if (sent instanceof S2CPacket wrapper) S2CPacket.STREAM_CODEC.encode(buffer, wrapper);
            else C2SPacket.STREAM_CODEC.encode(buffer, (C2SPacket) sent);
            int envelopeBytes = buffer.readableBytes();
            received = sent instanceof S2CPacket ? S2CPacket.STREAM_CODEC.decode(buffer) : C2SPacket.STREAM_CODEC.decode(buffer);
            var body = body(received).asReadOnly();
            int payloadBytes = body.readableBytes();
            int id = id(received);
            if (id != packet.getPacketType().getPacketId()) throw new IllegalStateException("Misaka registry id changed");
            var decoded = (Packet<?, ?>) NetworkSystem.getPacketTypeById(id).codec().decode(body);
            if (body.isReadable() || buffer.isReadable()) throw new IllegalStateException("Envelope codec left bytes");
            return new Observation(payloadBytes, envelopeBytes, decoded);
        } finally {
            buffer.release(); body(sent).release();
            if (received != null) body(received).release();
        }
    }

    private static Object wrap(Packet<?, ?> packet) {
        if (packet instanceof AbilityProgramManager.RequestPacket value) return new C2SPacket(value);
        if (packet instanceof AbilityProgramManager.ImportPacket value) return new C2SPacket(value);
        if (packet instanceof AbilityProgramManager.SavePacket value) return new C2SPacket(value);
        if (packet instanceof AbilityProgramManager.ExecutePacket value) return new C2SPacket(value);
        if (packet instanceof AbilityProgramManager.DismissStarterPacket value) return new C2SPacket(value);
        if (packet instanceof AbilityProgramManager.SyncPacket value) return new S2CPacket(value);
        if (packet instanceof AbilityProgramManager.ResultPacket value) return new S2CPacket(value);
        throw new IllegalArgumentException("Not a program packet");
    }

    private static Field field(Class<?> type, String name) {
        try { var value = type.getDeclaredField(name); value.setAccessible(true); return value; }
        catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }
    private static ByteBuf body(Object wrapper) {
        try { return (ByteBuf) (wrapper instanceof S2CPacket ? S2C_BODY : C2S_BODY).get(wrapper); }
        catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
    }
    private static int id(Object wrapper) {
        try { return (wrapper instanceof S2CPacket ? S2C_ID : C2S_ID).getInt(wrapper); }
        catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
    }

    /** Misaka.write passes an owned copy to writeBytes; consume and release that copy in this probe. */
    @SuppressWarnings("deprecation")
    private static final class OwnedCopyBuffer extends DuplicatedByteBuf {
        private OwnedCopyBuffer(ByteBuf buffer) { super(buffer); }
        @Override public ByteBuf writeBytes(ByteBuf source) {
            try { return super.writeBytes(source); }
            finally { source.release(); }
        }
    }
}
