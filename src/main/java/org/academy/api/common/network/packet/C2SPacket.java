package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.event.C2SPacketEvent;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.ApiStatus;

public final class C2SPacket implements net.minecraft.network.protocol.Packet<ServerGamePacketListenerImpl> {
    public static final PacketType<C2SPacket> TYPE = new PacketType<>(PacketFlow.SERVERBOUND, AcademyCraft.academy("c2s_packet"));
    public static final StreamCodec<FriendlyByteBuf, C2SPacket> STREAM_CODEC = net.minecraft.network.protocol.Packet.codec(
            C2SPacket::write, C2SPacket::new
    );

    private final int id;
    private final FriendlyByteBuf friendlyByteBuf;

    public <L extends ServerGamePacketListenerImpl, T extends Packet<L, T>> C2SPacket(T packet) {
        id = packet.getPacketType().getPacketId();
        this.friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());

        var startTime = NetworkSystem.debugInfo ? System.nanoTime() : 0;
        packet.getPacketType().codec().encode(friendlyByteBuf, packet);

        if (NetworkSystem.debugInfo) {
            var endTime = System.nanoTime();
            var packetSize = friendlyByteBuf.readableBytes();
            AcademyCraft.LOGGER.info(
                    "[AC-NET-DEBUG][SEND][C2S] Packet: {}(ID: {}), Size: {} bytes, Encode Time: {} ns",
                    packet.getClass().getSimpleName(),
                    id,
                    packetSize,
                    endTime - startTime
            );
        }
    }

    @ApiStatus.Internal
    private C2SPacket(FriendlyByteBuf newFriendlyByteBuf) {
        id = newFriendlyByteBuf.readVarInt();
        this.friendlyByteBuf = new FriendlyByteBuf(newFriendlyByteBuf.readBytes(newFriendlyByteBuf.readableBytes()));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(id);
        buffer.writeBytes(friendlyByteBuf.copy());
    }

    @Override
    public PacketType<? extends net.minecraft.network.protocol.Packet<ServerGamePacketListenerImpl>> type() {
        return TYPE;
    }

    @Override
    public void handle(ServerGamePacketListenerImpl handler) {
        handler.server.execute(() -> {
            var event = new C2SPacketEvent(this);
            NeoForge.EVENT_BUS.post(event);

            var packetType = NetworkSystem.<org.academy.api.common.network.packet.PacketType
                    <ServerGamePacketListenerImpl, ?>>getPacketTypeById(id);
            var packetClass = packetType.packetClass();

            if (NetworkSystem.debugInfo) {
                var packetSize = friendlyByteBuf.readableBytes();
                AcademyCraft.LOGGER.info(
                        "[AC-NET-DEBUG][RECEIVE][C2S] Packet: {}(ID: {}), Size: {} bytes",
                        packetClass.getSimpleName(),
                        id,
                        packetSize
                );
            }

            if (!NetworkSystem.shouldReceive(packetClass, ThreadType.SERVER)) return;

            try {
                var instance = packetType.codec().decode(friendlyByteBuf);
                instance.setPacketListener(handler);
                AcademyCraftServer.NETWORK_MANAGER.dispatchPacket(instance);
            } catch (Throwable e) {
                AcademyCraft.LOGGER.error(
                        "Exception processing C2S packet. Class: {}, ID: {}. Player: {}. Error: {}",
                        packetClass.getSimpleName(),
                        id,
                        handler.player.getGameProfile().name(),
                        e.getMessage(),
                        e
                );
            }
        });
    }
}