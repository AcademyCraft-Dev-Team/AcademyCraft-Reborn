package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.SERVER)
public class ConnectNodePacket extends IPacket<ServerGamePacketListenerImpl> {
    public BlockPos userPos;
    public String targetNodeName;
    public String passwordAttempt;

    public ConnectNodePacket(ServerGamePacketListenerImpl listener) {
        super(listener);
    }

    public ConnectNodePacket(BlockPos newUserPos, String newTargetNodeName, String newPasswordAttempt) {
        super(null);
        userPos = newUserPos;
        targetNodeName = newTargetNodeName;
        passwordAttempt = newPasswordAttempt;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        userPos = buf.readBlockPos();
        targetNodeName = buf.readUtf();
        passwordAttempt = buf.readUtf();
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(userPos);
        buf.writeUtf(targetNodeName);
        buf.writeUtf(passwordAttempt);
    }
}