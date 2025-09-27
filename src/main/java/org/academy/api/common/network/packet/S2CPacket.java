package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.event.S2CPacketEvent;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.ApiStatus;

public final class S2CPacket implements net.minecraft.network.protocol.Packet<ClientGamePacketListener> {
    public static final PacketType<S2CPacket> TYPE = new PacketType<>(PacketFlow.CLIENTBOUND, AcademyCraft.academy("s2c_packet"));
    public static final StreamCodec<FriendlyByteBuf, S2CPacket> STREAM_CODEC = net.minecraft.network.protocol.Packet.codec(
            S2CPacket::write, S2CPacket::new
    );

    private final int id;
    private final FriendlyByteBuf friendlyByteBuf;

    public <T extends Packet<ClientGamePacketListener, T>> S2CPacket(T packet) {
        id = packet.getPacketType().getPacketId();
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());

        var startTime = NetworkSystem.debugInfo ? System.nanoTime() : 0;
        packet.getPacketType().codec().encode(friendlyByteBuf, packet);

        if (NetworkSystem.debugInfo) {
            var endTime = System.nanoTime();
            var packetSize = friendlyByteBuf.readableBytes();
            AcademyCraft.LOGGER.info(
                    "[AC-NET-DEBUG][SEND][S2C] Packet: {}(ID: {}), Size: {} bytes, Encode Time: {} ns",
                    packet.getClass().getSimpleName(),
                    id,
                    packetSize,
                    endTime - startTime
            );
        }
    }

    @ApiStatus.Internal
    public S2CPacket(FriendlyByteBuf friendlyByteBuf) {
        id = friendlyByteBuf.readVarInt();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(id);
        buffer.writeBytes(friendlyByteBuf.copy());
    }

    @Override
    public PacketType<? extends net.minecraft.network.protocol.Packet<ClientGamePacketListener>> type() {
        return TYPE;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        Minecraft.getInstance().execute(() -> {
            var event = new S2CPacketEvent(this);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) return;

            var packetType = NetworkSystem.<org.academy.api.common.network.packet.PacketType
                    <ClientGamePacketListener, ?>>getPacketTypeById(id);
            var packetClass = packetType.packetClass();

            if (NetworkSystem.debugInfo) {
                var packetSize = friendlyByteBuf.readableBytes();
                AcademyCraft.LOGGER.info(
                        "[AC-NET-DEBUG][RECEIVE][S2C] Packet: {}(ID: {}), Size: {} bytes",
                        packetClass.getSimpleName(),
                        id,
                        packetSize
                );
            }

            if (!NetworkSystem.shouldReceive(packetClass, ThreadType.CLIENT)) return;

            try {
                var codec = packetType.codec();
                var instance = codec.decode(friendlyByteBuf);
                instance.setPacketListener(handler);
                AcademyCraftClient.CLIENT_NETWORK_MANAGER.dispatchPacket(instance);
            } catch (Throwable e) {
                AcademyCraft.LOGGER.error(
                        "Exception processing S2C packet. Class: {}, ID: {}. Listener: {}. Error: {}",
                        packetClass.getSimpleName(),
                        id,
                        handler.getClass().getSimpleName(),
                        e.getMessage(),
                        e
                );
            }
        });
    }
}