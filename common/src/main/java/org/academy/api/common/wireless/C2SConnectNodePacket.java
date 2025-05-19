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
public class C2SConnectNodePacket extends IPacket<ServerGamePacketListenerImpl> {
    public BlockPos userPos;
    public String targetNodeName;
    public String passwordAttempt;

    @ReceiverConstructor
    public C2SConnectNodePacket() {
    }

    @SenderConstructor
    public C2SConnectNodePacket(BlockPos userPos, String targetNodeName, String passwordAttempt) {
        this.userPos = userPos;
        this.targetNodeName = targetNodeName;
        this.passwordAttempt = passwordAttempt;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.userPos = buf.readBlockPos();
        this.targetNodeName = buf.readUtf();
        this.passwordAttempt = buf.readUtf();
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(this.userPos);
        buf.writeUtf(this.targetNodeName);
        buf.writeUtf(this.passwordAttempt);
    }
}