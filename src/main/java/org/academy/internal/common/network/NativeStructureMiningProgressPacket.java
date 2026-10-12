package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.client.structure.NativeStructureMiningClient;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Objects;
import java.util.UUID;

/** Bounded state-only acknowledgement and observer animation; never includes inventory or NBT. */
@PacketTarget(ThreadType.CLIENT)
public final class NativeStructureMiningProgressPacket extends Packet<ClientPacketListener, NativeStructureMiningProgressPacket> {
    public enum Status { STARTED, PROGRESS, READY, COMPLETED, ABORTED, REJECTED }

    public static final StreamCodec<ByteBuf, NativeStructureMiningProgressPacket> CODEC = new StreamCodec<>() {
        @Override public NativeStructureMiningProgressPacket decode(ByteBuf input) {
            var buffer = new FriendlyByteBuf(input);
            long requestId = buffer.readLong();
            long updateSequence = buffer.readLong();
            int breaker = buffer.readInt();
            var reference = new StructureRef(buffer.readUUID(), Identifier.parse(buffer.readUtf(256)),
                    Identifier.parse(buffer.readUtf(256)), buffer.readLong());
            var runtimeId = buffer.readUUID();
            var position = buffer.readBlockPos();
            int stateId = buffer.readInt();
            int stage = buffer.readByte();
            int status = buffer.readUnsignedByte();
            if (status >= Status.values().length || buffer.isReadable()) throw new IllegalArgumentException("Invalid mining update payload");
            return new NativeStructureMiningProgressPacket(requestId, updateSequence, breaker, reference,
                    runtimeId, position, stateId, stage, Status.values()[status]);
        }

        @Override public void encode(ByteBuf output, NativeStructureMiningProgressPacket packet) {
            var buffer = new FriendlyByteBuf(output);
            buffer.writeLong(packet.requestId);
            buffer.writeLong(packet.updateSequence);
            buffer.writeInt(packet.breakerEntityId);
            buffer.writeUUID(packet.reference.id());
            buffer.writeUtf(packet.reference.dimension().toString(), 256);
            buffer.writeUtf(packet.reference.backend().toString(), 256);
            buffer.writeLong(packet.reference.generation());
            buffer.writeUUID(packet.runtimeId);
            buffer.writeBlockPos(packet.localPosition);
            buffer.writeInt(packet.stateId);
            buffer.writeByte(packet.stage);
            buffer.writeByte(packet.status.ordinal());
        }
    };
    private static boolean initialized;
    private final long requestId, updateSequence;
    private final int breakerEntityId, stateId, stage;
    private final StructureRef reference;
    private final UUID runtimeId;
    private final BlockPos localPosition;
    private final Status status;

    public NativeStructureMiningProgressPacket(long requestId, long updateSequence, int breakerEntityId,
                                               StructureRef reference, UUID runtimeId, BlockPos localPosition,
                                               int stateId, int stage, Status status) {
        this.reference = Objects.requireNonNull(reference, "reference");
        this.runtimeId = Objects.requireNonNull(runtimeId, "runtimeId");
        this.localPosition = Objects.requireNonNull(localPosition, "localPosition").immutable();
        this.status = Objects.requireNonNull(status, "status");
        if (requestId <= 0 || updateSequence < 0 || breakerEntityId < 0 || stateId < 0 || stateId > 0xFFFFFF
                || stage < -1 || stage > 9 || !NativeStructureDimensions.contains(localPosition)
                || localPosition.getY() < -64 || localPosition.getY() > 319) {
            throw new IllegalArgumentException("Invalid mining update");
        }
        this.requestId = requestId;
        this.updateSequence = updateSequence;
        this.breakerEntityId = breakerEntityId;
        this.stateId = stateId;
        this.stage = stage;
    }

    public long requestId() { return requestId; }
    public long updateSequence() { return updateSequence; }
    public int breakerEntityId() { return breakerEntityId; }
    public StructureRef reference() { return reference; }
    public UUID runtimeId() { return runtimeId; }
    public BlockPos localPosition() { return localPosition; }
    public int stateId() { return stateId; }
    public int stage() { return stage; }
    public Status status() { return status; }

    public static void send(ServerPlayer recipient, long requestId, long updateSequence, int breakerEntityId,
                            StructureRef reference, UUID runtimeId, BlockPos localPosition,
                            int stateId, int stage, Status status) {
        MisakaNetworkServer.send(recipient, new NativeStructureMiningProgressPacket(requestId, updateSequence,
                breakerEntityId, reference, runtimeId, localPosition, stateId, stage, status));
    }

    public static synchronized void initClient() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    @Override public PacketType<ClientPacketListener, NativeStructureMiningProgressPacket> getPacketType() {
        return PacketTypes.NATIVE_STRUCTURE_MINING_PROGRESS.get();
    }

    public static final class Client {
        @SubscribePacket public static void handle(NativeStructureMiningProgressPacket packet) {
            NativeStructureMiningClient.accept(packet);
        }
    }
}
