package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.SERVER)
public class SetNodePassPacket extends IPacket<ServerGamePacketListenerImpl> {
    public BlockPos nodePos;
    public String newPass;

    public SetNodePassPacket(ServerGamePacketListenerImpl listener) {
        super(listener);
    }

    public SetNodePassPacket(BlockPos newNodePos, String newNewPass) {
        super(null);
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

    @Override
    public @NotNull PacketType<ServerGamePacketListenerImpl, ? extends IPacket<ServerGamePacketListenerImpl>> getPacketType() {
        return PacketTypes.SET_NODE_PASS.get();
    }
}