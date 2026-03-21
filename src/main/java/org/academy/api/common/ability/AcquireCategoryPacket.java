package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;

public class AcquireCategoryPacket extends RequestPacket<ServerGamePacketListenerImpl, AcquireCategoryPacket, ClientPacketListener, AcquireCategoryPacket.Response> {
    public static final StreamCodec<ByteBuf, AcquireCategoryPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG,
            AcquireCategoryPacket::getUserPos,
            AcquireCategoryPacket::new
    );

    private final long userPos;

    public AcquireCategoryPacket(long userPos) {
        this.userPos = userPos;
    }

    public long getUserPos() {
        return userPos;
    }

    @Override
    public PacketType<ClientPacketListener, Response> getResponsePacketType() {
        return PacketTypes.ACQUIRE_CATEGORY_RESPONSE.get();
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, AcquireCategoryPacket> getPacketType() {
        return PacketTypes.ACQUIRE_CATEGORY.get();
    }

    public static class Response extends ResponsePacket<ClientPacketListener, Response> {
        public static final StreamCodec<ByteBuf, Response> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                Response::getMessages,
                Response::new
        );
        private final List<String> messages;

        public Response(List<String> messages) {
            this.messages = new ArrayList<>(messages);
        }

        public List<String> getMessages() {
            return messages;
        }

        @Override
        public PacketType<ClientPacketListener, Response> getPacketType() {
            return PacketTypes.ACQUIRE_CATEGORY_RESPONSE.get();
        }
    }
}