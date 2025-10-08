package org.academy.internal.common.network;

import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AcquireCategoryPacket;
import org.academy.api.common.ability.ExpSyncPacket;
import org.academy.api.common.ability.LearnSkillPacket;
import org.academy.api.common.ability.PlayerSyncPacket;
import org.academy.api.common.ability.packet.sync.s2c.*;
import org.academy.api.common.vanilla.OpenScreenPacket;
import org.academy.api.common.wireless.*;
import org.academy.internal.common.ability.accelerator.skills.*;
import org.academy.internal.common.ability.electromaster.skills.ArcGenerate;
import org.academy.internal.common.ability.electromaster.skills.Railgun;
import org.academy.internal.common.ability.meltdowner.skills.SingleHighSpeedElectronBeam;
import org.academy.internal.common.ability.teleport.skills.SelfTeleport;
import org.academy.internal.common.core.particles.SpawnArcMediumParticlePacket;
import org.academy.internal.common.world.item.CoinItem;
import org.academy.internal.common.world.item.ImagiphaseDowsingRodItem;
import org.misaka.MisakaNetworkRegistries;
import org.misaka.api.common.network.packet.PacketType;

public final class PacketTypes {
    public static final DeferredRegister<PacketType<?, ?>> PACKET_TYPES =
            DeferredRegister.create(MisakaNetworkRegistries.Keys.PACKET_TYPES, AcademyCraft.MOD_ID);

