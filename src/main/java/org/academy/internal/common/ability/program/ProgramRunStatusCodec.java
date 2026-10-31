package org.academy.internal.common.ability.program;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramRunStatus;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Bounded display protocol. No world data, execution inputs or authorization are carried here. */
public final class ProgramRunStatusCodec {
    public static final int MAX_BYTES = 1024;
    public static final StreamCodec<ByteBuf, ProgramRunStatus> CODEC = StreamCodec.of(
            ProgramRunStatusCodec::write, ProgramRunStatusCodec::read);

    private ProgramRunStatusCodec() {}

    public static void writeUuid(ByteBuf buffer, UUID value) {
        buffer.writeLong(value.getMostSignificantBits());
        buffer.writeLong(value.getLeastSignificantBits());
    }

    public static UUID readUuid(ByteBuf buffer) { return new UUID(buffer.readLong(), buffer.readLong()); }

    public static void writeTarget(ByteBuf buffer, ProgramRunStatus.Target value) {
        writeText(buffer, value.dimension().toString(), 128);
        writeText(buffer, value.category().toString(), 128);
        buffer.writeByte(value.slot());
        writeUuid(buffer, value.programId());
        buffer.writeLong(value.bookRevision());
    }

    public static ProgramRunStatus.Target readTarget(ByteBuf buffer) {
        try {
            return new ProgramRunStatus.Target(identifier(buffer), identifier(buffer), buffer.readUnsignedByte(),
                    readUuid(buffer), buffer.readLong());
        } catch (IllegalArgumentException invalid) { throw new DecoderException("Invalid program observation target", invalid); }
    }

    private static void write(ByteBuf buffer, ProgramRunStatus value) {
        int start = buffer.writerIndex();
        writeUuid(buffer, value.token());
        writeTarget(buffer, value.target());
        buffer.writeLong(value.runSequence()); buffer.writeLong(value.updateSequence());
        buffer.writeByte(value.phase().ordinal()); buffer.writeByte(value.waitKind().ordinal());
        buffer.writeInt(value.nodeId()); buffer.writeInt(value.sourceNodeId());
        buffer.writeBoolean(value.nodeType() != null);
        if (value.nodeType() != null) writeText(buffer, value.nodeType().toString(), 128);
        buffer.writeLong(value.logicalTick()); buffer.writeLong(value.wakeAtLogicalTick());
        buffer.writeLong(value.worldGameTick()); buffer.writeLong(value.observedServerTick());
        writeText(buffer, value.diagnosticCode(), 64); writeText(buffer, value.port(), 64);
        writeText(buffer, value.reasonCode(), 96); buffer.writeBoolean(value.rollbackIncomplete());
        if (buffer.writerIndex() - start > MAX_BYTES) throw new EncoderException("Program status exceeds wire budget");
    }

    private static ProgramRunStatus read(ByteBuf buffer) {
        int start = buffer.readerIndex();
        try {
            var result = new ProgramRunStatus(readUuid(buffer), readTarget(buffer), buffer.readLong(), buffer.readLong(),
                    enumValue(ProgramRunStatus.Phase.values(), buffer.readUnsignedByte()),
                    enumValue(ProgramRunStatus.WaitKind.values(), buffer.readUnsignedByte()),
                    buffer.readInt(), buffer.readInt(), buffer.readBoolean() ? identifier(buffer) : null,
                    buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readLong(),
                    readText(buffer, 64), readText(buffer, 64), readText(buffer, 96), buffer.readBoolean());
            if (buffer.readerIndex() - start > MAX_BYTES) throw new DecoderException("Program status exceeds wire budget");
            return result;
        } catch (IllegalArgumentException invalid) { throw new DecoderException("Invalid program status", invalid); }
    }

    private static Identifier identifier(ByteBuf buffer) {
        var value = Identifier.tryParse(readText(buffer, 128));
        if (value == null) throw new DecoderException("Invalid program status identifier");
        return value;
    }

    private static <E> E enumValue(E[] values, int ordinal) {
        if (ordinal >= values.length) throw new DecoderException("Unknown program status enum");
        return values[ordinal];
    }

    private static void writeText(ByteBuf buffer, String value, int maximum) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximum) throw new EncoderException("Program status text exceeds limit");
        ByteBufCodecs.VAR_INT.encode(buffer, bytes.length); buffer.writeBytes(bytes);
    }

    private static String readText(ByteBuf buffer, int maximum) {
        int length = ByteBufCodecs.VAR_INT.decode(buffer);
        if (length < 0 || length > maximum || length > buffer.readableBytes())
            throw new DecoderException("Invalid program status text length");
        var value = buffer.toString(buffer.readerIndex(), length, StandardCharsets.UTF_8);
        buffer.skipBytes(length);
        return value;
    }
}
