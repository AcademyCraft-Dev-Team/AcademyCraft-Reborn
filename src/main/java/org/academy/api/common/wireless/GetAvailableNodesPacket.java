package org.academy.api.common.wireless;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.academy.api.common.network.future.IRequestPayload;
import org.academy.api.common.network.future.IResponsePayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GetAvailableNodesPacket extends IRequestPayload<ServerGamePacketListenerImpl, GetAvailableNodesPacket.Response> {
    public BlockPos requesterPos;

    public GetAvailableNodesPacket(ServerGamePacketListenerImpl listener) {
        super(listener);
    }

    public GetAvailableNodesPacket(BlockPos newRequesterPos) {
        super(null);
        requesterPos = newRequesterPos;
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(requesterPos);
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        requesterPos = buf.readBlockPos();
    }

    @Nullable
    @Override
    public Class<Response> getExpectedResponseType() {
        return Response.class;
    }

    public static class Response extends IResponsePayload<ClientPacketListener> {
        public ArrayList<String> availableNodeNames;

        public Response(ClientPacketListener listener) {
            super(listener);
        }

        public Response(List<String> newNames) {
            super(null);
            availableNodeNames = new ArrayList<>(newNames);
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            FriendlyByteBufSerializers.getCollectionFriendlyByteBufSerializer(String.class).serialize(buf, availableNodeNames);
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            availableNodeNames = FriendlyByteBufDeserializers.getCollectionFriendlyByteBufDeserializer(String.class, ArrayList::new).deserialize(buf);
        }
    }
}