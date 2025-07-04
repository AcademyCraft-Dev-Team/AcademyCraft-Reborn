package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftServer;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class S2CPacket implements Packet<ClientPacketListener> {
    public final int id;
    public final FriendlyByteBuf friendlyByteBuf;

    @SuppressWarnings("unchecked")
    public <T extends IPacket<ClientPacketListener>> S2CPacket(T packet) {
        var clazz = (Class<T>) packet.getClass();
        id = AcademyCraftServer.NETWORK_SYSTEM.getPacketIdByType(clazz);
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(friendlyByteBuf);
    }

    @ApiStatus.Internal
    public S2CPacket(@NotNull FriendlyByteBuf newFriendlyByteBuf) {
        id = newFriendlyByteBuf.readVarInt();
        friendlyByteBuf = new FriendlyByteBuf(newFriendlyByteBuf.readBytes(newFriendlyByteBuf.readableBytes()));
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buffer) {
        buffer.writeVarInt(id);
        buffer.writeBytes(friendlyByteBuf.copy());
    }

    @Override
    public void handle(@NotNull ClientPacketListener handler) {
        Minecraft.getInstance().execute(() -> {
            var event = new S2CPacketEvent(this);
            AcademyCraft.EVENT_BUS.post(event);
            if (event.isCanceled()) return;

            var packetClass = AcademyCraftClient.NETWORK_SYSTEM.<IPacket<ClientGamePacketListener>>getClassById(id);
            if (packetClass != null) {
                if (packetClass.isAnnotationPresent(PacketTarget.class)) {
                    var targetType = packetClass.getAnnotation(PacketTarget.class).value();
                    if (targetType != ThreadType.CLIENT) return;
                }
                try {
                    var factory =
                            AcademyCraftClient.NETWORK_SYSTEM.<IPacket<ClientGamePacketListener>, ClientGamePacketListener>getPacketFactory(packetClass);
                    if (factory == null) {
                        AcademyCraft.LOGGER.error("No factory found for S2C packet class: {}", packetClass.getName());
                        return;
                    }
                    var instance = factory.apply(handler);
                    instance.packetListenerSupplier = () -> handler;
                    instance.read(friendlyByteBuf);
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
            } else {
                AcademyCraft.LOGGER.error("Unknown S2C class packetID {}", id);
            }
        });
    }
}