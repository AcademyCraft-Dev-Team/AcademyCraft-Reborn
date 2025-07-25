package org.academy.api.common.ability;

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

public class AcquireCategoryPacket extends IRequestPayload<ServerGamePacketListenerImpl, AcquireCategoryPacket.Response> {
    public BlockPos userPos;

    public AcquireCategoryPacket(ServerGamePacketListenerImpl listener) {
        super(listener);
    }

    public AcquireCategoryPacket(BlockPos newUserPos) {
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

    @Nullable
    @Override
    public Class<Response> getExpectedResponseType() {
        return Response.class;
    }

    public static class Response extends IResponsePayload<ClientPacketListener> {
        public List<String> messages;

        public Response(ClientPacketListener listener) {
            super(listener);
        }

        public Response(List<String> messages) {
            super(null);
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