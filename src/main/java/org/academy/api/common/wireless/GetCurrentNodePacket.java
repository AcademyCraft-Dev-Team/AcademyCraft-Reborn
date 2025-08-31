package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.future.Payload;
import org.academy.api.common.network.future.PayloadType;
import org.academy.api.common.network.future.RequestPayload;
import org.academy.api.common.network.future.ResponsePayload;
import org.academy.internal.common.network.future.PayloadTypes;
import org.jetbrains.annotations.NotNull;

public class GetCurrentNodePacket extends RequestPayload<ServerGamePacketListenerImpl, GetCurrentNodePacket.Response> {
    public BlockPos userPos;

    public GetCurrentNodePacket(ServerGamePacketListenerImpl listener) {
        super(listener);
    }

    public GetCurrentNodePacket(BlockPos newUserPos) {
        super(null);
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

    @Override
    public @NotNull PayloadType<?, Response> getExpectedResponsePayloadType() {
        return PayloadTypes.GET_CURRENT_NODE_RESPONSE.get();
    }

    @Override
    public @NotNull PayloadType<ServerGamePacketListenerImpl, ? extends Payload<ServerGamePacketListenerImpl>> getPayloadType() {
        return PayloadTypes.GET_CURRENT_NODE.get();
    }

    public static class Response extends ResponsePayload<ClientGamePacketListener> {
        public boolean isNull;
        public String nodeName;

        public Response(ClientGamePacketListener packetListener) {
            super(packetListener);
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

        @Override
        public @NotNull PayloadType<ClientGamePacketListener, ? extends Payload<ClientGamePacketListener>> getPayloadType() {
            return PayloadTypes.GET_CURRENT_NODE_RESPONSE.get();
        }
    }
}