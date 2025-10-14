package org.academy.api.common.wireless;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;
import org.misaka.api.common.network.packet.PacketType;
import org.academy.internal.common.network.PacketTypes;

public class GetCurrentNodePacket extends RequestPacket<ServerGamePacketListenerImpl, GetCurrentNodePacket, ClientPacketListener, GetCurrentNodePacket.Response> {
    public static final StreamCodec<ByteBuf, GetCurrentNodePacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            GetCurrentNodePacket::getUserPos,
            GetCurrentNodePacket::new
    );

    private final BlockPos userPos;

    public GetCurrentNodePacket(BlockPos userPos) {
        this.userPos = userPos;
    }

    public BlockPos getUserPos() {
        return userPos;
    }

    @Override
    public PacketType<ClientPacketListener, Response> getResponsePacketType() {
        return PacketTypes.GET_CURRENT_NODE_RESPONSE.get();
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, GetCurrentNodePacket> getPacketType() {
        return PacketTypes.GET_CURRENT_NODE.get();
    }

    public static class Response extends ResponsePacket<ClientPacketListener, Response> {
        public static final StreamCodec<ByteBuf, Response> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                Response::isNull,
                ByteBufCodecs.STRING_UTF8,
                Response::getNodeName,
                Response::new
        );

        private final boolean isNull;
        private final String nodeName;

        public Response(boolean isNull, String nodeName) {
            this.isNull = isNull;
            this.nodeName = nodeName;
        }

        public boolean isNull() {
            return isNull;
        }

        public String getNodeName() {
            return nodeName;
        }

        @Override
        public PacketType<ClientPacketListener, Response> getPacketType() {
            return PacketTypes.GET_CURRENT_NODE_RESPONSE.get();
        }
    }
}