package org.academy.api.common.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.jetbrains.annotations.NotNull;

public class ServerToClientPacket implements Packet<ClientGamePacketListener> {
    public final ResourceLocation resourceLocation;
    public final FriendlyByteBuf friendlyByteBuf;

    public ServerToClientPacket(FriendlyByteBuf friendlyByteBuf) {
        resourceLocation = friendlyByteBuf.readResourceLocation();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    public ServerToClientPacket(ResourceLocation resourceLocation, FriendlyByteBuf friendlyByteBuf) {
        this.resourceLocation = resourceLocation;
        this.friendlyByteBuf = friendlyByteBuf;
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buffer) {
        friendlyByteBuf.writeResourceLocation(resourceLocation);
        friendlyByteBuf.writeBytes(friendlyByteBuf.copy());
    }

    @Override
    public void handle(@NotNull ClientGamePacketListener handler) {
        Minecraft.getInstance().execute(() -> {
            AcademyCraftNetworkSystemClient.SERVER_TO_CLIENT_PACKET_HANDLER_MAP.get(resourceLocation).handle(handler, this);
            friendlyByteBuf.release();
        });
    }
}