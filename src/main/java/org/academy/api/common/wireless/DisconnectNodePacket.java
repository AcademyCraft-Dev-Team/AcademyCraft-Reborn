package org.academy.api.common.wireless;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.PacketTypes;

@PacketTarget(ThreadType.SERVER)
public class DisconnectNodePacket extends Packet<ServerGamePacketListenerImpl, DisconnectNodePacket> {
    public static final StreamCodec<ByteBuf, DisconnectNodePacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            DisconnectNodePacket::getUserPos,
            DisconnectNodePacket::new
    );

    private final BlockPos userPos;

    public DisconnectNodePacket(BlockPos userPos) {
        this.userPos = userPos;
    }

    public BlockPos getUserPos() {
        return userPos;
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, DisconnectNodePacket> getPacketType() {
        return PacketTypes.DISCONNECT_NODE.get();
    }
}