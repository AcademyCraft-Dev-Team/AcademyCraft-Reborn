package org.academy.internal.common.network.future;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AcquireCategoryPacket;
import org.academy.api.common.ability.LearnSkillPayload;
import org.academy.api.common.network.future.PayloadType;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.wireless.GetAvailableNodesPacket;
import org.academy.api.common.wireless.GetCurrentNodePacket;
import org.academy.internal.common.world.item.ImagiphaseDowsingRodItem;

public final class PayloadTypes {
    public static final DeferredRegister<PayloadType<?, ?>> PAYLOAD_TYPES =
            DeferredRegister.create(Registries.Keys.PAYLOAD_TYPES, AcademyCraft.MOD_ID);

    public static final DeferredHolder<PayloadType<?, ?>, PayloadType<ServerGamePacketListenerImpl, AcquireCategoryPacket>>
            ACQUIRE_CATEGORY = PAYLOAD_TYPES.register("acquire_category",
            () -> new PayloadType<>(AcquireCategoryPacket.class, AcquireCategoryPacket::new));

    public static final DeferredHolder<PayloadType<?, ?>, PayloadType<ClientPacketListener, AcquireCategoryPacket.Response>>
            ACQUIRE_CATEGORY_RESPONSE = PAYLOAD_TYPES.register("acquire_category_response",
            () -> new PayloadType<>(AcquireCategoryPacket.Response.class, AcquireCategoryPacket.Response::new));

    public static final DeferredHolder<PayloadType<?, ?>, PayloadType<ServerGamePacketListenerImpl, LearnSkillPayload>>
            LEARN_SKILL = PAYLOAD_TYPES.register("learn_skill",
            () -> new PayloadType<>(LearnSkillPayload.class, LearnSkillPayload::new));

    public static final DeferredHolder<PayloadType<?, ?>, PayloadType<ClientPacketListener, LearnSkillPayload.Response>>
            LEARN_SKILL_RESPONSE = PAYLOAD_TYPES.register("learn_skill_response",
            () -> new PayloadType<>(LearnSkillPayload.Response.class, LearnSkillPayload.Response::new));

    public static final DeferredHolder<PayloadType<?, ?>, PayloadType<ServerGamePacketListenerImpl, GetAvailableNodesPacket>>
            GET_AVAILABLE_NODES = PAYLOAD_TYPES.register("get_available_nodes",
            () -> new PayloadType<>(GetAvailableNodesPacket.class, GetAvailableNodesPacket::new));

    public static final DeferredHolder<PayloadType<?, ?>, PayloadType<ClientPacketListener, GetAvailableNodesPacket.Response>>
            GET_AVAILABLE_NODES_RESPONSE = PAYLOAD_TYPES.register("get_available_nodes_response",
            () -> new PayloadType<>(GetAvailableNodesPacket.Response.class, GetAvailableNodesPacket.Response::new));

    public static final DeferredHolder<PayloadType<?, ?>, PayloadType<ServerGamePacketListenerImpl, GetCurrentNodePacket>>
            GET_CURRENT_NODE = PAYLOAD_TYPES.register("get_current_node",
            () -> new PayloadType<>(GetCurrentNodePacket.class, GetCurrentNodePacket::new));

    public static final DeferredHolder<PayloadType<?, ?>, PayloadType<ClientPacketListener, GetCurrentNodePacket.Response>>
            GET_CURRENT_NODE_RESPONSE = PAYLOAD_TYPES.register("get_current_node_response",
            () -> new PayloadType<>(GetCurrentNodePacket.Response.class, GetCurrentNodePacket.Response::new));

    public static final DeferredHolder<PayloadType<?, ?>, PayloadType<ServerGamePacketListenerImpl, ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket>>
            GET_LEVEL_CHUNK_SECTIONS = PAYLOAD_TYPES.register("get_level_chunk_sections",
            () -> new PayloadType<>(ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.class, ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket::new));

    public static final DeferredHolder<PayloadType<?, ?>, PayloadType<ClientPacketListener, ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response>>
            GET_LEVEL_CHUNK_SECTIONS_RESPONSE = PAYLOAD_TYPES.register("get_level_chunk_sections_response",
            () -> new PayloadType<>(ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response.class, ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response::new));

    private PayloadTypes() {
    }
}