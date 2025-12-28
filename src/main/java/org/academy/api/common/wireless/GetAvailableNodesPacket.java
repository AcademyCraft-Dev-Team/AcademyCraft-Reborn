package org.academy.api.common.wireless;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public class GetAvailableNodesPacket extends RequestPacket<ServerGamePacketListenerImpl, GetAvailableNodesPacket, ClientPacketListener, GetAvailableNodesPacket.Response> {
    public static final StreamCodec<ByteBuf, GetAvailableNodesPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            GetAvailableNodesPacket::getRequesterPos,
            GetAvailableNodesPacket::new
    );

    private final BlockPos requesterPos;

    public GetAvailableNodesPacket(BlockPos requesterPos) {
        this.requesterPos = requesterPos;
    }

    public BlockPos getRequesterPos() {
        return requesterPos;
    }

    @Override
    public PacketType<ClientPacketListener, Response> getResponsePacketType() {
        return PacketTypes.GET_AVAILABLE_NODES_RESPONSE.get();
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, GetAvailableNodesPacket> getPacketType() {
        return PacketTypes.GET_AVAILABLE_NODES.get();
    }

    public static class Response extends ResponsePacket<ClientPacketListener, Response> {
        public static final StreamCodec<ByteBuf, Response> CODEC = ByteBufCodecs.STRING_UTF8
                .apply(ByteBufCodecs.list())
                .map(Response::new, Response::getAvailableNodeNames);

        private final List<String> availableNodeNames;

        public Response(List<String> availableNodeNames) {
            this.availableNodeNames = availableNodeNames;
        }

        public List<String> getAvailableNodeNames() {
            return availableNodeNames;
        }

        @Override
        public PacketType<ClientPacketListener, Response> getPacketType() {
            return PacketTypes.GET_AVAILABLE_NODES_RESPONSE.get();
        }
    }
}