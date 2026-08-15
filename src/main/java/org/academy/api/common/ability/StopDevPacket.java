package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.SERVER)
public class StopDevPacket extends Packet<ServerGamePacketListenerImpl, StopDevPacket> {
    public static final StreamCodec<ByteBuf, StopDevPacket> CODEC = StreamCodec.composite(
            DevelopmentSource.CODEC,
            StopDevPacket::getSource,
            StopDevPacket::new
    );

    private final DevelopmentSource source;

    public StopDevPacket(BlockPos userPos) {
        this(DevelopmentSource.block(userPos));
    }

    public StopDevPacket(InteractionHand hand) {
        this(DevelopmentSource.tablet(hand));
    }

    public StopDevPacket(DevelopmentSource source) {
        this.source = source;
    }

    public BlockPos getUserPos() {
        return source.blockPos();
    }

    public DevelopmentSource getSource() {
        return source;
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, StopDevPacket> getPacketType() {
        return PacketTypes.STOP_DEV.get();
    }
}
