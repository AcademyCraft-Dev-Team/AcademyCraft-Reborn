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

import java.util.function.Function;

public class S2CPacket implements Packet<ClientPacketListener> {
    public final int id;
    public final FriendlyByteBuf friendlyByteBuf;

    @SuppressWarnings("unchecked")
    public <T extends IPacket<ClientPacketListener>> S2CPacket(T packet) {
        Class<T> clazz = (Class<T>) packet.getClass();
        id = AcademyCraftServer.NETWORK_SYSTEM_INSTANCE.getPacketIdByType(clazz);
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(friendlyByteBuf);
    }

    @ApiStatus.Internal
    public S2CPacket(@NotNull FriendlyByteBuf friendlyByteBuf) {
        id = friendlyByteBuf.readVarInt();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buffer) {
        buffer.writeVarInt(id);
        buffer.writeBytes(friendlyByteBuf.copy());
    }

    @Override
    public void handle(@NotNull ClientPacketListener handler) {
        Minecraft.getInstance().execute(() -> {
            S2CPacketEvent event = new S2CPacketEvent(this);
            AcademyCraft.EVENT_BUS.post(event);
            if (event.isCanceled()) return;

            Class<IPacket<ClientGamePacketListener>> packetClass = AcademyCraftClient.NETWORK_SYSTEM_INSTANCE.getClassById(id);
            if (packetClass != null) {
                if (packetClass.isAnnotationPresent(PacketTarget.class)) {
                    ThreadType targetType = packetClass.getAnnotation(PacketTarget.class).value();
                    if (targetType != ThreadType.CLIENT) return;
                }
                try {
                    Function<ClientGamePacketListener, IPacket<ClientGamePacketListener>> factory =
                            AcademyCraftClient.NETWORK_SYSTEM_INSTANCE.getPacketFactory(packetClass);
                    if (factory == null) {
                        AcademyCraft.LOGGER.error("No factory found for S2C packet class: {}", packetClass.getName());
                        return;
                    }
                    IPacket<ClientGamePacketListener> instance = factory.apply(handler);
                    instance.packetListenerSupplier = () -> handler;
                    instance.read(friendlyByteBuf);
                    AcademyCraftClient.NETWORK_SYSTEM_CLIENT_INSTANCE.dispatchClientPacket(instance);
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