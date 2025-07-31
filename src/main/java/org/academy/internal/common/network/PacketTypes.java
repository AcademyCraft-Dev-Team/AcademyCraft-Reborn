package org.academy.internal.common.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.ExpSyncPacket;
import org.academy.api.common.ability.PlayerSyncPacket;
import org.academy.api.common.attachment.AttachmentDataSyncPacket;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.packet.FutureRequestPacket;
import org.academy.api.common.network.packet.FutureResponsePacket;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.vanilla.OpenScreenPacket;
import org.academy.api.common.wireless.ConnectNodePacket;
import org.academy.api.common.wireless.DisconnectNodePacket;
import org.academy.api.common.wireless.SetNodeNamePacket;
import org.academy.api.common.wireless.SetNodePassPacket;
import org.academy.internal.common.ability.accelerator.skills.*;
import org.academy.internal.common.ability.electromaster.skills.ArcGenerate;
import org.academy.internal.common.ability.electromaster.skills.Railgun;
import org.academy.internal.common.ability.meltdowner.skills.SingleHighSpeedElectronBeam;
import org.academy.internal.common.ability.teleport.skills.SelfTeleport;
import org.academy.internal.common.core.particles.SpawnArcMediumParticlePacket;
import org.academy.internal.common.world.item.CoinItem;

