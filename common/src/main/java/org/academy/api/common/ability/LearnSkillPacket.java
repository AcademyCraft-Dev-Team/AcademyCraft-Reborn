package org.academy.api.common.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.future.IRequestPayload;
import org.academy.api.common.network.future.IResponsePayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LearnSkillPacket extends IRequestPayload<ServerGamePacketListenerImpl> {
    public String skillName;
    public BlockPos userPos;

    public LearnSkillPacket() {}

    public LearnSkillPacket(String skillName, BlockPos userPos) {
        this.skillName = skillName;
        this.userPos = userPos;
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeUtf(this.skillName);
        buf.writeBlockPos(this.userPos);
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.skillName = buf.readUtf();
        this.userPos = buf.readBlockPos();
    }

    @Nullable
    @Override
    public Class<Response> getExpectedResponseType() {
        return Response.class;
    }

    public static class Response implements IResponsePayload {
        public boolean success;

        public Response() {
        }

        public Response(boolean success) {
            this.success = success;
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeBoolean(success);
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            this.success = buf.readBoolean();
        }
    }
}