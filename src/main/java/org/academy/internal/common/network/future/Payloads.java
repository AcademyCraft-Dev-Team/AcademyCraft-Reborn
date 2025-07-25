package org.academy.internal.common.network.future;

import net.minecraft.network.PacketListener;
import org.academy.api.common.ability.AcquireCategoryPacket;
import org.academy.api.common.ability.LearnSkillPacket;
import org.academy.api.common.network.future.FutureManager;
import org.academy.api.common.network.future.IPayload;
import org.academy.api.common.wireless.GetAvailableNodesPacket;
import org.academy.api.common.wireless.GetCurrentNodePacket;
import org.academy.internal.common.world.item.ImagiphaseDowsingRodItem;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@SuppressWarnings({"unused", "rawtypes"})
public final class Payloads {
    private static final Map<Class<? extends IPayload>, Function<? extends PacketListener, ? extends IPayload>>
            REGISTERED_PAYLOAD_FACTORIES = new HashMap<>();

    private Payloads() {
    }

    private static <T extends IPayload<PL>, PL extends PacketListener> Class<T> register(Class<T> payloadClass, Function<PL, T> factory) {
        REGISTERED_PAYLOAD_FACTORIES.put(payloadClass, factory);
        return payloadClass;
    }

    public static final Class<AcquireCategoryPacket> ACQUIRE_CATEGORY_PACKET =
            register(AcquireCategoryPacket.class, AcquireCategoryPacket::new);
    public static final Class<AcquireCategoryPacket.Response> ACQUIRE_CATEGORY_PACKET_RESPONSE =
            register(AcquireCategoryPacket.Response.class, AcquireCategoryPacket.Response::new);

    public static final Class<LearnSkillPacket> LEARN_SKILL_PACKET =
            register(LearnSkillPacket.class, LearnSkillPacket::new);
    public static final Class<LearnSkillPacket.Response> LEARN_SKILL_PACKET_RESPONSE =
            register(LearnSkillPacket.Response.class, LearnSkillPacket.Response::new);

    public static final Class<GetAvailableNodesPacket> GET_AVAILABLE_NODES_PACKET =
            register(GetAvailableNodesPacket.class, GetAvailableNodesPacket::new);
    public static final Class<GetAvailableNodesPacket.Response> GET_AVAILABLE_NODES_PACKET_RESPONSE =
            register(GetAvailableNodesPacket.Response.class, GetAvailableNodesPacket.Response::new);

    public static final Class<GetCurrentNodePacket> GET_CURRENT_NODE_PACKET =
            register(GetCurrentNodePacket.class, GetCurrentNodePacket::new);
    public static final Class<GetCurrentNodePacket.Response> GET_CURRENT_NODE_PACKET_RESPONSE =
            register(GetCurrentNodePacket.Response.class, GetCurrentNodePacket.Response::new);

    public static final Class<ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket> GET_LEVEL_CHUNK_SECTIONS_PACKET =
            register(ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.class, ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket::new);
    public static final Class<ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response> GET_LEVEL_CHUNK_SECTIONS_PACKET_RESPONSE =
            register(ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response.class, ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response::new);

    @SuppressWarnings({"unchecked"})
    public static void registerAll(FutureManager futureManager) {
        for (var entry : REGISTERED_PAYLOAD_FACTORIES.entrySet()) {
            futureManager.registerPayloadType((Class) entry.getKey(), (Function<PacketListener, IPayload>) entry.getValue());
        }
    }
}