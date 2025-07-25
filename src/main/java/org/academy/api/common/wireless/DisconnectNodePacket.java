package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.SERVER)
public class DisconnectNodePacket extends IPacket<ServerGamePacketListenerImpl> {
    public BlockPos userPos;

    public DisconnectNodePacket(ServerGamePacketListenerImpl listener) {
        super(listener);
    }

    public DisconnectNodePacket(BlockPos newUserPos) {
        super(null);
        userPos = newUserPos;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        userPos = buf.readBlockPos();
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(userPos);
    }
}