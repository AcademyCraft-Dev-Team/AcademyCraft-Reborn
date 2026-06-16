package org.academy.internal.common.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AcquireCategoryPacket;
import org.academy.api.common.ability.LearnSkillPacket;
import org.academy.api.common.ability.pakcet.SyncAbilityCategoryPacket;
import org.academy.api.common.ability.pakcet.SyncCPDataPacket;
import org.academy.api.common.ability.pakcet.SyncSkillDataPacket;
import org.academy.api.common.sync.packet.SyncDataPacket;
import org.academy.api.common.util.UncheckedUtil;
import org.academy.api.common.vanilla.OpenScreenPacket;
import org.academy.api.common.wireless.*;
import org.academy.internal.common.ability.accelerator.skills.lv1.KineticEnergyApplied;
import org.academy.internal.common.ability.accelerator.skills.lv2.DirStrike;
import org.academy.internal.common.ability.accelerator.skills.lv2.VectorAccel;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.ability.accelerator.skills.lv5.BloodflowReverse;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlasmaGeneration;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.ability.electromaster.skills.lv5.BallLightning;
import org.academy.internal.common.ability.electromaster.skills.lv5.Railgun;
import org.academy.internal.common.ability.meltdowner.skills.HellFlare;
import org.academy.internal.common.ability.meltdowner.skills.SingleHighSpeedElectronBeam;
import org.academy.internal.common.ability.accelerator.skills.lv2.KineticSuperposition;
import org.academy.internal.common.ability.electromaster.skills.lv1.PulseCharge;
import org.academy.internal.common.ability.meltdowner.skills.lv2.SpreadingBlast;
import org.academy.internal.common.ability.accelerator.skills.lv1.FlowControl;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorReduction;
import org.academy.internal.common.ability.electromaster.skills.lv1.ElectricalContact;
import org.academy.internal.common.ability.electromaster.skills.lv2.MagnetManipulation;
import org.academy.internal.common.ability.electromaster.skills.lv2.BioelectricSurge;
import org.academy.internal.common.ability.meltdowner.skills.lv1.TraceRing;
import org.academy.internal.common.ability.accelerator.skills.lv2.DirectedShock;
import org.academy.internal.common.ability.accelerator.skills.lv3.HyperAccelerate;
import org.academy.internal.common.ability.electromaster.skills.lv2.MagnetMomentCharge;
import org.academy.internal.common.ability.electromaster.skills.lv3.ThunderLance;
import org.academy.internal.common.ability.meltdowner.skills.lv2.ElectronBarrier;
import org.academy.internal.common.ability.meltdowner.skills.lv2.MiningBeam;
import org.academy.internal.common.ability.teleport.skills.lv1.ClipThrough;
import org.academy.internal.common.ability.teleport.skills.lv2.VisualTeleport;
import org.academy.internal.common.ability.electromaster.skills.lv2.LightningNova;
import org.academy.internal.common.ability.electromaster.skills.lv4.LightningStorm;
import org.academy.internal.common.ability.meltdowner.skills.lv3.Cloudroom;
import org.academy.internal.common.ability.meltdowner.skills.lv3.BetaParticleStream;
import org.academy.internal.common.ability.meltdowner.skills.lv3.HomingBlast;
import org.academy.internal.common.ability.meltdowner.skills.lv4.JetStrike;
import org.academy.internal.common.ability.teleport.skills.lv2.Disarm;
import org.academy.internal.common.ability.teleport.skills.lv3.Shackle;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon;
import org.academy.internal.common.ability.electromaster.skills.lv4.IronSandArsenal;
import org.academy.internal.common.ability.electromaster.skills.lv5.Thunderclap;
import org.academy.internal.common.ability.meltdowner.skills.lv5.ChainFusion;
import org.academy.internal.common.ability.meltdowner.skills.lv5.Disintegrate;
import org.academy.internal.common.ability.teleport.skills.lv3.CoordinateTeleport;
import org.academy.internal.common.ability.teleport.skills.lv4.FlashBack;
import org.academy.internal.common.ability.teleport.skills.lv5.SpacialReplace;
import org.academy.internal.common.ability.teleport.skills.lv5.SpacialExcision;
import org.academy.internal.common.ability.teleport.skills.lv4.PhantomFalling;
import org.academy.internal.common.ability.teleport.skills.lv3.CutThrough;
import org.academy.internal.common.ability.teleport.skills.lv2.SpatialSynergy;
import org.academy.internal.common.ability.teleport.skills.lv1.MatterWarp;
import org.academy.internal.common.ability.teleport.skills.SelfTeleport;
import org.academy.internal.common.world.item.CoinItem;
import org.misaka.api.common.network.packet.PacketType;
import org.misaka.api.common.registries.MisakaNetworkRegistries;

