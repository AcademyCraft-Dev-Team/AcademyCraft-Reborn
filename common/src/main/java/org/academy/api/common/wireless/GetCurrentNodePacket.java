package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.future.IRequestPayload;
import org.academy.api.common.network.future.IResponsePayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GetCurrentNodePacket extends IRequestPayload<ServerGamePacketListenerImpl> {
    public BlockPos userPos;

    public GetCurrentNodePacket() {}

    public GetCurrentNodePacket(BlockPos userPos) {
        this.userPos = userPos;
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(this.userPos);
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.userPos = buf.readBlockPos();
    }

    @Nullable
    @Override
    public Class<Response> getExpectedResponseType() {
        return Response.class;
    }

    public static class Response implements IResponsePayload {
        public boolean isNull;
        public String nodeName;

        public Response() {
        }

        public Response(boolean isNull, String nodeName) {
            this.isNull = isNull;
            this.nodeName = nodeName;
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeBoolean(isNull);
            buf.writeUtf(nodeName);
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            this.isNull = buf.readBoolean();
            this.nodeName = buf.readUtf();
        }
    }
}