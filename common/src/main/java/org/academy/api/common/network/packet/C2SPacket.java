package org.academy.api.common.network.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.AcademyCraft;
import org.academy.api.common.network.FriendlyByteBufSerializer;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.vanilla.EnvType;
import org.academy.api.server.network.C2SPacketHandler;
import org.academy.api.server.network.NetworkSystemServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class C2SPacket implements Packet<ServerGamePacketListener> {
    public boolean clazz;
    public int id;
    public FriendlyByteBuf friendlyByteBuf;

    @SuppressWarnings("unchecked")
    public <T extends IPacket<ServerGamePacketListenerImpl>> C2SPacket(T packet) {
        Class<T> clazz = (Class<T>) packet.getClass();
        id = NetworkSystem.getPacketIdByType(clazz);
        this.clazz = true;
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(friendlyByteBuf);
    }

    @ApiStatus.Internal
    public C2SPacket(@NotNull FriendlyByteBuf friendlyByteBuf) {
        this.clazz = friendlyByteBuf.readBoolean();
        this.id = friendlyByteBuf.readVarInt();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    public C2SPacket(@NotNull String packet, @NotNull FriendlyByteBuf friendlyByteBuf) {
        this.id = NetworkSystem.getPacketIdByName(packet);
        this.friendlyByteBuf = friendlyByteBuf;
        this.clazz = false;
    }

    public C2SPacket(@NotNull String packet, @NotNull ByteBuf byteBuf) {
        this.id = NetworkSystem.getPacketIdByName(packet);
        if (byteBuf instanceof FriendlyByteBuf buf) {
            this.friendlyByteBuf = buf;
        } else {
            this.friendlyByteBuf = new FriendlyByteBuf(byteBuf);
        }
        this.clazz = false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public C2SPacket(@NotNull String packet, @NotNull Object... values) {
        this.id = NetworkSystem.getPacketIdByName(packet);
        this.friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        for (Object value : values) {
            FriendlyByteBufSerializer friendlyByteBufSerializer = FriendlyByteBufSerializers.getRequiredSerializer(value.getClass());
            friendlyByteBufSerializer.serialize(this.friendlyByteBuf, value);
        }
        this.clazz = false;
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buffer) {
        buffer.writeBoolean(clazz);
        buffer.writeVarInt(id);
        buffer.writeBytes(friendlyByteBuf.copy());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(@NotNull ServerGamePacketListener handler) {
        C2SPacketEvent event = new C2SPacketEvent(this);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;

        if (handler instanceof ServerGamePacketListenerImpl listenerImpl) {
            listenerImpl.getPlayer().server.execute(() -> {
                if (clazz) {
                    Class<?> packetClass = NetworkSystem.getClassById(id);
                    if (packetClass != null) {
                        if (packetClass.isAnnotationPresent(PacketTarget.class)) {
                            EnvType[] envTypes = packetClass.getAnnotation(PacketTarget.class).value();
                            boolean hasEnvironment = false;
                            for (EnvType envType : envTypes) {
                                hasEnvironment = hasEnvironment || envType == EnvType.SERVER;
                            }
                            if (!hasEnvironment) return;
                        }
                        try {
                            IPacket<ServerGamePacketListenerImpl> instance = (IPacket<ServerGamePacketListenerImpl>) packetClass.getDeclaredConstructor().newInstance();
                            instance.packetListenerSupplier = () -> listenerImpl;
                            instance.read(friendlyByteBuf);
                            NetworkSystem.dispatchPacket(instance);
                        } catch (Throwable ignored) {
                        }
                    } else {
                        AcademyCraft.LOGGER.error("Unknown packetID {}", id);
                    }
                } else {
                    String packetName = NetworkSystem.getNameById(id);
                    if (packetName != null) {
                        C2SPacketHandler specificHandler = NetworkSystemServer.getC2SPacketHandler(packetName);
                        if (specificHandler != null) {
                            specificHandler.handle(listenerImpl, this);
                        } else {
                            AcademyCraft.LOGGER.warn("PacketHandler {} not found", packetName);
                        }
                    } else {
                        AcademyCraft.LOGGER.error("Unknown stringPacketID {}", id);
                    }
                }
            });
        }
    }
}