public final class PacketTypes {
    public static final DeferredRegister<PacketType<?, ?>> PACKET_TYPES =
            DeferredRegister.create(MisakaNetworkRegistries.Keys.PACKET_TYPES, AcademyCraft.MOD_ID);

    /**
     * Sync
     */
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, ?>>
            SYNC_DATA = PACKET_TYPES.register("sync_data",
            () -> new PacketType<>(SyncDataPacket.class, UncheckedUtil.uncheckedCast(SyncDataPacket.CODEC)));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, SyncAbilityCategoryPacket>>
            SYNC_ABILITY_CATEGORY = PACKET_TYPES.register("sync_ability_category",
            () -> new PacketType<>(SyncAbilityCategoryPacket.class, SyncAbilityCategoryPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, SyncCPDataPacket>>
            SYNC_CP_DATA = PACKET_TYPES.register("sync_cp_data",
            () -> new PacketType<>(SyncCPDataPacket.class, SyncCPDataPacket.CODEC));
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

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, BallLightning.ActivatePacket>>
            LIGHTNING_NOVA_ACTIVATE = PACKET_TYPES.register("ball_lightning_activate",
            () -> new PacketType<>(BallLightning.ActivatePacket.class, BallLightning.ActivatePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Railgun.StartPacket>>
            RAILGUN_START_CHARGE = PACKET_TYPES.register("railgun_start_charge",
            () -> new PacketType<>(Railgun.StartPacket.class, Railgun.StartPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SingleHighSpeedElectronBeam.ShootPacket>>
            SINGLE_HIGH_SPEED_ELECTRON_BEAM_SHOOT = PACKET_TYPES.register("single_high_speed_electron_beam_shoot",
            () -> new PacketType<>(SingleHighSpeedElectronBeam.ShootPacket.class, SingleHighSpeedElectronBeam.ShootPacket.CODEC));

        public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, HellFlare.TogglePacket>>
            HELL_FLARE_ACTION = PACKET_TYPES.register("hell_flare_action",
            () -> new PacketType<>(HellFlare.TogglePacket.class, HellFlare.TogglePacket.CODEC));

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

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AcquireCategoryPacket>>
            ACQUIRE_CATEGORY = PACKET_TYPES.register("acquire_category",
            () -> new PacketType<>(AcquireCategoryPacket.class, AcquireCategoryPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, AcquireCategoryPacket.Response>>
            ACQUIRE_CATEGORY_RESPONSE = PACKET_TYPES.register("acquire_category_response",
            () -> new PacketType<>(AcquireCategoryPacket.Response.class, AcquireCategoryPacket.Response.CODEC));

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

    private PacketTypes() {
    }

    /**
     * Phase 1 - New Skills
     */
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, KineticSuperposition.TogglePacket>>
            KINETIC_SUPERPOSITION_TOGGLE = PACKET_TYPES.register("kinetic_superposition_toggle",
            () -> new PacketType<>(KineticSuperposition.TogglePacket.class, KineticSuperposition.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PulseCharge.UsePacket>>
            PULSE_CHARGE_USE = PACKET_TYPES.register("pulse_charge_use",
            () -> new PacketType<>(PulseCharge.UsePacket.class, PulseCharge.UsePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SpreadingBlast.ShootPacket>>
            SPREADING_BLAST_SHOOT = PACKET_TYPES.register("spreading_blast_shoot",
            () -> new PacketType<>(SpreadingBlast.ShootPacket.class, SpreadingBlast.ShootPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MatterWarp.UsePacket>>
            MATTER_WARP_USE = PACKET_TYPES.register("matter_warp_use",
            () -> new PacketType<>(MatterWarp.UsePacket.class, MatterWarp.UsePacket.CODEC));

    /**
     * Phase 2 - Aura and Toggle Skills
     */
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, FlowControl.ActionPacket>>
            FLOW_CONTROL_ACTION = PACKET_TYPES.register("flow_control_action",
            () -> new PacketType<>(FlowControl.ActionPacket.class, FlowControl.ActionPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ElectricalContact.TogglePacket>>
            ELECTRICAL_CONTACT_TOGGLE = PACKET_TYPES.register("electrical_contact_toggle",
            () -> new PacketType<>(ElectricalContact.TogglePacket.class, ElectricalContact.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, TraceRing.TogglePacket>>
            TRACE_RING_TOGGLE = PACKET_TYPES.register("trace_ring_toggle",
            () -> new PacketType<>(TraceRing.TogglePacket.class, TraceRing.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VectorReduction.TogglePacket>>
            VECTOR_REDUCTION_TOGGLE = PACKET_TYPES.register("vector_reduction_toggle",
            () -> new PacketType<>(VectorReduction.TogglePacket.class, VectorReduction.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, BioelectricSurge.TogglePacket>>
            BIOELECTRIC_SURGE_TOGGLE = PACKET_TYPES.register("bioelectric_surge_toggle",
            () -> new PacketType<>(BioelectricSurge.TogglePacket.class, BioelectricSurge.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MagnetManipulation.PullPacket>>
            MAGNET_MANIPULATION_PULL = PACKET_TYPES.register("magnet_manipulation_pull",
            () -> new PacketType<>(MagnetManipulation.PullPacket.class, MagnetManipulation.PullPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SpatialSynergy.TogglePacket>>
            SPATIAL_SYNERGY_TOGGLE = PACKET_TYPES.register("spatial_synergy_toggle",
            () -> new PacketType<>(SpatialSynergy.TogglePacket.class, SpatialSynergy.TogglePacket.CODEC));

    /**
     * Phase 3 - Charged and Context Skills
     */
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DirectedShock.ActionPacket>>
            DIRECTED_SHOCK_ACTION = PACKET_TYPES.register("directed_shock_action",
            () -> new PacketType<>(DirectedShock.ActionPacket.class, DirectedShock.ActionPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, HyperAccelerate.LaunchPacket>>
            HYPER_ACCELERATE_LAUNCH = PACKET_TYPES.register("hyper_accelerate_launch",
            () -> new PacketType<>(HyperAccelerate.LaunchPacket.class, HyperAccelerate.LaunchPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PlasmaGeneration.FirePacket>>
            PLASMA_GENERATION_FIRE = PACKET_TYPES.register("plasma_generation_fire",
            () -> new PacketType<>(PlasmaGeneration.FirePacket.class, PlasmaGeneration.FirePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MagnetMomentCharge.ActivatePacket>>
            MAGNET_MOMENT_CHARGE_ACTIVATE = PACKET_TYPES.register("magnet_moment_charge_activate",
            () -> new PacketType<>(MagnetMomentCharge.ActivatePacket.class, MagnetMomentCharge.ActivatePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ThunderLance.StartPacket>>
            THUNDER_LANCE_START = PACKET_TYPES.register("thunder_lance_start",
            () -> new PacketType<>(ThunderLance.StartPacket.class, ThunderLance.StartPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ElectronBarrier.TogglePacket>>
            ELECTRON_BARRIER_TOGGLE = PACKET_TYPES.register("electron_barrier_toggle",
            () -> new PacketType<>(ElectronBarrier.TogglePacket.class, ElectronBarrier.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MiningBeam.TogglePacket>>
            MINING_BEAM_TOGGLE = PACKET_TYPES.register("mining_beam_toggle",
            () -> new PacketType<>(MiningBeam.TogglePacket.class, MiningBeam.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ClipThrough.TeleportPacket>>
            CLIP_THROUGH_TELEPORT = PACKET_TYPES.register("clip_through_teleport",
            () -> new PacketType<>(ClipThrough.TeleportPacket.class, ClipThrough.TeleportPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VisualTeleport.TeleportPacket>>
            VISUAL_TELEPORT = PACKET_TYPES.register("visual_teleport",
            () -> new PacketType<>(VisualTeleport.TeleportPacket.class, VisualTeleport.TeleportPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, CutThrough.TeleportPacket>>
            CUT_THROUGH_TELEPORT = PACKET_TYPES.register("cut_through_teleport",
            () -> new PacketType<>(CutThrough.TeleportPacket.class, CutThrough.TeleportPacket.CODEC));

    /**
     * Phase 4 - Complex Skills
     */
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, LightningNova.ActivatePacket>>
            LIGHTNING_NOVA_ACTIVATE_P4 = PACKET_TYPES.register("lightning_nova_activate_p4",
            () -> new PacketType<>(LightningNova.ActivatePacket.class, LightningNova.ActivatePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Cloudroom.TogglePacket>>
            CLOUDROOM_TOGGLE = PACKET_TYPES.register("cloudroom_toggle",
            () -> new PacketType<>(Cloudroom.TogglePacket.class, Cloudroom.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, BetaParticleStream.MulticastPacket>>
            BETA_PARTICLE_STREAM_MULTICAST = PACKET_TYPES.register("beta_particle_stream_multicast",
            () -> new PacketType<>(BetaParticleStream.MulticastPacket.class, BetaParticleStream.MulticastPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, HomingBlast.ActivatePacket>>
            HOMING_BLAST_ACTIVATE = PACKET_TYPES.register("homing_blast_activate",
            () -> new PacketType<>(HomingBlast.ActivatePacket.class, HomingBlast.ActivatePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, JetStrike.DashPacket>>
            JET_STRIKE_DASH = PACKET_TYPES.register("jet_strike_dash",
            () -> new PacketType<>(JetStrike.DashPacket.class, JetStrike.DashPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, LightningStorm.ActivatePacket>>
            LIGHTNING_STORM_ACTIVATE = PACKET_TYPES.register("lightning_storm_activate",
            () -> new PacketType<>(LightningStorm.ActivatePacket.class, LightningStorm.ActivatePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Disarm.UsePacket>>
            DISARM_USE = PACKET_TYPES.register("disarm_use",
            () -> new PacketType<>(Disarm.UsePacket.class, Disarm.UsePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Shackle.UsePacket>>
            SHACKLE_USE = PACKET_TYPES.register("shackle_use",
            () -> new PacketType<>(Shackle.UsePacket.class, Shackle.UsePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PhantomFalling.UsePacket>>
            PHANTOM_FALLING_USE = PACKET_TYPES.register("phantom_falling_use",
            () -> new PacketType<>(PhantomFalling.UsePacket.class, PhantomFalling.UsePacket.CODEC));

    /**
     * Phase 5 - Ultimate and Signature Skills
     */
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, IronSandArsenal.TogglePacket>>
            IRON_SAND_ARSENAL_TOGGLE = PACKET_TYPES.register("iron_sand_arsenal_toggle",
            () -> new PacketType<>(IronSandArsenal.TogglePacket.class, IronSandArsenal.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, IronSandArsenal.FormSelectPacket>>
            IRON_SAND_ARSENAL_FORM_SELECT = PACKET_TYPES.register("iron_sand_arsenal_form_select",
            () -> new PacketType<>(IronSandArsenal.FormSelectPacket.class, IronSandArsenal.FormSelectPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MagneticWeapon.TogglePacket>>
            MAGNETIC_WEAPON_TOGGLE = PACKET_TYPES.register("magnetic_weapon_toggle",
            () -> new PacketType<>(MagneticWeapon.TogglePacket.class, MagneticWeapon.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Thunderclap.UsePacket>>
            THUNDERCLAP_USE = PACKET_TYPES.register("thunderclap_use",
            () -> new PacketType<>(Thunderclap.UsePacket.class, Thunderclap.UsePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ChainFusion.ActivatePacket>>
            CHAIN_FUSION_ACTIVATE = PACKET_TYPES.register("chain_fusion_activate",
            () -> new PacketType<>(ChainFusion.ActivatePacket.class, ChainFusion.ActivatePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Disintegrate.UsePacket>>
            DISINTEGRATE_USE = PACKET_TYPES.register("disintegrate_use",
            () -> new PacketType<>(Disintegrate.UsePacket.class, Disintegrate.UsePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, FlashBack.TogglePacket>>
            FLASH_BACK_TOGGLE = PACKET_TYPES.register("flash_back_toggle",
            () -> new PacketType<>(FlashBack.TogglePacket.class, FlashBack.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, CoordinateTeleport.SavePositionPacket>>
            COORDINATE_TELEPORT_SAVE = PACKET_TYPES.register("coordinate_teleport_save",
            () -> new PacketType<>(CoordinateTeleport.SavePositionPacket.class, CoordinateTeleport.SavePositionPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, CoordinateTeleport.RequestTeleportPacket>>
            COORDINATE_TELEPORT_REQUEST = PACKET_TYPES.register("coordinate_teleport_request",
            () -> new PacketType<>(CoordinateTeleport.RequestTeleportPacket.class, CoordinateTeleport.RequestTeleportPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SpacialReplace.SetCornerPacket>>
            SPACIAL_REPLACE_SET_CORNER = PACKET_TYPES.register("spacial_replace_set_corner",
            () -> new PacketType<>(SpacialReplace.SetCornerPacket.class, SpacialReplace.SetCornerPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SpacialReplace.PastePacket>>
            SPACIAL_REPLACE_PASTE = PACKET_TYPES.register("spacial_replace_paste",
            () -> new PacketType<>(SpacialReplace.PastePacket.class, SpacialReplace.PastePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SpacialExcision.ActivatePacket>>
            SPACIAL_EXCISION_ACTIVATE = PACKET_TYPES.register("spacial_excision_activate",
            () -> new PacketType<>(SpacialExcision.ActivatePacket.class, SpacialExcision.ActivatePacket.CODEC));
}