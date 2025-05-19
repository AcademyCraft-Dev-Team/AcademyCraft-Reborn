package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.ReceiverConstructor;
import org.academy.api.common.network.SenderConstructor;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.SERVER)
public class C2SSetNodePassPacket extends IPacket<ServerGamePacketListenerImpl> {
    public BlockPos nodePos;
    public String newPass;

    @ReceiverConstructor
    public C2SSetNodePassPacket() {
    }

    @SenderConstructor
    public C2SSetNodePassPacket(BlockPos nodePos, String newPass) {
        this.nodePos = nodePos;
        this.newPass = newPass;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.nodePos = buf.readBlockPos();
        this.newPass = buf.readUtf();
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(this.nodePos);
        buf.writeUtf(this.newPass);
    }
}