package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.packet.FutureRequestPayload;
import org.jetbrains.annotations.NotNull;

public class C2SGetAvailableNodesPacket extends FutureRequestPayload {
    public BlockPos requesterPos;

    public C2SGetAvailableNodesPacket() {
    }

    public C2SGetAvailableNodesPacket(BlockPos requesterPos) {
        this.requesterPos = requesterPos;
    }

    @Override
    public void readPayload(@NotNull FriendlyByteBuf buf) {
        this.requesterPos = buf.readBlockPos();
    }

    @Override
    public void writePayload(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(this.requesterPos);
    }
}