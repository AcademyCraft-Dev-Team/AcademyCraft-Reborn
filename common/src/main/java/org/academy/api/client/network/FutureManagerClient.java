package org.academy.api.client.network;

import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.C2SPacket;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class FutureManagerClient {
    private static final Map<Integer, Consumer<?>> FUTURES = new HashMap<>();
    private static final BitSet USED_IDS = new BitSet();

    public static <T> void sendFuturePacket(String packet, Consumer<T> handler, Object... values) {
        sendFuturePacket(NetworkSystem.getPacketId(packet), handler, values);
    }

    public static <T> void sendFuturePacket(int packetID, Consumer<T> handler, Object... values) {
        int id = USED_IDS.nextClearBit(0);
        USED_IDS.set(id);

        FUTURES.put(id, handler);

        Object[] allValues = new Object[2 + values.length];
        allValues[0] = packetID;
        allValues[1] = id;
        System.arraycopy(values, 0, allValues, 2, values.length);

        NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_FUTURE, allValues));
    }

    @SuppressWarnings("unchecked")
    public static void register() {
        NetworkSystemClient.registerS2CPacketHandler(Packets.S2C_FUTURE, (listener, packet) -> {
            FriendlyByteBuf buf = packet.friendlyByteBuf;
            int id = buf.readVarInt();
            int deserializerId = buf.readVarInt();
            Object value = FriendlyByteBufDeserializers.getRequiredDeserializer(deserializerId).deserialize(buf);

            Consumer<Object> handler = (Consumer<Object>) FUTURES.remove(id);
            USED_IDS.clear(id);

            if (handler != null) {
                handler.accept(value);
            }
        });
    }

    private FutureManagerClient() {
    }
}