package org.academy.api.common.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.FutureRequestPayload;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.SERVER)
public class LearnSkillPacket extends FutureRequestPayload {
    public String skillName;
    public BlockPos userPos;

    public LearnSkillPacket() {}

    public LearnSkillPacket(String skillName, BlockPos userPos) {
        this.skillName = skillName;
        this.userPos = userPos;
    }

    @Override
    public void readPayload(@NotNull FriendlyByteBuf buf) {
        this.skillName = buf.readUtf();
        this.userPos = buf.readBlockPos();
    }

    @Override
    public void writePayload(@NotNull FriendlyByteBuf buf) {
        buf.writeUtf(this.skillName);
        buf.writeBlockPos(this.userPos);
    }
}