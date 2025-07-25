package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftServer;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class C2SPacket implements Packet<ServerGamePacketListenerImpl> {
    public static final PacketType<C2SPacket> TYPE = new PacketType<>(PacketFlow.SERVERBOUND, AcademyCraft.getResourceLocation("s2c_packet"));
    public static final StreamCodec<FriendlyByteBuf, C2SPacket> STREAM_CODEC = Packet.codec(
            C2SPacket::write, C2SPacket::new
    );
    public int id;
    public FriendlyByteBuf friendlyByteBuf;

    @SuppressWarnings("unchecked")
    public <T extends IPacket<ServerGamePacketListenerImpl>> C2SPacket(T packet) {
        var clazz = (Class<T>) packet.getClass();
        id = AcademyCraftClient.NETWORK_SYSTEM.getPacketIdByType(clazz);
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(friendlyByteBuf);
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
    public @NotNull PacketType<? extends Packet<ServerGamePacketListenerImpl>> type() {
        return TYPE;
    }

    @Override
    public void handle(@NotNull ServerGamePacketListenerImpl handler) {
        handler.getPlayer().server.execute(() -> {
            var event = new C2SPacketEvent(this);
            NeoForge.EVENT_BUS.post(event);

            var packetClass = AcademyCraftServer.NETWORK_SYSTEM.<IPacket<ServerGamePacketListenerImpl>>getClassById(id);
            if (packetClass != null) {
                if (packetClass.isAnnotationPresent(PacketTarget.class)) {
                    var targetType = packetClass.getAnnotation(PacketTarget.class).value();
                    if (targetType != ThreadType.SERVER) return;
                }
                try {
                    var factory =
                            AcademyCraftServer.NETWORK_SYSTEM.<IPacket<ServerGamePacketListenerImpl>, ServerGamePacketListenerImpl>getPacketFactory(packetClass);
                    if (factory == null) {
                        AcademyCraft.LOGGER.error("No factory found for C2S packet class: {}", packetClass.getName());
                        return;
                    }
                    var instance = factory.apply(handler);
                    instance.read(friendlyByteBuf);
                    AcademyCraftServer.SERVER_NETWORK_MANAGER.dispatchPacket(instance);
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