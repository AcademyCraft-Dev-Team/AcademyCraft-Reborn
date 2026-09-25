package org.academy.api.common.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.*;
import org.academy.api.common.ability.darkmatter.SyncDarkmatterStatePacket;
import org.academy.api.common.ability.pakcet.SyncAbilityCategoryPacket;
import org.academy.api.common.ability.pakcet.SyncAbilityDataPacket;
import org.academy.api.common.ability.pakcet.SyncSkillDataPacket;
import org.academy.api.common.sync.packet.SyncDataPacket;
import org.academy.api.common.util.UncheckedUtil;
import org.academy.api.common.vanilla.OpenScreenPacket;
import org.academy.api.common.wireless.*;
import org.academy.api.server.ability.SkillTuning;
import org.misaka.api.common.network.packet.PacketType;
import org.misaka.api.common.registries.MisakaNetworkRegistries;

public final class PacketTypes {
    public static final DeferredRegister<PacketType<?, ?>> PACKET_TYPES =
            DeferredRegister.create(MisakaNetworkRegistries.Keys.PACKET_TYPES, AcademyCraft.MOD_ID);

    @SuppressWarnings("unchecked")
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, ?>>
            SYNC_DATA = PACKET_TYPES.register("sync_data",
            () -> new PacketType<>(SyncDataPacket.class, UncheckedUtil.uncheckedCast(SyncDataPacket.CODEC)));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, SkillTuning.SyncPacket>>
            SKILL_TUNING_SYNC = PACKET_TYPES.register("skill_tuning_sync",
            () -> new PacketType<>(SkillTuning.SyncPacket.class, SkillTuning.SyncPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, SyncDarkmatterStatePacket>>
            SYNC_DARKMATTER_STATE = PACKET_TYPES.register("sync_darkmatter_state",
            () -> new PacketType<>(SyncDarkmatterStatePacket.class, SyncDarkmatterStatePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, AirMobilitySyncPacket>>
            AIR_MOBILITY_SYNC = PACKET_TYPES.register("air_mobility_sync",
            () -> new PacketType<>(AirMobilitySyncPacket.class, AirMobilitySyncPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, SyncAbilityCategoryPacket>>
            SYNC_ABILITY_CATEGORY = PACKET_TYPES.register("sync_ability_category",
            () -> new PacketType<>(SyncAbilityCategoryPacket.class, SyncAbilityCategoryPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, SyncAbilityDataPacket>>
            SYNC_ABILITY_DATA = PACKET_TYPES.register("sync_ability_data",
            () -> new PacketType<>(SyncAbilityDataPacket.class, SyncAbilityDataPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, SyncSkillDataPacket>>
            SYNC_SKILL_DATA = PACKET_TYPES.register("sync_skill_data",
            () -> new PacketType<>(SyncSkillDataPacket.class, SyncSkillDataPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ConnectNodePacket>>
            CONNECT_NODE = PACKET_TYPES.register("connect_node",
            () -> new PacketType<>(ConnectNodePacket.class, ConnectNodePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DisconnectNodePacket>>
            DISCONNECT_NODE = PACKET_TYPES.register("disconnect_node",
            () -> new PacketType<>(DisconnectNodePacket.class, DisconnectNodePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SetNodeNamePacket>>
            SET_NODE_NAME = PACKET_TYPES.register("set_node_name",
            () -> new PacketType<>(SetNodeNamePacket.class, SetNodeNamePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SetNodePassPacket>>
            SET_NODE_PASS = PACKET_TYPES.register("set_node_pass",
            () -> new PacketType<>(SetNodePassPacket.class, SetNodePassPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, OpenScreenPacket>>
            OPEN_SCREEN = PACKET_TYPES.register("open_screen",
            () -> new PacketType<>(OpenScreenPacket.class, OpenScreenPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, LearnSkillPacket>>
            LEARN_SKILL = PACKET_TYPES.register("learn_skill",
            () -> new PacketType<>(LearnSkillPacket.class, LearnSkillPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, LearnSkillPacket.Response>>
            LEARN_SKILL_RESPONSE = PACKET_TYPES.register("learn_skill_response",
            () -> new PacketType<>(LearnSkillPacket.Response.class, LearnSkillPacket.Response.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, GetAvailableNodesPacket>>
            GET_AVAILABLE_NODES = PACKET_TYPES.register("get_available_nodes",
            () -> new PacketType<>(GetAvailableNodesPacket.class, GetAvailableNodesPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, GetAvailableNodesPacket.Response>>
            GET_AVAILABLE_NODES_RESPONSE = PACKET_TYPES.register("get_available_nodes_response",
            () -> new PacketType<>(GetAvailableNodesPacket.Response.class, GetAvailableNodesPacket.Response.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, GetCurrentNodePacket>>
            GET_CURRENT_NODE = PACKET_TYPES.register("get_current_node",
            () -> new PacketType<>(GetCurrentNodePacket.class, GetCurrentNodePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, GetCurrentNodePacket.Response>>
            GET_CURRENT_NODE_RESPONSE = PACKET_TYPES.register("get_current_node_response",
            () -> new PacketType<>(GetCurrentNodePacket.Response.class, GetCurrentNodePacket.Response.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, StartSkillDevPacket>>
            START_SKILL_DEV = PACKET_TYPES.register("start_skill_dev",
            () -> new PacketType<>(StartSkillDevPacket.class, StartSkillDevPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, StartSkillDevPacket.Response>>
            START_SKILL_DEV_RESPONSE = PACKET_TYPES.register("start_skill_dev_response",
            () -> new PacketType<>(StartSkillDevPacket.Response.class, StartSkillDevPacket.Response.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, StartLevelDevPacket>>
            START_LEVEL_DEV = PACKET_TYPES.register("start_level_dev",
            () -> new PacketType<>(StartLevelDevPacket.class, StartLevelDevPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, StartLevelDevPacket.Response>>
            START_LEVEL_DEV_RESPONSE = PACKET_TYPES.register("start_level_dev_response",
            () -> new PacketType<>(StartLevelDevPacket.Response.class, StartLevelDevPacket.Response.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, DevSyncPacket>>
            DEV_SYNC = PACKET_TYPES.register("dev_sync",
            () -> new PacketType<>(DevSyncPacket.class, DevSyncPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, StopDevPacket>>
            STOP_DEV = PACKET_TYPES.register("stop_dev",
            () -> new PacketType<>(StopDevPacket.class, StopDevPacket.CODEC));

    private PacketTypes() {
    }
}
