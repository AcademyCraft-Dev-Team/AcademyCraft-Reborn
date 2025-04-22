package org.academy.api.server.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.S2CPacket;

public class FutureManagerServer {
    public static void register() {
        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_FUTURE, (listener, packet) -> {
            FriendlyByteBuf buf = packet.friendlyByteBuf;
            int packetID = buf.readVarInt();
            NetworkSystemServer.C2S_PACKET_HANDLER_MAP.get(NetworkSystem.getPacketResourceLocation(packetID)).handle(listener, packet);
        });
    }

    public static <T> void sendResult(ServerGamePacketListenerImpl listener, int id, T value) {
        Class<?> clazz = value.getClass();
        int deserializerId = FriendlyByteBufDeserializers.getDeserializerId(FriendlyByteBufDeserializers.getDeserializer(clazz));
        listener.send(new S2CPacket(Packets.S2C_FUTURE, id, deserializerId, value));
    }

    private FutureManagerServer() {
    }
}