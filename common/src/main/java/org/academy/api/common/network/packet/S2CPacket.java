package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.AcademyCraft;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.network.S2CPacketHandler;
import org.academy.api.common.network.FriendlyByteBufSerializer;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class S2CPacket implements Packet<ClientGamePacketListener> {
    public boolean clazz;
    public final int id;
    public final FriendlyByteBuf friendlyByteBuf;

    @SuppressWarnings("unchecked")
    public <T extends IPacket<ClientPacketListener>> S2CPacket(T packet) {
        Class<T> clazz = (Class<T>) packet.getClass();
        this.clazz = true;
        id = NetworkSystem.getPacketIdByType(clazz);
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(friendlyByteBuf);
    }

    @ApiStatus.Internal
    public S2CPacket(@NotNull FriendlyByteBuf friendlyByteBuf) {
        clazz = friendlyByteBuf.readBoolean();
        id = friendlyByteBuf.readVarInt();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    public S2CPacket(@NotNull String packet, @NotNull FriendlyByteBuf friendlyByteBuf) {
        this.id = NetworkSystem.getPacketIdByName(packet);
        this.friendlyByteBuf = friendlyByteBuf;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public S2CPacket(@NotNull String packet, @NotNull Object... values) {
        this.id = NetworkSystem.getPacketIdByName(packet);
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        for (Object value : values) {
            FriendlyByteBufSerializer friendlyByteBufSerializer = FriendlyByteBufSerializers.getRequiredSerializer(value.getClass());
            friendlyByteBufSerializer.serialize(friendlyByteBuf, value);
        }
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buffer) {
        buffer.writeBoolean(clazz);
        buffer.writeVarInt(id);
        buffer.writeBytes(friendlyByteBuf.copy());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(@NotNull ClientGamePacketListener handler) {
        S2CPacketEvent event = new S2CPacketEvent(this);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        if (handler instanceof ClientPacketListener listener) {
            Minecraft.getInstance().execute(() -> {
                if (clazz) {
                    Class<?> packetClass = NetworkSystem.getClassById(id);
                    if (packetClass != null) {
                        if (packetClass.isAnnotationPresent(PacketTarget.class)) {
                            ThreadType[] threadTypes = packetClass.getAnnotation(PacketTarget.class).value();
                            boolean hasEnvironment = false;
                            for (ThreadType threadType : threadTypes) {
                                hasEnvironment = hasEnvironment || threadType == ThreadType.CLIENT;
                            }
                            if (!hasEnvironment) return;
                        }
                        try {
                            IPacket<ClientGamePacketListener> instance = (IPacket<ClientGamePacketListener>) packetClass.getDeclaredConstructor().newInstance();
                            instance.packetListenerSupplier = () -> handler;
                            instance.read(friendlyByteBuf);
                            NetworkSystem.dispatchPacket(instance);
                        } catch (Throwable ignored) {
                        }
                    } else {
                        AcademyCraft.LOGGER.error("Unknown packetID {}", id);
                    }
                } else {
                    String packet = NetworkSystem.getNameById(id);
                    if (packet != null) {
                        S2CPacketHandler packetHandler = NetworkSystemClient.getS2CPacketHandler(packet);
                        if (packetHandler != null) {
                            packetHandler.handle(listener, this);
                        } else {
                            AcademyCraft.LOGGER.error("PacketHandler {} not found", packet);
                        }
                    } else {
                        AcademyCraft.LOGGER.error("Unknown stringPacketID {}", id);
                    }
                }
            });
        }
    }
}