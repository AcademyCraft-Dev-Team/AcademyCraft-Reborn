package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import org.academy.internal.client.structure.NativeStructureClientInteraction;
import org.academy.internal.common.structure.space.NativeStructureInteraction;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Objects;

/** A reply to one consumed use request; contains no storage or inventory data. */
@PacketTarget(ThreadType.CLIENT)
public final class NativeStructureUseResultPacket extends Packet<ClientPacketListener, NativeStructureUseResultPacket> {
    public enum Status {
        APPLIED, DENIED, STALE, UNSUPPORTED, BUSY, REPLAYED;

        static Status from(NativeStructureInteraction.Result result) {
            return switch (result) {
                case APPLIED -> APPLIED;
                case DENIED -> DENIED;
                case STALE -> STALE;
                case UNSUPPORTED -> UNSUPPORTED;
                case BUSY -> BUSY;
            };
        }
    }

    public static final StreamCodec<ByteBuf, NativeStructureUseResultPacket> CODEC = new StreamCodec<>() {
        @Override public NativeStructureUseResultPacket decode(ByteBuf input) {
            long requestId = input.readLong();
            int status = input.readUnsignedByte();
            if (status >= Status.values().length || input.isReadable()) {
                throw new IllegalArgumentException("Invalid structure use acknowledgement");
            }
            return new NativeStructureUseResultPacket(requestId, Status.values()[status]);
        }

        @Override public void encode(ByteBuf output, NativeStructureUseResultPacket packet) {
            output.writeLong(packet.requestId);
            output.writeByte(packet.status.ordinal());
        }
    };

    private static boolean initialized;
    private final long requestId;
    private final Status status;

    public NativeStructureUseResultPacket(long requestId, Status status) {
        if (requestId <= 0) throw new IllegalArgumentException("Invalid structure use request identity");
        this.requestId = requestId;
        this.status = Objects.requireNonNull(status, "status");
    }

    public long requestId() { return requestId; }
    public Status status() { return status; }

    public static void send(ServerPlayer recipient, long requestId, Status status) {
        MisakaNetworkServer.send(recipient, new NativeStructureUseResultPacket(requestId, status));
    }

    public static synchronized void initClient() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    @Override public PacketType<ClientPacketListener, NativeStructureUseResultPacket> getPacketType() {
        return PacketTypes.NATIVE_STRUCTURE_USE_RESULT.get();
    }

    public static final class Client {
        @SubscribePacket public static void handle(NativeStructureUseResultPacket packet) {
            NativeStructureClientInteraction.accept(packet);
        }
    }
}
