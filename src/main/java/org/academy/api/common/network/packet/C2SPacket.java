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
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class C2SPacket implements net.minecraft.network.protocol.Packet<ServerGamePacketListenerImpl> {
    public static final PacketType<C2SPacket> TYPE = new PacketType<>(PacketFlow.SERVERBOUND, AcademyCraft.academy("c2s_packet"));
    public static final StreamCodec<FriendlyByteBuf, C2SPacket> STREAM_CODEC = net.minecraft.network.protocol.Packet.codec(
            C2SPacket::write, C2SPacket::new
    );
    public int id;
    public FriendlyByteBuf friendlyByteBuf;

    public <L extends ServerGamePacketListenerImpl, T extends Packet<L, T>> C2SPacket(T packet) {
        id = packet.getPacketType().getPacketId();
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        packet.getPacketType().getCodec().encode(friendlyByteBuf, packet);
    }

    @ApiStatus.Internal
    private C2SPacket(@NotNull FriendlyByteBuf newFriendlyByteBuf) {
        id = newFriendlyByteBuf.readVarInt();
        friendlyByteBuf = new FriendlyByteBuf(newFriendlyByteBuf.readBytes(newFriendlyByteBuf.readableBytes()));
    }

    public void write(@NotNull FriendlyByteBuf buffer) {
        buffer.writeVarInt(id);
        buffer.writeBytes(friendlyByteBuf.copy());
    }

    @Override
    public @NotNull PacketType<? extends net.minecraft.network.protocol.Packet<ServerGamePacketListenerImpl>> type() {
        return TYPE;
    }

    @Override
    public void handle(@NotNull ServerGamePacketListenerImpl handler) {
        handler.server.execute(() -> {
            var event = new C2SPacketEvent(this);
            NeoForge.EVENT_BUS.post(event);

            var packetType = NetworkSystem.<org.academy.api.common.network.PacketType
                    <ServerGamePacketListenerImpl, ?>>getPacketTypeById(id);
            var packetClass = packetType.getPacketClass();
            if (packetClass != null) {
                if (packetClass.isAnnotationPresent(PacketTarget.class)) {
                    var targetType = packetClass.getAnnotation(PacketTarget.class).value();
                    if (targetType != ThreadType.SERVER) return;
                }
                try {
                    var instance = packetType.getCodec().decode(friendlyByteBuf);
                    instance.setPacketListener(handler);
                    AcademyCraftServer.NETWORK_MANAGER.dispatchPacket(instance);
                } catch (Throwable e) {
                    AcademyCraft.LOGGER.error(
                            "Exception processing C2S packet. Class: {}, ID: {}. Player: {}. Error: {}",
                            packetClass.getSimpleName(),
                            id,
                            handler.player.getGameProfile().getName(),
                            e.getMessage(),
                            e
                    );
                }
            } else {
                AcademyCraft.LOGGER.error("Unknown C2S class packetID {}", id);
            }
        });
    }
}