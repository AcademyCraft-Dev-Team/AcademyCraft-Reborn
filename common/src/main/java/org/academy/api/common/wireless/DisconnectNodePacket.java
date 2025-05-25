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

    public DisconnectNodePacket() {
    }

    public DisconnectNodePacket(BlockPos userPos) {
        this.userPos = userPos;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.userPos = buf.readBlockPos();
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(this.userPos);
    }
}