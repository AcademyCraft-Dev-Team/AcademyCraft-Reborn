package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.SERVER)
public class SetNodePassPacket extends IPacket<ServerGamePacketListenerImpl> {
    public BlockPos nodePos;
    public String newPass;

    public SetNodePassPacket() {
    }

    public SetNodePassPacket(BlockPos newNodePos, String newNewPass) {
        nodePos = newNodePos;
        newPass = newNewPass;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        nodePos = buf.readBlockPos();
        newPass = buf.readUtf();
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(nodePos);
        buf.writeUtf(newPass);
    }
}