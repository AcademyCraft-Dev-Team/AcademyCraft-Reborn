package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.network.FriendlyByteBufSerializer;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.academy.api.common.network.NetworkSystem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class S2CPacket implements Packet<ClientGamePacketListener> {
    public int id;
    public final FriendlyByteBuf friendlyByteBuf;

    @ApiStatus.Internal
    public S2CPacket(FriendlyByteBuf friendlyByteBuf) {
        id = friendlyByteBuf.readVarInt();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    public S2CPacket(String packet, FriendlyByteBuf friendlyByteBuf) {
        this.id = NetworkSystem.getPacketId(packet);
        this.friendlyByteBuf = friendlyByteBuf;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public S2CPacket(@NotNull String packet, Object... values) {
        this.id = NetworkSystem.getPacketId(packet);
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
    public void handle(@NotNull ClientGamePacketListener handler) {
        Minecraft.getInstance().execute(() -> NetworkSystemClient.SERVER_TO_CLIENT_PACKET_HANDLER_MAP.get(NetworkSystem.getPacketResourceLocation(id)).handle((ClientPacketListener) handler, this));
    }
}