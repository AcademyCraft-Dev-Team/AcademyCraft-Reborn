package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.FriendlyByteBufSerializer;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.academy.api.server.network.AcademyCraftNetworkSystemServer;
import org.jetbrains.annotations.NotNull;

public class ClientToServerPacket implements Packet<ServerGamePacketListener> {
    public ResourceLocation resourceLocation;
    public FriendlyByteBuf friendlyByteBuf;

    public ClientToServerPacket(@NotNull FriendlyByteBuf friendlyByteBuf) {
        resourceLocation = friendlyByteBuf.readResourceLocation();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    public ClientToServerPacket(@NotNull ResourceLocation resourceLocation, @NotNull FriendlyByteBuf friendlyByteBuf) {
        this.resourceLocation = resourceLocation;
        this.friendlyByteBuf = friendlyByteBuf;
    }

    @SuppressWarnings("unchecked")
    public ClientToServerPacket(@NotNull ResourceLocation resourceLocation, Object... values) {
        this.resourceLocation = resourceLocation;
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        for (Object value : values) {
            final FriendlyByteBufSerializer<Object> friendlyByteBufSerializer = (FriendlyByteBufSerializer<Object>) FriendlyByteBufSerializers.getRequiredSerializer(value.getClass());
            friendlyByteBufSerializer.serialize(friendlyByteBuf, value);
        }
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buffer) {
        friendlyByteBuf.writeResourceLocation(resourceLocation);
        friendlyByteBuf.writeBytes(friendlyByteBuf.copy());
    }

    @Override
    public void handle(@NotNull ServerGamePacketListener handler) {
        final ServerGamePacketListenerImpl serverPacketListener = (ServerGamePacketListenerImpl) handler;
        serverPacketListener.player.server.execute(() -> {
            AcademyCraftNetworkSystemServer.CLIENT_TO_SERVER_PACKET_HANDLER_MAP.get(resourceLocation).handle(serverPacketListener, this);
            friendlyByteBuf.release();
        });
    }
}