    /**
     * Sync
     */
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, SyncLevelPacket>>
            SYNC_LEVEL = PACKET_TYPES.register("sync_level",
            () -> new PacketType<>(SyncLevelPacket.class, SyncLevelPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, SyncSkillsPacket>>
            SYNC_SKILLS = PACKET_TYPES.register("sync_skills",
            () -> new PacketType<>(SyncSkillsPacket.class, SyncSkillsPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, SyncAbilityCategoryPacket>>
            SYNC_ABILITY_CATEGORY = PACKET_TYPES.register("sync_ability_category",
            () -> new PacketType<>(SyncAbilityCategoryPacket.class, SyncAbilityCategoryPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, SyncComputingPowerPacket>>
            SYNC_COMPUTING_POWER = PACKET_TYPES.register("sync_computing_power",
            () -> new PacketType<>(SyncComputingPowerPacket.class, SyncComputingPowerPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, SyncMaxComputingPowerPacket>>
            SYNC_MAX_COMPUTING_POWER = PACKET_TYPES.register("sync_max_computing_power",
            () -> new PacketType<>(SyncMaxComputingPowerPacket.class, SyncMaxComputingPowerPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, PlayerSyncPacket>>
            PLAYER_SYNC = PACKET_TYPES.register("player_sync",
            () -> new PacketType<>(PlayerSyncPacket.class, PlayerSyncPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, ExpSyncPacket>>
            EXP_SYNC = PACKET_TYPES.register("exp_sync",
            () -> new PacketType<>(ExpSyncPacket.class, ExpSyncPacket.CODEC));

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

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, OpenScreenPacket>>
            OPEN_SCREEN = PACKET_TYPES.register("open_screen",
            () -> new PacketType<>(OpenScreenPacket.class, OpenScreenPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, CoinItem.ThrowCoinPacket>>
            THROW_COIN_WITH_VELOCITY = PACKET_TYPES.register("throw_coin_with_velocity",
            () -> new PacketType<>(CoinItem.ThrowCoinPacket.class, CoinItem.ThrowCoinPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, StormWing.TogglePacket>>
            STORM_WING_TOGGLE = PACKET_TYPES.register("storm_wing_toggle",
            () -> new PacketType<>(StormWing.TogglePacket.class, StormWing.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, StormWing.ControlPacket>>
            STORM_WING_CONTROL = PACKET_TYPES.register("storm_wing_control",
            () -> new PacketType<>(StormWing.ControlPacket.class, StormWing.ControlPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VectorAccel.DashPacket>>
            VECTOR_ACCEL_DASH = PACKET_TYPES.register("vector_accel_dash",
            () -> new PacketType<>(VectorAccel.DashPacket.class, VectorAccel.DashPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ArcGenerate.GeneratePacket>>
            ARC_GENERATE_GENERATE = PACKET_TYPES.register("arc_generate_generate",
            () -> new PacketType<>(ArcGenerate.GeneratePacket.class, ArcGenerate.GeneratePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Railgun.ShootPacket>>
            RAILGUN_SHOOT = PACKET_TYPES.register("railgun_shoot",
            () -> new PacketType<>(Railgun.ShootPacket.class, Railgun.ShootPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Railgun.StartChargePacket>>
            RAILGUN_START_CHARGE = PACKET_TYPES.register("railgun_start_charge",
            () -> new PacketType<>(Railgun.StartChargePacket.class, Railgun.StartChargePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, Railgun.ConfirmChargePacket>>
            RAILGUN_CONFIRM_CHARGE = PACKET_TYPES.register("railgun_confirm_charge",
            () -> new PacketType<>(Railgun.ConfirmChargePacket.class, Railgun.ConfirmChargePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, Railgun.ChargeEndPacket>>
            RAILGUN_CHARGE_END = PACKET_TYPES.register("railgun_charge_end",
            () -> new PacketType<>(Railgun.ChargeEndPacket.class, Railgun.ChargeEndPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SingleHighSpeedElectronBeam.ShootPacket>>
            SINGLE_HIGH_SPEED_ELECTRON_BEAM_SHOOT = PACKET_TYPES.register("single_high_speed_electron_beam_shoot",
            () -> new PacketType<>(SingleHighSpeedElectronBeam.ShootPacket.class, SingleHighSpeedElectronBeam.ShootPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SelfTeleport.SelfTeleportPacket>>
            SELF_TELEPORT = PACKET_TYPES.register("self_teleport",
            () -> new PacketType<>(SelfTeleport.SelfTeleportPacket.class, SelfTeleport.SelfTeleportPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DirStrike.ActionPacket>>
            DIR_STRIKE = PACKET_TYPES.register("dir_strike",
            () -> new PacketType<>(DirStrike.ActionPacket.class, DirStrike.ActionPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, KineticEnergyApplied.TogglePacket>>
            KINETIC_ENERGY_APPLIED_TOGGLE = PACKET_TYPES.register("kinetic_energy_applied_toggle",
            () -> new PacketType<>(KineticEnergyApplied.TogglePacket.class, KineticEnergyApplied.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VectorReflection.TogglePacket>>
            VECTOR_REFLECTION_TOGGLE = PACKET_TYPES.register("vector_reflection_toggle",
            () -> new PacketType<>(VectorReflection.TogglePacket.class, VectorReflection.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, BloodflowReverse.ReverseBloodflowPacket>>
            REVERSE_BLOODFLOW = PACKET_TYPES.register("reverse_bloodflow",
            () -> new PacketType<>(BloodflowReverse.ReverseBloodflowPacket.class, BloodflowReverse.ReverseBloodflowPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, SpawnArcMediumParticlePacket>>
            SPAWN_ARC_MEDIUM_PARTICLE = PACKET_TYPES.register("spawn_arc_medium_particle",
            () -> new PacketType<>(SpawnArcMediumParticlePacket.class, SpawnArcMediumParticlePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AcquireCategoryPacket>>
            ACQUIRE_CATEGORY = PACKET_TYPES.register("acquire_category",
            () -> new PacketType<>(AcquireCategoryPacket.class, AcquireCategoryPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, AcquireCategoryPacket.Response>>
            ACQUIRE_CATEGORY_RESPONSE = PACKET_TYPES.register("acquire_category_response",
            () -> new PacketType<>(AcquireCategoryPacket.Response.class, AcquireCategoryPacket.Response.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, LearnSkillPacket>>
            LEARN_SKILL = PACKET_TYPES.register("learn_skill",
            () -> new PacketType<>(LearnSkillPacket.class, LearnSkillPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, LearnSkillPacket.Response>>
            LEARN_SKILL_RESPONSE = PACKET_TYPES.register("learn_skill_response",
            () -> new PacketType<>(LearnSkillPacket.Response.class, LearnSkillPacket.Response.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, GetAvailableNodesPacket>>
            GET_AVAILABLE_NODES = PACKET_TYPES.register("get_available_nodes",
            () -> new PacketType<>(GetAvailableNodesPacket.class, GetAvailableNodesPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, GetAvailableNodesPacket.Response>>
            GET_AVAILABLE_NODES_RESPONSE = PACKET_TYPES.register("get_available_nodes_response",
            () -> new PacketType<>(GetAvailableNodesPacket.Response.class, GetAvailableNodesPacket.Response.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, GetCurrentNodePacket>>
            GET_CURRENT_NODE = PACKET_TYPES.register("get_current_node",
            () -> new PacketType<>(GetCurrentNodePacket.class, GetCurrentNodePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, GetCurrentNodePacket.Response>>
            GET_CURRENT_NODE_RESPONSE = PACKET_TYPES.register("get_current_node_response",
            () -> new PacketType<>(GetCurrentNodePacket.Response.class, GetCurrentNodePacket.Response.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket>>
            GET_LEVEL_CHUNK_SECTIONS = PACKET_TYPES.register("get_level_chunk_sections",
            () -> new PacketType<>(ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.class, ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientGamePacketListener, ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response>>
            GET_LEVEL_CHUNK_SECTIONS_RESPONSE = PACKET_TYPES.register("get_level_chunk_sections_response",
            () -> new PacketType<>(ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response.class, ImagiphaseDowsingRodItem.GetLevelChunkSectionsPacket.Response.CODEC));

    private PacketTypes() {
    }
}