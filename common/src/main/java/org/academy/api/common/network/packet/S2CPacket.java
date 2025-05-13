package org.academy.api.common.network.packet;

import io.netty.buffer.ByteBuf;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class S2CPacket implements Packet<ClientGamePacketListener> {
    public int id;
    public final FriendlyByteBuf friendlyByteBuf;

    @ApiStatus.Internal
    public S2CPacket(@NotNull FriendlyByteBuf friendlyByteBuf) {
        id = friendlyByteBuf.readVarInt();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    public S2CPacket(@NotNull String packet, @NotNull FriendlyByteBuf friendlyByteBuf) {
        this.id = NetworkSystem.getPacketId(packet);
        this.friendlyByteBuf = friendlyByteBuf;
    }

    public S2CPacket(@NotNull String packet, @NotNull ByteBuf byteBuf) {
        this.id = NetworkSystem.getPacketId(packet);
        if (byteBuf instanceof FriendlyByteBuf buf) {
            this.friendlyByteBuf = buf;
        } else {
            this.friendlyByteBuf = new FriendlyByteBuf(byteBuf);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public S2CPacket(@NotNull String packet, @NotNull Object... values) {
        this.id = NetworkSystem.getPacketId(packet);
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        for (Object value : values) {
            FriendlyByteBufSerializer friendlyByteBufSerializer = FriendlyByteBufSerializers.getRequiredSerializer(value.getClass());
            friendlyByteBufSerializer.serialize(friendlyByteBuf, value);
        }
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buffer) {
        buffer.writeVarInt(id);
        buffer.writeBytes(friendlyByteBuf.copy());
    }

    @Override
    public void handle(@NotNull ClientGamePacketListener handler) {
        S2CPacketEvent event = new S2CPacketEvent(this);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        if (handler instanceof ClientPacketListener listener) {
            String packet = NetworkSystem.getPacketResourceLocation(id);
            if (packet != null) {
                S2CPacketHandler packetHandler = NetworkSystemClient.S2C_PACKET_HANDLER_MAP.get(packet);
                if (packetHandler != null) {
                    Minecraft.getInstance().execute(() -> packetHandler.handle(listener, this));
                } else {
                    AcademyCraft.LOGGER.warn("PacketHandler " + packet + " not found");
                }
            } else {
                AcademyCraft.LOGGER.info("Unknown packetID " + id);
            }
        }
    }
}