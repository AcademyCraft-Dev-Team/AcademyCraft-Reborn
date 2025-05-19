package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.packet.FutureRequestPayload;
import org.jetbrains.annotations.NotNull;

public class C2SGetCurrentNodePacket extends FutureRequestPayload {
    public BlockPos userPos;

    public C2SGetCurrentNodePacket() {}

    public C2SGetCurrentNodePacket(BlockPos userPos) {
        this.userPos = userPos;
    }

    @Override
    public void readPayload(@NotNull FriendlyByteBuf buf) {
        this.userPos = buf.readBlockPos();
    }

    @Override
    public void writePayload(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(this.userPos);
    }
}