package org.academy.api.common.wireless;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.annotation.PacketTarget;
import org.academy.api.common.network.packet.PacketType;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.PacketTypes;

@PacketTarget(ThreadType.SERVER)
public class SetNodeNamePacket extends Packet<ServerGamePacketListenerImpl, SetNodeNamePacket> {
    public static final StreamCodec<ByteBuf, SetNodeNamePacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SetNodeNamePacket::getNodePos,
            ByteBufCodecs.STRING_UTF8,
            SetNodeNamePacket::getNewName,
            SetNodeNamePacket::new
    );

    private final BlockPos nodePos;
    private final String newName;

    public SetNodeNamePacket(BlockPos nodePos, String newName) {
        this.nodePos = nodePos;
        this.newName = newName;
    }

    public BlockPos getNodePos() {
        return nodePos;
    }

    public String getNewName() {
        return newName;
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, SetNodeNamePacket> getPacketType() {
        return PacketTypes.SET_NODE_NAME.get();
    }
}