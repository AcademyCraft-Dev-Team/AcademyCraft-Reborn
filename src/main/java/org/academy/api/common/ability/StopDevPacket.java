package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.SERVER)
public class StopDevPacket extends Packet<ServerGamePacketListenerImpl, StopDevPacket> {
    public static final StreamCodec<ByteBuf, StopDevPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            StopDevPacket::getUserPos,
            StopDevPacket::new
    );

    private final BlockPos userPos;

    public StopDevPacket(BlockPos userPos) {
        this.userPos = userPos;
    }

    public BlockPos getUserPos() {
        return userPos;
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, StopDevPacket> getPacketType() {
        return PacketTypes.STOP_DEV.get();
    }
}
