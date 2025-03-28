package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.FriendlyByteBufSerializer;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.academy.api.server.network.NetworkSystemServer;
import org.jetbrains.annotations.NotNull;

public class C2SPacket implements Packet<ServerGamePacketListener> {
    public ResourceLocation resourceLocation;
    public FriendlyByteBuf friendlyByteBuf;

    public C2SPacket(@NotNull FriendlyByteBuf friendlyByteBuf) {
        resourceLocation = friendlyByteBuf.readResourceLocation();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    public C2SPacket(@NotNull ResourceLocation resourceLocation, @NotNull FriendlyByteBuf friendlyByteBuf) {
        this.resourceLocation = resourceLocation;
        this.friendlyByteBuf = friendlyByteBuf;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public C2SPacket(@NotNull ResourceLocation resourceLocation, Object... values) {
        this.resourceLocation = resourceLocation;
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        for (Object value : values) {
            FriendlyByteBufSerializer friendlyByteBufSerializer = FriendlyByteBufSerializers.getRequiredSerializer(value.getClass());
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
            NetworkSystemServer.C2S_PACKET_HANDLER_MAP.get(resourceLocation).handle(serverPacketListener, this);
            friendlyByteBuf.release();
        });
    }
}