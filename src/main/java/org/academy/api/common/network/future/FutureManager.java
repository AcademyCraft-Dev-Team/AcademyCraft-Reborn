package org.academy.api.common.network.future;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.network.PacketListener;
import org.academy.api.common.registries.Registries;

public final class FutureManager {
    private static final BiMap<Class<? extends Payload<?>>, PayloadType<?, ?>> PAYLOAD_CLASS_TO_PAYLOAD_TYPE = HashBiMap.create();

    private FutureManager() {
    }

    public static void progressRegistry() {
        for (var payloadType : Registries.PAYLOAD_TYPES) {
            PAYLOAD_CLASS_TO_PAYLOAD_TYPE.put(payloadType.getPayloadClass(), payloadType);
        }
    }

    /**
     * 给参数加泛型也没什么用了, 这样还省事
     */
    @SuppressWarnings({"unchecked"})
    public static <T extends PayloadType<?, ?>> T getPayloadType(Class<?> payloadClass) {
        return (T) PAYLOAD_CLASS_TO_PAYLOAD_TYPE.get(payloadClass);
    }

    @SuppressWarnings("unchecked")
    public static <L extends PacketListener, P extends Payload<L>> PayloadType<L, P> getPayloadTypeById(int id) {
        return (PayloadType<L, P>) Registries.PAYLOAD_TYPES.byIdOrThrow(id);
    }
}