package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.network.FriendlyByteBufSerializer;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.jetbrains.annotations.NotNull;

public class S2CPacket implements Packet<ClientGamePacketListener> {
    public final ResourceLocation resourceLocation;
    public final FriendlyByteBuf friendlyByteBuf;

    public S2CPacket(FriendlyByteBuf friendlyByteBuf) {
        resourceLocation = friendlyByteBuf.readResourceLocation();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    public S2CPacket(ResourceLocation resourceLocation, FriendlyByteBuf friendlyByteBuf) {
        this.resourceLocation = resourceLocation;
        this.friendlyByteBuf = friendlyByteBuf;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public S2CPacket(@NotNull ResourceLocation resourceLocation, Object... values) {
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
    public void handle(@NotNull ClientGamePacketListener handler) {
        Minecraft.getInstance().execute(() -> {
            NetworkSystemClient.SERVER_TO_CLIENT_PACKET_HANDLER_MAP.get(resourceLocation).handle((ClientPacketListener) handler, this);
            friendlyByteBuf.release();
        });
    }
}