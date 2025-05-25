package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.SERVER)
public class SetNodeNamePacket extends IPacket<ServerGamePacketListenerImpl> {
    public BlockPos nodePos;
    public String newName;

    public SetNodeNamePacket() {
    }

    public SetNodeNamePacket(BlockPos nodePos, String newName) {
        this.nodePos = nodePos;
        this.newName = newName;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.nodePos = buf.readBlockPos();
        this.newName = buf.readUtf();
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(this.nodePos);
        buf.writeUtf(this.newName);
    }
}