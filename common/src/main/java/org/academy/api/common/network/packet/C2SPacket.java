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
import org.academy.api.server.network.C2SPacketHandler;
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

    public C2SPacket(@NotNull String packet, @NotNull FriendlyByteBuf friendlyByteBuf) {
        this.id = NetworkSystem.getPacketId(packet);
        this.friendlyByteBuf = friendlyByteBuf;
    }

    public C2SPacket(@NotNull String packet, @NotNull ByteBuf byteBuf) {
        this.id = NetworkSystem.getPacketId(packet);
        if (byteBuf instanceof FriendlyByteBuf buf) {
            this.friendlyByteBuf = buf;
        } else {
            this.friendlyByteBuf = new FriendlyByteBuf(byteBuf);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public C2SPacket(@NotNull String packet, @NotNull Object... values) {
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
    public void handle(@NotNull ServerGamePacketListener handler) {
        C2SPacketEvent event = new C2SPacketEvent(this);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        if (handler instanceof ServerGamePacketListenerImpl listenerImpl) {
            String packet = NetworkSystem.getPacketResourceLocation(id);
            if (packet != null) {
                C2SPacketHandler packetHandler = NetworkSystemServer.C2S_PACKET_HANDLER_MAP.get(packet);
                if (packetHandler != null) {
                    listenerImpl.player.server.execute(() -> packetHandler.handle(listenerImpl, this));
                } else {
                    AcademyCraft.LOGGER.warn("PacketHandler " + packet + " not found");
                }
            } else {
                AcademyCraft.LOGGER.info("Unknown packetID " + id);
            }
        }
    }
}