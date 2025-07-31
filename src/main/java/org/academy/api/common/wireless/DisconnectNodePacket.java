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

    @Override
    public @NotNull PacketType<ServerGamePacketListenerImpl, ? extends IPacket<ServerGamePacketListenerImpl>> getPacketType() {
        return PacketTypes.DISCONNECT_NODE.get();
    }
}