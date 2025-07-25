package org.academy.api.common.ability;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.future.IRequestPayload;
import org.academy.api.common.network.future.IResponsePayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LearnSkillPacket extends IRequestPayload<ServerGamePacketListenerImpl, LearnSkillPacket.Response> {
    public String skillName;
    public BlockPos userPos;

    public LearnSkillPacket(ServerGamePacketListenerImpl listener) {
        super(listener);
    }

    public LearnSkillPacket(String newSkillName, BlockPos newUserPos) {
        super(null);
        skillName = newSkillName;
        userPos = newUserPos;
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeUtf(skillName);
        buf.writeBlockPos(userPos);
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        skillName = buf.readUtf();
        userPos = buf.readBlockPos();
    }

    @Nullable
    @Override
    public Class<Response> getExpectedResponseType() {
        return Response.class;
    }

    public static class Response extends IResponsePayload<ClientPacketListener> {
        public boolean success;

        public Response(ClientPacketListener listener) {
            super(listener);
        }

        public Response(boolean newSuccess) {
            super(null);
            success = newSuccess;
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeBoolean(success);
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            success = buf.readBoolean();
        }
    }
}