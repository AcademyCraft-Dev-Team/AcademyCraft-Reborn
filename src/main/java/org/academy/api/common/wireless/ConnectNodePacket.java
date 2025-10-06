package org.academy.api.common.wireless;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.annotation.PacketTarget;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.network.packet.PacketType;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.PacketTypes;

@PacketTarget(ThreadType.SERVER)
public class ConnectNodePacket extends Packet<ServerGamePacketListenerImpl, ConnectNodePacket> {
    public static final StreamCodec<ByteBuf, ConnectNodePacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ConnectNodePacket::getUserPos,
            ByteBufCodecs.STRING_UTF8,
            ConnectNodePacket::getTargetNodeName,
            ByteBufCodecs.STRING_UTF8,
            ConnectNodePacket::getPasswordAttempt,
            ConnectNodePacket::new
    );

    private final BlockPos userPos;
    private final String targetNodeName;
    private final String passwordAttempt;

    public ConnectNodePacket(BlockPos userPos, String targetNodeName, String passwordAttempt) {
        this.userPos = userPos;
        this.targetNodeName = targetNodeName;
        this.passwordAttempt = passwordAttempt;
    }

    public BlockPos getUserPos() {
        return userPos;
    }

    public String getTargetNodeName() {
        return targetNodeName;
    }

    public String getPasswordAttempt() {
        return passwordAttempt;
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, ConnectNodePacket> getPacketType() {
        return PacketTypes.CONNECT_NODE.get();
    }
}