@SuppressWarnings({"unused"})
public final class PacketTypes {
    public static final DeferredRegister<PacketType<?, ?>> PACKET_TYPES =
            DeferredRegister.create(Registries.Keys.PACKET_TYPES, AcademyCraft.MOD_ID);

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, PlayerSyncPacket>>
            PLAYER_SYNC = PACKET_TYPES.register("player_sync",
            () -> new PacketType<>(PlayerSyncPacket.class, PlayerSyncPacket::new));

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, AttachmentDataSyncPacket<?>>>
            ATTACHMENT_DATA_SYNC = PACKET_TYPES.register("attachment_data_sync",
            () -> new PacketType<ClientPacketListener, AttachmentDataSyncPacket<?>>
                    ((Class) AttachmentDataSyncPacket.class, AttachmentDataSyncPacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, ExpSyncPacket>>
            EXP_SYNC = PACKET_TYPES.register("exp_sync",
            () -> new PacketType<>(ExpSyncPacket.class, ExpSyncPacket::new));

    private PacketTypes() {
    }

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ConnectNodePacket>>
            CONNECT_NODE = PACKET_TYPES.register("connect_node",
            () -> new PacketType<>(ConnectNodePacket.class, ConnectNodePacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DisconnectNodePacket>>
            DISCONNECT_NODE = PACKET_TYPES.register("disconnect_node",
            () -> new PacketType<>(DisconnectNodePacket.class, DisconnectNodePacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SetNodeNamePacket>>
            SET_NODE_NAME = PACKET_TYPES.register("set_node_name",
            () -> new PacketType<>(SetNodeNamePacket.class, SetNodeNamePacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SetNodePassPacket>>
            SET_NODE_PASS = PACKET_TYPES.register("set_node_pass",
            () -> new PacketType<>(SetNodePassPacket.class, SetNodePassPacket::new));


    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, OpenScreenPacket>>
            OPEN_SCREEN = PACKET_TYPES.register("open_screen",
            () -> new PacketType<>(OpenScreenPacket.class, OpenScreenPacket::new));

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final DeferredHolder<PacketType<?, ?>, PacketType<?, ?>>
            FUTURE_REQUEST = PACKET_TYPES.register("future_request",
            () -> new PacketType<>((Class) FutureRequestPacket.class, FutureRequestPacket::new));

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final DeferredHolder<PacketType<?, ?>, PacketType<?, ?>>
            FUTURE_RESPONSE = PACKET_TYPES.register("future_response",
            () -> new PacketType<>((Class) FutureResponsePacket.class, FutureResponsePacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, CoinItem.ThrowCoinPacket>>
            THROW_COIN_WITH_VELOCITY = PACKET_TYPES.register("throw_coin_with_velocity",
            () -> new PacketType<>(CoinItem.ThrowCoinPacket.class, CoinItem.ThrowCoinPacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, StormWing.TogglePacket>>
            STORM_WING_TOGGLE = PACKET_TYPES.register("storm_wing_toggle",
            () -> new PacketType<>(StormWing.TogglePacket.class, StormWing.TogglePacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, StormWing.ControlPacket>>
            STORM_WING_CONTROL = PACKET_TYPES.register("storm_wing_control",
            () -> new PacketType<>(StormWing.ControlPacket.class, StormWing.ControlPacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VectorAccel.DashPacket>>
            VECTOR_ACCEL_DASH = PACKET_TYPES.register("vector_accel_dash",
            () -> new PacketType<>(VectorAccel.DashPacket.class, VectorAccel.DashPacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ArcGenerate.GeneratePacket>>
            ARC_GENERATE_GENERATE = PACKET_TYPES.register("arc_generate_generate",
            () -> new PacketType<>(ArcGenerate.GeneratePacket.class, ArcGenerate.GeneratePacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Railgun.ShootPacket>>
            RAILGUN_SHOOT = PACKET_TYPES.register("railgun_shoot",
            () -> new PacketType<>(Railgun.ShootPacket.class, Railgun.ShootPacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Railgun.StartChargePacket>>
            RAILGUN_START_CHARGE = PACKET_TYPES.register("railgun_start_charge",
            () -> new PacketType<>(Railgun.StartChargePacket.class, Railgun.StartChargePacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, Railgun.ConfirmChargePacket>>
            RAILGUN_CONFIRM_CHARGE = PACKET_TYPES.register("railgun_confirm_charge",
            () -> new PacketType<>(Railgun.ConfirmChargePacket.class, Railgun.ConfirmChargePacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, Railgun.ChargeEndPacket>>
            RAILGUN_CHARGE_END = PACKET_TYPES.register("railgun_charge_end",
            () -> new PacketType<>(Railgun.ChargeEndPacket.class, Railgun.ChargeEndPacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SingleHighSpeedElectronBeam.ShootPacket>>
            SINGLE_HIGH_SPEED_ELECTRON_BEAM_SHOOT = PACKET_TYPES.register("single_high_speed_electron_beam_shoot",
            () -> new PacketType<>(SingleHighSpeedElectronBeam.ShootPacket.class, SingleHighSpeedElectronBeam.ShootPacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SelfTeleport.SelfTeleportPacket>>
            SELF_TELEPORT = PACKET_TYPES.register("self_teleport",
            () -> new PacketType<>(SelfTeleport.SelfTeleportPacket.class, SelfTeleport.SelfTeleportPacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DirStrike.ActionPacket>>
            DIR_STRIKE = PACKET_TYPES.register("dir_strike",
            () -> new PacketType<>(DirStrike.ActionPacket.class, DirStrike.ActionPacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, KineticEnergyApplied.TogglePacket>>
            KINETIC_ENERGY_APPLIED_TOGGLE = PACKET_TYPES.register("kinetic_energy_applied_toggle",
            () -> new PacketType<>(KineticEnergyApplied.TogglePacket.class, KineticEnergyApplied.TogglePacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VectorReflection.TogglePacket>>
            VECTOR_REFLECTION_TOGGLE = PACKET_TYPES.register("vector_reflection_toggle",
            () -> new PacketType<>(VectorReflection.TogglePacket.class, VectorReflection.TogglePacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, BloodflowReverse.ReverseBloodflowPacket>>
            REVERSE_BLOODFLOW = PACKET_TYPES.register("reverse_bloodflow",
            () -> new PacketType<>(BloodflowReverse.ReverseBloodflowPacket.class, BloodflowReverse.ReverseBloodflowPacket::new));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, SpawnArcMediumParticlePacket>>
            SPAWN_ARC_MEDIUM_PARTICLE = PACKET_TYPES.register("spawn_arc_medium_particle",
            () -> new PacketType<>(SpawnArcMediumParticlePacket.class, SpawnArcMediumParticlePacket::new));
}