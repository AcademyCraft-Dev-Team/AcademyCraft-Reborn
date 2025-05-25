package org.academy.api.common.wireless;

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

    public GetAvailableNodesPacket() {
    }

    public GetAvailableNodesPacket(BlockPos requesterPos) {
        this.requesterPos = requesterPos;
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBlockPos(this.requesterPos);
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.requesterPos = buf.readBlockPos();
    }

    @Nullable
    @Override
    public Class<Response> getExpectedResponseType() {
        return Response.class;
    }

    public static class Response implements IResponsePayload {
        public ArrayList<String> availableNodeNames;

        public Response() {
        }

        public Response(List<String> names) {
            this.availableNodeNames = new ArrayList<>(names);
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            FriendlyByteBufSerializers.getCollectionFriendlyByteBufSerializer(String.class).serialize(buf, availableNodeNames);
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            this.availableNodeNames = FriendlyByteBufDeserializers.getCollectionFriendlyByteBufDeserializer(String.class, ArrayList::new).deserialize(buf);
        }
    }
}