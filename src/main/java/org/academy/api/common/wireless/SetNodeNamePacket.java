package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.SERVER)
public class SetNodeNamePacket extends Packet<ServerGamePacketListenerImpl> {
    public BlockPos nodePos;
    public String newName;

    public SetNodeNamePacket(ServerGamePacketListenerImpl listener) {
        super(listener);
    }

    public SetNodeNamePacket(BlockPos newNodePos, String newNewName) {
        super(null);
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

    @Override
    public @NotNull PacketType<ServerGamePacketListenerImpl, ? extends Packet<ServerGamePacketListenerImpl>> getPacketType() {
        return PacketTypes.SET_NODE_NAME.get();
    }
}