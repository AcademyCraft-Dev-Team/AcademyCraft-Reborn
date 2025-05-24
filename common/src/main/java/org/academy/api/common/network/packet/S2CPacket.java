package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.AcademyCraft;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.asm.InstanceCreator;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class S2CPacket implements Packet<ClientPacketListener> {
    public final int id;
    public final FriendlyByteBuf friendlyByteBuf;

    @SuppressWarnings("unchecked")
    public <T extends IPacket<ClientPacketListener>> S2CPacket(T packet) {
        Class<T> clazz = (Class<T>) packet.getClass();
        id = NetworkSystem.getPacketIdByType(clazz);
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

    @SuppressWarnings("unchecked")
    @Override
    public void handle(@NotNull ClientPacketListener handler) {
        Minecraft.getInstance().execute(() -> {
            S2CPacketEvent event = new S2CPacketEvent(this);
            AcademyCraft.EVENT_BUS.post(event);
            if (event.isCanceled()) return;

            Class<?> packetClassUntyped = NetworkSystem.getClassById(id);
            if (packetClassUntyped != null) {
                if (packetClassUntyped.isAnnotationPresent(PacketTarget.class)) {
                    ThreadType targetType = packetClassUntyped.getAnnotation(PacketTarget.class).value();
                    if (targetType != ThreadType.CLIENT) return;
                }
                try {
                    Class<IPacket<ClientGamePacketListener>> packetClass =
                            (Class<IPacket<ClientGamePacketListener>>) packetClassUntyped;
                    InstanceCreator<IPacket<ClientGamePacketListener>> creator = NetworkSystem.getPacketCreator(packetClass);

                    if (creator == null) {
                        AcademyCraft.LOGGER.error("No creator instance found for S2C packet class: {}", packetClass.getName());
                        return;
                    }
                    IPacket<ClientGamePacketListener> instance = creator.create();
                    instance.packetListenerSupplier = () -> handler;
                    instance.read(friendlyByteBuf);
                    NetworkSystemClient.dispatchClientPacket(instance);
                } catch (Throwable e) {
                    AcademyCraft.LOGGER.error(
                            "Exception processing S2C packet. Class: {}, ID: {}. Listener: {}. Error: {}",
                            packetClassUntyped.getSimpleName(),
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