package org.academy.api.common.ability;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.FBBDeserializers;
import org.academy.api.common.network.FBBSerializers;
import org.academy.api.common.network.future.Payload;
import org.academy.api.common.network.future.PayloadType;
import org.academy.api.common.network.future.RequestPayload;
import org.academy.api.common.network.future.ResponsePayload;
import org.academy.internal.common.network.future.PayloadTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AcquireCategoryPacket extends RequestPayload<ServerGamePacketListenerImpl, AcquireCategoryPacket.Response> {
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

    @Override
    public @NotNull PayloadType<ServerGamePacketListenerImpl, ? extends Payload<ServerGamePacketListenerImpl>> getPayloadType() {
        return PayloadTypes.ACQUIRE_CATEGORY.get();
    }

    @Override
    public @NotNull PayloadType<?, Response> getExpectedResponsePayloadType() {
        return PayloadTypes.ACQUIRE_CATEGORY_RESPONSE.get();
    }

    public static class Response extends ResponsePayload<ClientPacketListener> {
        public List<String> messages;

        public Response(ClientPacketListener listener) {
            super(listener);
        }

        public Response(List<String> messages) {
            this.messages = new ArrayList<>(messages);
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            FBBSerializers.getCollectionFriendlyByteBufSerializer(String.class).serialize(buf, messages);
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            messages = FBBDeserializers.getCollectionFriendlyByteBufDeserializer(String.class, ArrayList::new).deserialize(buf);
        }

        @Override
        public @NotNull PayloadType<ClientPacketListener, ? extends Payload<ClientPacketListener>> getPayloadType() {
            return PayloadTypes.ACQUIRE_CATEGORY_RESPONSE.get();
        }
    }
}