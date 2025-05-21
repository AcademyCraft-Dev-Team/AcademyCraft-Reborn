package org.academy.api.common.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.FutureRequestPayload;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.SERVER)
public class AcquireCategoryPacket extends FutureRequestPayload {
    public BlockPos userPos;

    public AcquireCategoryPacket() {
    }

    public AcquireCategoryPacket(BlockPos userPos) {
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