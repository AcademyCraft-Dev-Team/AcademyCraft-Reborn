package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.structure.StructureInteractionController;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Objects;
import java.util.UUID;

/** State-only crack/progress acknowledgement; it never carries inventory, loot or NBT. */
@PacketTarget(ThreadType.CLIENT)
public final class SubLevelMiningProgressPacket extends Packet<ClientPacketListener, SubLevelMiningProgressPacket> {
    public enum Status { STARTED, PROGRESS, READY, COMPLETED, ABORTED, REJECTED }

    public static final StreamCodec<ByteBuf, SubLevelMiningProgressPacket> CODEC = new StreamCodec<>() {
        @Override public SubLevelMiningProgressPacket decode(ByteBuf input) {
            int status = input.readUnsignedByte();
            long requestId = input.readLong();
            long sequence = input.readLong();
            int actor = input.readInt();
            boolean hasTarget = input.readBoolean();
            SubLevelId id = null;
            var local = BlockPos.ZERO;
            if (hasTarget) {
                id = new SubLevelId(new UUID(input.readLong(), input.readLong()));
                local = new BlockPos(input.readInt(), input.readInt(), input.readInt());
            }
            int stage = input.readByte();
            if (status >= Status.values().length || requestId <= 0 || sequence < 0 || actor < 0
                    || stage < -1 || stage > 9 || input.isReadable()) {
                throw new IllegalArgumentException("Invalid SubLevel mining progress payload");
            }
            return new SubLevelMiningProgressPacket(Status.values()[status], requestId, sequence, actor, id, local, stage);
        }

        @Override public void encode(ByteBuf output, SubLevelMiningProgressPacket packet) {
            output.writeByte(packet.status.ordinal());
            output.writeLong(packet.requestId);
            output.writeLong(packet.sequence);
            output.writeInt(packet.actorEntityId);
            output.writeBoolean(packet.id != null);
            if (packet.id != null) {
                output.writeLong(packet.id.value().getMostSignificantBits());
                output.writeLong(packet.id.value().getLeastSignificantBits());
                output.writeInt(packet.local.getX());
                output.writeInt(packet.local.getY());
                output.writeInt(packet.local.getZ());
            }
            output.writeByte(packet.stage);
        }
    };

    private static boolean initialized;
    private final Status status;
    private final long requestId;
    private final long sequence;
    private final int actorEntityId;
    private final SubLevelId id;
    private final BlockPos local;
    private final int stage;

    public SubLevelMiningProgressPacket(Status status, long requestId, long sequence, int actorEntityId,
                                        SubLevelId id, BlockPos local, int stage) {
        this.status = Objects.requireNonNull(status, "status");
        this.id = id;
        this.local = Objects.requireNonNull(local, "local").immutable();
        this.requestId = requestId;
        this.sequence = sequence;
        this.actorEntityId = actorEntityId;
        this.stage = stage;
    }

    public Status status() { return status; }
    public long requestId() { return requestId; }
    public long sequence() { return sequence; }
    public int actorEntityId() { return actorEntityId; }
    public SubLevelId id() { return id; }
    public BlockPos local() { return local; }
    public int stage() { return stage; }

    public static void send(ServerPlayer recipient, StructureInteractionController.MiningReceipt receipt,
                            int actorEntityId) {
        var target = receipt.target();
        var id = target == null ? null : SubLevelStructureRefs.id(target.block().structure()).orElse(null);
        var status = switch (receipt.status()) {
            case STARTED -> Status.STARTED;
            case PROGRESS -> Status.PROGRESS;
            case COMPLETED -> Status.COMPLETED;
            case ABORTED -> Status.ABORTED;
            case REJECTED -> Status.REJECTED;
        };
        MisakaNetworkServer.send(recipient, new SubLevelMiningProgressPacket(status, Math.max(1, receipt.requestId()),
                Math.max(0, receipt.updateSequence()), Math.max(0, actorEntityId),
                id, target == null ? BlockPos.ZERO : target.block().localPosition(), receipt.crackStage()));
    }

    public static synchronized void initClient() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    @Override public PacketType<ClientPacketListener, SubLevelMiningProgressPacket> getPacketType() {
        return PacketTypes.SUBLEVEL_MINING_PROGRESS.get();
    }

    public static final class Client {
        @SubscribePacket public static void handle(SubLevelMiningProgressPacket packet) {
            net.minecraft.client.Minecraft.getInstance().execute(
                    () -> org.academy.internal.client.sublevel.SubLevelMiningClient.accept(packet));
        }
    }
}
