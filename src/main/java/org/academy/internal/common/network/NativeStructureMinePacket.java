package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.academy.api.common.structure.StructureHit;
import org.academy.internal.common.structure.space.NativeStructureMiningSessions;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Objects;

/** Input intent only; no client duration, progress or replacement block is accepted. */
@PacketTarget(ThreadType.SERVER)
public final class NativeStructureMinePacket extends Packet<ServerGamePacketListenerImpl, NativeStructureMinePacket> {
    public enum Action { START, HOLD, STOP, ABORT }

    public static final StreamCodec<ByteBuf, NativeStructureMinePacket> CODEC = new StreamCodec<>() {
        @Override public NativeStructureMinePacket decode(ByteBuf input) {
            var buffer = new FriendlyByteBuf(input);
            int action = buffer.readUnsignedByte();
            if (action >= Action.values().length) throw new IllegalArgumentException("Invalid mining action");
            long requestId = buffer.readLong();
            StructureHit hit = null;
            if (action == Action.START.ordinal()) {
                var observation = NativeStructureUsePacket.CODEC.decode(buffer);
                if (observation.hand() != InteractionHand.MAIN_HAND) throw new IllegalArgumentException("Mining requires main hand");
                hit = observation.hit();
            }
            if (buffer.isReadable()) throw new IllegalArgumentException("Unexpected mining payload");
            return new NativeStructureMinePacket(Action.values()[action], requestId, hit);
        }

        @Override public void encode(ByteBuf output, NativeStructureMinePacket packet) {
            output.writeByte(packet.action.ordinal());
            output.writeLong(packet.requestId);
            if (packet.startHit != null) NativeStructureUsePacket.CODEC.encode(output, new NativeStructureUsePacket(packet.startHit));
        }
    };
    private static boolean initialized;
    private final Action action;
    private final long requestId;
    private final @Nullable StructureHit startHit;

    public NativeStructureMinePacket(Action action, long requestId, @Nullable StructureHit startHit) {
        this.action = Objects.requireNonNull(action, "action");
        if (requestId <= 0 || (action == Action.START) != (startHit != null)) {
            throw new IllegalArgumentException("Invalid mining request identity or observation");
        }
        this.requestId = requestId;
        this.startHit = startHit;
    }

    public Action action() { return action; }
    public long requestId() { return requestId; }
    public @Nullable StructureHit startHit() { return startHit; }

    public static synchronized void initServer() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override public PacketType<ServerGamePacketListenerImpl, NativeStructureMinePacket> getPacketType() {
        return PacketTypes.NATIVE_STRUCTURE_MINE.get();
    }

    public static final class Server {
        @SubscribePacket public static void handle(NativeStructureMinePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            switch (packet.action) {
                case START -> NativeStructureMiningSessions.start(player, packet.requestId, Objects.requireNonNull(packet.startHit));
                case HOLD -> NativeStructureMiningSessions.hold(player, packet.requestId);
                case STOP -> NativeStructureMiningSessions.stop(player, packet.requestId);
                case ABORT -> NativeStructureMiningSessions.abort(player, packet.requestId);
            }
        }
    }
}
