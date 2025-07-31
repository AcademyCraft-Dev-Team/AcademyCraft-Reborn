package org.academy.api.common.wireless;

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

public class GetAvailableNodesPacket extends RequestPayload<ServerGamePacketListenerImpl, GetAvailableNodesPacket.Response> {
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

    @Override
    public @NotNull PayloadType<?, Response> getExpectedResponsePayloadType() {
        return PayloadTypes.GET_AVAILABLE_NODES_RESPONSE.get();
    }

    @Override
    public @NotNull PayloadType<ServerGamePacketListenerImpl, ? extends Payload<ServerGamePacketListenerImpl>> getPayloadType() {
        return PayloadTypes.GET_AVAILABLE_NODES.get();
    }

    public static class Response extends ResponsePayload<ClientPacketListener> {
        public ArrayList<String> availableNodeNames;

        public Response(ClientPacketListener listener) {
            super(listener);
        }

        public Response(List<String> newNames) {
            availableNodeNames = new ArrayList<>(newNames);
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            FBBSerializers.getCollectionFriendlyByteBufSerializer(String.class).serialize(buf, availableNodeNames);
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            availableNodeNames = FBBDeserializers.getCollectionFriendlyByteBufDeserializer(String.class, ArrayList::new).deserialize(buf);
        }

        @Override
        public @NotNull PayloadType<ClientPacketListener, ? extends Payload<ClientPacketListener>> getPayloadType() {
            return PayloadTypes.GET_AVAILABLE_NODES_RESPONSE.get();
        }
    }
}