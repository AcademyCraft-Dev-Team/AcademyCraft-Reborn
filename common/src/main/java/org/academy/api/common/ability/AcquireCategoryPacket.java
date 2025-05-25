package org.academy.api.common.ability;

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

public class AcquireCategoryPacket extends IRequestPayload<ServerGamePacketListenerImpl, AcquireCategoryPacket.Response> {
    public BlockPos userPos;

    public AcquireCategoryPacket() {
    }

    public AcquireCategoryPacket(BlockPos userPos) {
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
        public List<String> messages;

        public Response() {
        }

        public Response(List<String> messages) {
            this.messages = new ArrayList<>(messages);
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            FriendlyByteBufSerializers.getCollectionFriendlyByteBufSerializer(String.class).serialize(buf, messages);
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            this.messages = FriendlyByteBufDeserializers.getCollectionFriendlyByteBufDeserializer(String.class, ArrayList::new).deserialize(buf);
        }
    }
}