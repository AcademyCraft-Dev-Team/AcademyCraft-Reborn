package org.academy.api.client.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.C2SPacket;

import java.util.HashMap;
import java.util.function.Consumer;

public class FutureManagerClient {
    public static final HashMap<Integer, Consumer<?>> FUTURES = new HashMap<>();

    public static <T> void sendFuturePacket(ResourceLocation resourceLocation, Consumer<T> handler, Object... values) {
        sendFuturePacket(NetworkSystem.getPacketId(resourceLocation), handler, values);
    }

    public static <T> void sendFuturePacket(int packetID, Consumer<T> handler, Object... values) {
        int id = FUTURES.size();
        FUTURES.put(id, handler);

        Object[] allValues = new Object[2 + values.length];
        allValues[0] = packetID;
        allValues[1] = id;
        System.arraycopy(values, 0, allValues, 2, values.length);

        NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_FUTURE, allValues));
    }

    @SuppressWarnings({"DataFlowIssue", "unchecked"})
    public static void register() {
        NetworkSystemClient.registerS2CPacketHandler(Packets.S2C_FUTURE, (listener, packet) -> {
            FriendlyByteBuf buf = packet.friendlyByteBuf;
            int id = buf.readVarInt();
            int deserializerId = buf.readVarInt();
            Object value = FriendlyByteBufDeserializers.getDeserializer(deserializerId).deserialize(packet.friendlyByteBuf);
            Consumer<Object> consumer = (Consumer<Object>) FUTURES.get(id);
            consumer.accept(value);
        });
    }

    private FutureManagerClient() {
    }
}