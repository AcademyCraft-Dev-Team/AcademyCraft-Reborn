package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.AcademyCraft;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.asm.InstanceCreator;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class C2SPacket implements Packet<ServerGamePacketListenerImpl> {
    public int id;
    public FriendlyByteBuf friendlyByteBuf;

    @SuppressWarnings("unchecked")
    public <T extends IPacket<ServerGamePacketListenerImpl>> C2SPacket(T packet) {
        Class<T> clazz = (Class<T>) packet.getClass();
        id = NetworkSystem.getPacketIdByType(clazz);
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(friendlyByteBuf);
    }

    @ApiStatus.Internal
    public C2SPacket(@NotNull FriendlyByteBuf friendlyByteBuf) {
        this.id = friendlyByteBuf.readVarInt();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buffer) {
        buffer.writeVarInt(id);
        buffer.writeBytes(friendlyByteBuf.copy());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(@NotNull ServerGamePacketListenerImpl handler) {
        C2SPacketEvent event = new C2SPacketEvent(this);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;

        handler.getPlayer().server.execute(() -> {
            Class<?> packetClassUntyped = NetworkSystem.getClassById(id);
            if (packetClassUntyped != null) {
                if (packetClassUntyped.isAnnotationPresent(PacketTarget.class)) {
                    ThreadType targetType = packetClassUntyped.getAnnotation(PacketTarget.class).value();
                    if (targetType != ThreadType.SERVER) return;
                }
                try {
                    Class<IPacket<ServerGamePacketListenerImpl>> packetClass =
                            (Class<IPacket<ServerGamePacketListenerImpl>>) packetClassUntyped;
                    InstanceCreator<IPacket<ServerGamePacketListenerImpl>> creator = NetworkSystem.getPacketCreator(packetClass);

                    if (creator == null) {
                        AcademyCraft.LOGGER.error("No creator instance found for C2S packet class: {}", packetClass.getName());
                        return;
                    }
                    IPacket<ServerGamePacketListenerImpl> instance = creator.create();
                    instance.packetListenerSupplier = () -> handler;
                    instance.read(friendlyByteBuf);
                    NetworkSystem.dispatchPacket(instance);
                } catch (Throwable e) {
                    AcademyCraft.LOGGER.error(
                            "Exception processing C2S packet. Class: {}, ID: {}. Player: {}. Error: {}",
                            packetClassUntyped.getSimpleName(),
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