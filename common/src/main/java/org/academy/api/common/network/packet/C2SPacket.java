package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.FriendlyByteBufSerializer;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.server.network.NetworkSystemServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class C2SPacket implements Packet<ServerGamePacketListener> {
    public int id;
    public FriendlyByteBuf friendlyByteBuf;

    @ApiStatus.Internal
    public C2SPacket(@NotNull FriendlyByteBuf friendlyByteBuf) {
        id = friendlyByteBuf.readVarInt();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    public C2SPacket(@NotNull ResourceLocation resourceLocation, @NotNull FriendlyByteBuf friendlyByteBuf) {
        this.id = NetworkSystem.getPacketId(resourceLocation);
        this.friendlyByteBuf = friendlyByteBuf;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public C2SPacket(@NotNull ResourceLocation resourceLocation, Object... values) {
        this.id = NetworkSystem.getPacketId(resourceLocation);
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        for (Object value : values) {
            FriendlyByteBufSerializer friendlyByteBufSerializer = FriendlyByteBufSerializers.getRequiredSerializer(value.getClass());
            friendlyByteBufSerializer.serialize(friendlyByteBuf, value);
        }
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buffer) {
        friendlyByteBuf.writeVarInt(id);
        friendlyByteBuf.writeBytes(friendlyByteBuf.copy());
    }

    @Override
    public void handle(@NotNull ServerGamePacketListener handler) {
        final ServerGamePacketListenerImpl serverPacketListener = (ServerGamePacketListenerImpl) handler;
        serverPacketListener.player.server.execute(() -> {
            NetworkSystemServer.C2S_PACKET_HANDLER_MAP.get(NetworkSystem.getPacketResourceLocation(id)).handle(serverPacketListener, this);
            friendlyByteBuf.release();
        });
    }
}