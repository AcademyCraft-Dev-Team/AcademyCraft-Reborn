package org.academy.api.common.wireless;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.PacketTypes;

@PacketTarget(ThreadType.SERVER)
public class SetNodePassPacket extends Packet<ServerGamePacketListenerImpl, SetNodePassPacket> {
    public static final StreamCodec<ByteBuf, SetNodePassPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SetNodePassPacket::getNodePos,
            ByteBufCodecs.STRING_UTF8,
            SetNodePassPacket::getNewPass,
            SetNodePassPacket::new
    );

    private final BlockPos nodePos;
    private final String newPass;

    public SetNodePassPacket(BlockPos nodePos, String newPass) {
        this.nodePos = nodePos;
        this.newPass = newPass;
    }

    public BlockPos getNodePos() {
        return nodePos;
    }

    public String getNewPass() {
        return newPass;
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, SetNodePassPacket> getPacketType() {
        return PacketTypes.SET_NODE_PASS.get();
    }
}