package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.future.IRequestPayload;
import org.academy.api.common.network.future.IResponsePayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GetCurrentNodePacket extends IRequestPayload<ServerGamePacketListenerImpl, GetCurrentNodePacket.Response> {
    public BlockPos userPos;

    public GetCurrentNodePacket() {
    }

    public GetCurrentNodePacket(BlockPos newUserPos) {
        userPos = newUserPos;
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(userPos);
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        userPos = buf.readBlockPos();
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

        public Response(boolean newIsNull, String newNodeName) {
            isNull = newIsNull;
            nodeName = newNodeName;
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeBoolean(isNull);
            buf.writeUtf(nodeName);
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            isNull = buf.readBoolean();
            nodeName = buf.readUtf();
        }
    }
}