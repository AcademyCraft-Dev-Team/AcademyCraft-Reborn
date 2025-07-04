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

    public SetNodeNamePacket(BlockPos newNodePos, String newNewName) {
        nodePos = newNodePos;
        newName = newNewName;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        nodePos = buf.readBlockPos();
        newName = buf.readUtf();
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(nodePos);
        buf.writeUtf(newName);
    }
}