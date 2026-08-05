package org.academy.internal.common.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.*;
import org.academy.api.common.ability.pakcet.SyncAbilityCategoryPacket;
import org.academy.api.common.ability.pakcet.SyncAbilityDataPacket;
import org.academy.api.common.ability.pakcet.SyncSkillDataPacket;
import org.academy.api.common.sync.packet.SyncDataPacket;
import org.academy.api.common.util.UncheckedUtil;
import org.academy.api.common.vanilla.OpenScreenPacket;
import org.academy.api.common.wireless.*;
import org.academy.internal.common.ability.accelerator.skills.lv1.KineticEnergyApplied;
import org.academy.internal.common.ability.accelerator.skills.lv1.VectorBlast;
import org.academy.internal.common.ability.accelerator.skills.lv2.DirStrike;
import org.academy.internal.common.ability.accelerator.skills.lv2.VectorAccel;
import org.academy.internal.common.ability.accelerator.skills.lv3.HyperAccelerate;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorReduction;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.ability.accelerator.skills.lv4.ReflectionFilter;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.ability.accelerator.skills.lv5.BloodflowReverse;
import org.academy.internal.common.ability.accelerator.skills.lv5.AdvancedWingSweepPacket;
import org.academy.internal.common.ability.accelerator.skills.lv5.BlackWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.CrossingTheAbyss;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlasmaGeneration;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlatinumWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.WhiteWing;
import org.academy.internal.common.ability.aeromanip.skills.AirflowJet;
import org.academy.internal.common.ability.aeromanip.skills.BreathingFilm;
import org.academy.internal.common.ability.aeromanip.FlowSensePacket;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldSyncPacket;
import org.academy.internal.common.ability.aeromanip.skills.PneumaticGrasp;
import org.academy.internal.common.ability.aeromanip.skills.LaminarCutter;
import org.academy.internal.common.ability.aeromanip.skills.VortexPull;
import org.academy.internal.common.ability.aeromanip.skills.WindCorridor;
import org.academy.internal.common.ability.aeromanip.skills.PressureLock;
import org.academy.internal.common.ability.aeromanip.skills.TailwindField;
import org.academy.internal.common.ability.aeromanip.skills.AtmosphericDominion;
import org.academy.internal.common.ability.aeromanip.skills.AtmosphereShield;
import org.academy.internal.common.ability.aeromanip.skills.AtmosphereBlastGun;
import org.academy.internal.common.ability.aeromanip.skills.Flight;
import org.academy.internal.common.ability.aeromanip.skills.VacuumDomain;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.ability.electromaster.skills.lv1.ElectricalContact;
import org.academy.internal.common.ability.electromaster.skills.lv1.PulseCharge;
import org.academy.internal.common.ability.electromaster.skills.lv2.LightningNova;
import org.academy.internal.common.ability.electromaster.skills.lv2.MagnetManipulation;
import org.academy.internal.common.ability.electromaster.skills.lv2.MineDetect;
import org.academy.internal.common.ability.electromaster.skills.lv3.CurrentSymbiosis;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon;
import org.academy.internal.common.ability.electromaster.skills.lv3.ThunderLance;
import org.academy.internal.common.ability.electromaster.skills.lv4.BioelectricOperation;
import org.academy.internal.common.ability.electromaster.skills.lv4.ElectromagneticShield;
import org.academy.internal.common.ability.electromaster.skills.lv4.IronSandArsenal;
import org.academy.internal.common.ability.electromaster.skills.lv4.LightningStorm;
import org.academy.internal.common.ability.electromaster.skills.lv5.BallLightning;
import org.academy.internal.common.ability.electromaster.skills.lv5.Railgun;
import org.academy.internal.common.ability.electromaster.skills.lv5.Thunderclap;
import org.academy.internal.common.ability.meltdowner.skills.SingleHighSpeedElectronBeam;
import org.academy.internal.common.ability.meltdowner.skills.lv2.MiningBeam;
import org.academy.internal.common.ability.meltdowner.skills.lv2.ScatterBomb;
import org.academy.internal.common.ability.meltdowner.skills.lv3.Cloudroom;
import org.academy.internal.common.ability.meltdowner.skills.lv3.LightShield;
import org.academy.internal.common.ability.meltdowner.skills.lv4.JetStrike;
import org.academy.internal.common.ability.meltdowner.skills.lv4.ParticleWaveCannon;
import org.academy.internal.common.ability.meltdowner.skills.lv5.ChainFusion;
import org.academy.internal.common.ability.meltdowner.skills.lv5.AutoCruiseBeamCannon;
import org.academy.internal.common.ability.meltdowner.skills.lv5.Disintegrate;
import org.academy.internal.common.ability.mentalout.skills.MentalStupor;
import org.academy.internal.common.ability.mentalout.skills.MentalIntervention;
import org.academy.internal.common.ability.mentalout.skills.ImpressionManipulation;
import org.academy.internal.common.ability.mentalout.skills.TargetMisidentification;
import org.academy.internal.common.ability.mentalout.MentaloutRosterPackets;
import org.academy.internal.common.ability.teleport.skills.SelfTeleport;
import org.academy.internal.common.ability.teleport.skills.lv1.ThreateningTeleport;
import org.academy.internal.common.ability.teleport.skills.lv2.Disarm;
import org.academy.internal.common.ability.teleport.skills.lv2.SpatialSynergy;
import org.academy.internal.common.ability.teleport.skills.lv3.CutThrough;
import org.academy.internal.common.ability.teleport.skills.lv3.FleshRipping;
import org.academy.internal.common.ability.teleport.skills.lv3.LocationTeleport;
import org.academy.internal.common.ability.teleport.skills.lv3.Shackle;
import org.academy.internal.common.ability.teleport.skills.lv4.QuickLocationTeleport;
import org.academy.internal.common.ability.teleport.skills.lv4.AreaTeleportSelect;
import org.academy.internal.common.ability.teleport.skills.lv4.AreaTeleportSetup;
import org.academy.internal.common.ability.teleport.skills.lv4.AreaTeleportStart;
import org.academy.internal.common.ability.teleport.skills.lv5.SpacialExcision;
import org.academy.internal.common.ability.teleport.skills.lv5.Flashing;
import org.academy.internal.common.ability.teleport.skills.lv5.DefensiveTeleport;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterShaping;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterDisassemble;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterCut;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterRadiation;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterRepair;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterCreation;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterSixWings;
import org.academy.internal.common.world.item.CoinItem;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
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

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MusicSyncPackets.SharePacket>>
            MUSIC_SHARE = PACKET_TYPES.register("music_share",
            () -> new PacketType<>(MusicSyncPackets.SharePacket.class, MusicSyncPackets.SharePacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, MusicSyncPackets.SyncPacket>>
            MUSIC_SYNC = PACKET_TYPES.register("music_sync",
            () -> new PacketType<>(MusicSyncPackets.SyncPacket.class, MusicSyncPackets.SyncPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, CoinItem.ThrowCoinPacket>>
            THROW_COIN_WITH_VELOCITY = PACKET_TYPES.register("throw_coin_with_velocity",
            () -> new PacketType<>(CoinItem.ThrowCoinPacket.class, CoinItem.ThrowCoinPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AirflowJet.StartPacket>>
            AIRFLOW_JET_START = PACKET_TYPES.register("airflow_jet_start",
            () -> new PacketType<>(AirflowJet.StartPacket.class, AirflowJet.StartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AirflowJet.StopPacket>>
            AIRFLOW_JET_STOP = PACKET_TYPES.register("airflow_jet_stop",
            () -> new PacketType<>(AirflowJet.StopPacket.class, AirflowJet.StopPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, BreathingFilm.CastPacket>>
            BREATHING_FILM_CAST = PACKET_TYPES.register("breathing_film_cast",
            () -> new PacketType<>(BreathingFilm.CastPacket.class, BreathingFilm.CastPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, FlowSensePacket>>
            FLOW_SENSE_SYNC = PACKET_TYPES.register("flow_sense_sync",
            () -> new PacketType<>(FlowSensePacket.class, FlowSensePacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, AeromanipFieldSyncPacket>>
            AEROMANIP_FIELD_SYNC = PACKET_TYPES.register("aeromanip_field_sync",
            () -> new PacketType<>(AeromanipFieldSyncPacket.class, AeromanipFieldSyncPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PneumaticGrasp.StartPacket>>
            PNEUMATIC_GRASP_START = PACKET_TYPES.register("pneumatic_grasp_start",
            () -> new PacketType<>(PneumaticGrasp.StartPacket.class, PneumaticGrasp.StartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PneumaticGrasp.StopPacket>>
            PNEUMATIC_GRASP_STOP = PACKET_TYPES.register("pneumatic_grasp_stop",
            () -> new PacketType<>(PneumaticGrasp.StopPacket.class, PneumaticGrasp.StopPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PneumaticGrasp.AdjustDistancePacket>>
            PNEUMATIC_GRASP_ADJUST_DISTANCE = PACKET_TYPES.register("pneumatic_grasp_adjust_distance",
            () -> new PacketType<>(PneumaticGrasp.AdjustDistancePacket.class, PneumaticGrasp.AdjustDistancePacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, LaminarCutter.CastPacket>>
            LAMINAR_CUTTER_CAST = PACKET_TYPES.register("laminar_cutter_cast",
            () -> new PacketType<>(LaminarCutter.CastPacket.class, LaminarCutter.CastPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VortexPull.CastPacket>>
            VORTEX_PULL_CAST = PACKET_TYPES.register("vortex_pull_cast",
            () -> new PacketType<>(VortexPull.CastPacket.class, VortexPull.CastPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, WindCorridor.CastPacket>>
            WIND_CORRIDOR_CAST = PACKET_TYPES.register("wind_corridor_cast",
            () -> new PacketType<>(WindCorridor.CastPacket.class, WindCorridor.CastPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PressureLock.StartPacket>>
            PRESSURE_LOCK_START = PACKET_TYPES.register("pressure_lock_start",
            () -> new PacketType<>(PressureLock.StartPacket.class, PressureLock.StartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PressureLock.StopPacket>>
            PRESSURE_LOCK_STOP = PACKET_TYPES.register("pressure_lock_stop",
            () -> new PacketType<>(PressureLock.StopPacket.class, PressureLock.StopPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, TailwindField.TogglePacket>>
            TAILWIND_FIELD_TOGGLE = PACKET_TYPES.register("tailwind_field_toggle",
            () -> new PacketType<>(TailwindField.TogglePacket.class, TailwindField.TogglePacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AtmosphericDominion.CastPacket>>
            ATMOSPHERIC_DOMINION_CAST = PACKET_TYPES.register("atmospheric_dominion_cast",
            () -> new PacketType<>(AtmosphericDominion.CastPacket.class, AtmosphericDominion.CastPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AtmosphereShield.TogglePacket>>
            ATMOSPHERE_SHIELD_TOGGLE = PACKET_TYPES.register("atmosphere_shield_toggle",
            () -> new PacketType<>(AtmosphereShield.TogglePacket.class, AtmosphereShield.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AtmosphereBlastGun.CastPacket>>
            ATMOSPHERE_BLAST_GUN_CAST = PACKET_TYPES.register("atmosphere_blast_gun_cast",
            () -> new PacketType<>(AtmosphereBlastGun.CastPacket.class, AtmosphereBlastGun.CastPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AtmosphereBlastGun.StartPacket>>
            ATMOSPHERE_BLAST_GUN_START = PACKET_TYPES.register("atmosphere_blast_gun_start",
            () -> new PacketType<>(AtmosphereBlastGun.StartPacket.class, AtmosphereBlastGun.StartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AtmosphereBlastGun.StopPacket>>
            ATMOSPHERE_BLAST_GUN_STOP = PACKET_TYPES.register("atmosphere_blast_gun_stop",
            () -> new PacketType<>(AtmosphereBlastGun.StopPacket.class, AtmosphereBlastGun.StopPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Flight.TogglePacket>>
            FLIGHT_TOGGLE = PACKET_TYPES.register("flight_toggle",
            () -> new PacketType<>(Flight.TogglePacket.class, Flight.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VacuumDomain.CastPacket>>
            VACUUM_DOMAIN_CAST = PACKET_TYPES.register("vacuum_domain_cast",
            () -> new PacketType<>(VacuumDomain.CastPacket.class, VacuumDomain.CastPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, StormWing.TogglePacket>>
            STORM_WING_TOGGLE = PACKET_TYPES.register("storm_wing_toggle",
            () -> new PacketType<>(StormWing.TogglePacket.class, StormWing.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, StormWing.ControlPacket>>
            STORM_WING_CONTROL = PACKET_TYPES.register("storm_wing_control",
            () -> new PacketType<>(StormWing.ControlPacket.class, StormWing.ControlPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VectorAccel.StartPacket>>
            VECTOR_ACCEL_START = PACKET_TYPES.register("vector_accel_start",
            () -> new PacketType<>(VectorAccel.StartPacket.class, VectorAccel.StartPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VectorAccel.DashPacket>>
            VECTOR_ACCEL_DASH = PACKET_TYPES.register("vector_accel_dash",
            () -> new PacketType<>(VectorAccel.DashPacket.class, VectorAccel.DashPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ArcGenerate.GeneratePacket>>
            ARC_GENERATE_GENERATE = PACKET_TYPES.register("arc_generate_generate",
            () -> new PacketType<>(ArcGenerate.GeneratePacket.class, ArcGenerate.GeneratePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MagnetManipulation.MoveStartPacket>>
            MAGNET_MANIPULATION_MOVE_START = PACKET_TYPES.register("magnet_manipulation_move_start",
            () -> new PacketType<>(MagnetManipulation.MoveStartPacket.class, MagnetManipulation.MoveStartPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MagnetManipulation.MoveStopPacket>>
            MAGNET_MANIPULATION_MOVE_STOP = PACKET_TYPES.register("magnet_manipulation_move_stop",
            () -> new PacketType<>(MagnetManipulation.MoveStopPacket.class, MagnetManipulation.MoveStopPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MineDetect.TogglePacket>>
            MINE_DETECT_TOGGLE = PACKET_TYPES.register("mine_detect_toggle",
            () -> new PacketType<>(MineDetect.TogglePacket.class, MineDetect.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ElectromagneticShield.TogglePacket>>
            ELECTROMAGNETIC_SHIELD_TOGGLE = PACKET_TYPES.register("electromagnetic_shield_toggle",
            () -> new PacketType<>(ElectromagneticShield.TogglePacket.class, ElectromagneticShield.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, CurrentSymbiosis.TogglePacket>>
            CURRENT_SYMBIOSIS_TOGGLE = PACKET_TYPES.register("current_symbiosis_toggle",
            () -> new PacketType<>(CurrentSymbiosis.TogglePacket.class, CurrentSymbiosis.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, BioelectricOperation.TogglePacket>>
            BIOELECTRIC_OPERATION_TOGGLE = PACKET_TYPES.register("bioelectric_operation_toggle",
            () -> new PacketType<>(BioelectricOperation.TogglePacket.class, BioelectricOperation.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, BallLightning.ActivatePacket>>
            LIGHTNING_NOVA_ACTIVATE = PACKET_TYPES.register("ball_lightning_activate",
            () -> new PacketType<>(BallLightning.ActivatePacket.class, BallLightning.ActivatePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Railgun.StartPacket>>
            RAILGUN_START_CHARGE = PACKET_TYPES.register("railgun_start_charge",
            () -> new PacketType<>(Railgun.StartPacket.class, Railgun.StartPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SingleHighSpeedElectronBeam.ShootPacket>>
            SINGLE_HIGH_SPEED_ELECTRON_BEAM_SHOOT = PACKET_TYPES.register("single_high_speed_electron_beam_shoot",
            () -> new PacketType<>(SingleHighSpeedElectronBeam.ShootPacket.class, SingleHighSpeedElectronBeam.ShootPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ScatterBomb.ShootPacket>>
            SCATTER_BOMB_SHOOT = PACKET_TYPES.register("scatter_bomb_shoot",
            () -> new PacketType<>(ScatterBomb.ShootPacket.class, ScatterBomb.ShootPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SelfTeleport.SelfTeleportPacket>>
            SELF_TELEPORT = PACKET_TYPES.register("self_teleport",
            () -> new PacketType<>(SelfTeleport.SelfTeleportPacket.class, SelfTeleport.SelfTeleportPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ThreateningTeleport.CastPacket>>
            THREATENING_TELEPORT_CAST = PACKET_TYPES.register("threatening_teleport_cast",
            () -> new PacketType<>(ThreateningTeleport.CastPacket.class, ThreateningTeleport.CastPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, FleshRipping.CastPacket>>
            FLESH_RIPPING_CAST = PACKET_TYPES.register("flesh_ripping_cast",
            () -> new PacketType<>(FleshRipping.CastPacket.class, FleshRipping.CastPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, LocationTeleport.RequestMarksPacket>>
            LOCATION_TELEPORT_REQUEST = PACKET_TYPES.register("location_teleport_request",
            () -> new PacketType<>(LocationTeleport.RequestMarksPacket.class, LocationTeleport.RequestMarksPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, LocationTeleport.SaveMarkPacket>>
            LOCATION_TELEPORT_SAVE = PACKET_TYPES.register("location_teleport_save",
            () -> new PacketType<>(LocationTeleport.SaveMarkPacket.class, LocationTeleport.SaveMarkPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, LocationTeleport.RemoveMarkPacket>>
            LOCATION_TELEPORT_REMOVE = PACKET_TYPES.register("location_teleport_remove",
            () -> new PacketType<>(LocationTeleport.RemoveMarkPacket.class, LocationTeleport.RemoveMarkPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, LocationTeleport.SelectMarkPacket>>
            LOCATION_TELEPORT_SELECT = PACKET_TYPES.register("location_teleport_select",
            () -> new PacketType<>(LocationTeleport.SelectMarkPacket.class, LocationTeleport.SelectMarkPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, LocationTeleport.TeleportToMarkPacket>>
            LOCATION_TELEPORT_RUN = PACKET_TYPES.register("location_teleport_run",
            () -> new PacketType<>(LocationTeleport.TeleportToMarkPacket.class, LocationTeleport.TeleportToMarkPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, LocationTeleport.MarksSyncPacket>>
            LOCATION_TELEPORT_SYNC = PACKET_TYPES.register("location_teleport_sync",
            () -> new PacketType<>(LocationTeleport.MarksSyncPacket.class, LocationTeleport.MarksSyncPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, QuickLocationTeleport.RunPacket>>
            QUICK_LOCATION_TELEPORT_RUN = PACKET_TYPES.register("quick_location_teleport_run",
            () -> new PacketType<>(QuickLocationTeleport.RunPacket.class, QuickLocationTeleport.RunPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AreaTeleportSelect.MarkPacket>>
            AREA_TELEPORT_SELECT_MARK = PACKET_TYPES.register("area_teleport_select_mark",
            () -> new PacketType<>(AreaTeleportSelect.MarkPacket.class, AreaTeleportSelect.MarkPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, AreaTeleportSelect.SyncPacket>>
            AREA_TELEPORT_SYNC = PACKET_TYPES.register("area_teleport_sync",
            () -> new PacketType<>(AreaTeleportSelect.SyncPacket.class, AreaTeleportSelect.SyncPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AreaTeleportSetup.MarkPacket>>
            AREA_TELEPORT_SETUP_MARK = PACKET_TYPES.register("area_teleport_setup_mark",
            () -> new PacketType<>(AreaTeleportSetup.MarkPacket.class, AreaTeleportSetup.MarkPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AreaTeleportStart.RunPacket>>
            AREA_TELEPORT_START_RUN = PACKET_TYPES.register("area_teleport_start_run",
            () -> new PacketType<>(AreaTeleportStart.RunPacket.class, AreaTeleportStart.RunPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Flashing.TogglePacket>>
            FLASHING_TOGGLE = PACKET_TYPES.register("flashing_toggle",
            () -> new PacketType<>(Flashing.TogglePacket.class, Flashing.TogglePacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, Flashing.DashPacket>>
            FLASHING_DASH = PACKET_TYPES.register("flashing_dash",
            () -> new PacketType<>(Flashing.DashPacket.class, Flashing.DashPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DefensiveTeleport.TogglePacket>>
            DEFENSIVE_TELEPORT_TOGGLE = PACKET_TYPES.register("defensive_teleport_toggle",
            () -> new PacketType<>(DefensiveTeleport.TogglePacket.class, DefensiveTeleport.TogglePacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DarkmatterShaping.CastPacket>>
            DARKMATTER_SHAPING_CAST = PACKET_TYPES.register("darkmatter_shaping_cast",
            () -> new PacketType<>(DarkmatterShaping.CastPacket.class, DarkmatterShaping.CastPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DarkmatterDisassemble.CastPacket>>
            DARKMATTER_DISASSEMBLE_CAST = PACKET_TYPES.register("darkmatter_disassemble_cast",
            () -> new PacketType<>(DarkmatterDisassemble.CastPacket.class, DarkmatterDisassemble.CastPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DarkmatterCut.CastPacket>>
            DARKMATTER_CUT_CAST = PACKET_TYPES.register("darkmatter_cut_cast",
            () -> new PacketType<>(DarkmatterCut.CastPacket.class, DarkmatterCut.CastPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DarkmatterRadiation.StartPacket>>
            DARKMATTER_RADIATION_START = PACKET_TYPES.register("darkmatter_radiation_start",
            () -> new PacketType<>(DarkmatterRadiation.StartPacket.class, DarkmatterRadiation.StartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DarkmatterRadiation.StopPacket>>
            DARKMATTER_RADIATION_STOP = PACKET_TYPES.register("darkmatter_radiation_stop",
            () -> new PacketType<>(DarkmatterRadiation.StopPacket.class, DarkmatterRadiation.StopPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DarkmatterRepair.TogglePacket>>
            DARKMATTER_REPAIR_TOGGLE = PACKET_TYPES.register("darkmatter_repair_toggle",
            () -> new PacketType<>(DarkmatterRepair.TogglePacket.class, DarkmatterRepair.TogglePacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DarkmatterCreation.CastPacket>>
            DARKMATTER_CREATION_CAST = PACKET_TYPES.register("darkmatter_creation_cast",
            () -> new PacketType<>(DarkmatterCreation.CastPacket.class, DarkmatterCreation.CastPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DarkmatterSixWings.TogglePacket>>
            DARKMATTER_SIX_WINGS_TOGGLE = PACKET_TYPES.register("darkmatter_six_wings_toggle",
            () -> new PacketType<>(DarkmatterSixWings.TogglePacket.class, DarkmatterSixWings.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DirStrike.ActionPacket>>
            DIR_STRIKE = PACKET_TYPES.register("dir_strike",
            () -> new PacketType<>(DirStrike.ActionPacket.class, DirStrike.ActionPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VectorBlast.UsePacket>>
            VECTOR_BLAST_USE = PACKET_TYPES.register("vector_blast_use",
            () -> new PacketType<>(VectorBlast.UsePacket.class, VectorBlast.UsePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, KineticEnergyApplied.TogglePacket>>
            KINETIC_ENERGY_APPLIED_TOGGLE = PACKET_TYPES.register("kinetic_energy_applied_toggle",
            () -> new PacketType<>(KineticEnergyApplied.TogglePacket.class, KineticEnergyApplied.TogglePacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, KineticEnergyApplied.ToggleShockwavePacket>>
            KINETIC_ENERGY_APPLIED_SHOCKWAVE_TOGGLE = PACKET_TYPES.register("kinetic_energy_applied_shockwave_toggle",
            () -> new PacketType<>(KineticEnergyApplied.ToggleShockwavePacket.class, KineticEnergyApplied.ToggleShockwavePacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, KineticEnergyApplied.CycleImpactLevelPacket>>
            KINETIC_ENERGY_APPLIED_IMPACT_LEVEL = PACKET_TYPES.register("kinetic_energy_applied_impact_level",
            () -> new PacketType<>(KineticEnergyApplied.CycleImpactLevelPacket.class, KineticEnergyApplied.CycleImpactLevelPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, KineticEnergyApplied.AttackWavePacket>>
            KINETIC_ENERGY_APPLIED_ATTACK_WAVE = PACKET_TYPES.register("kinetic_energy_applied_attack_wave",
            () -> new PacketType<>(KineticEnergyApplied.AttackWavePacket.class, KineticEnergyApplied.AttackWavePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VectorReflection.TogglePacket>>
            VECTOR_REFLECTION_TOGGLE = PACKET_TYPES.register("vector_reflection_toggle",
            () -> new PacketType<>(VectorReflection.TogglePacket.class, VectorReflection.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ReflectionFilter.RequestPacket>>
            REFLECTION_FILTER_REQUEST = PACKET_TYPES.register("reflection_filter_request",
            () -> new PacketType<>(ReflectionFilter.RequestPacket.class, ReflectionFilter.RequestPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ReflectionFilter.UpdatePacket>>
            REFLECTION_FILTER_UPDATE = PACKET_TYPES.register("reflection_filter_update",
            () -> new PacketType<>(ReflectionFilter.UpdatePacket.class, ReflectionFilter.UpdatePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, ReflectionFilter.SyncPacket>>
            REFLECTION_FILTER_SYNC = PACKET_TYPES.register("reflection_filter_sync",
            () -> new PacketType<>(ReflectionFilter.SyncPacket.class, ReflectionFilter.SyncPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, BlackWing.TogglePacket>>
            BLACK_WING_TOGGLE = PACKET_TYPES.register("black_wing_toggle",
            () -> new PacketType<>(BlackWing.TogglePacket.class, BlackWing.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, BlackWing.ControlPacket>>
            BLACK_WING_CONTROL = PACKET_TYPES.register("black_wing_control",
            () -> new PacketType<>(BlackWing.ControlPacket.class, BlackWing.ControlPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, WhiteWing.TogglePacket>>
            WHITE_WING_TOGGLE = PACKET_TYPES.register("white_wing_toggle",
            () -> new PacketType<>(WhiteWing.TogglePacket.class, WhiteWing.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, WhiteWing.ControlPacket>>
            WHITE_WING_CONTROL = PACKET_TYPES.register("white_wing_control",
            () -> new PacketType<>(WhiteWing.ControlPacket.class, WhiteWing.ControlPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PlatinumWing.TogglePacket>>
            PLATINUM_WING_TOGGLE = PACKET_TYPES.register("platinum_wing_toggle",
            () -> new PacketType<>(PlatinumWing.TogglePacket.class, PlatinumWing.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PlatinumWing.ControlPacket>>
            PLATINUM_WING_CONTROL = PACKET_TYPES.register("platinum_wing_control",
            () -> new PacketType<>(PlatinumWing.ControlPacket.class, PlatinumWing.ControlPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, AdvancedWingSweepPacket>>
            ADVANCED_WING_SWEEP = PACKET_TYPES.register("advanced_wing_sweep",
            () -> new PacketType<>(AdvancedWingSweepPacket.class, AdvancedWingSweepPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, PlatinumWing.ExecutionVisualPacket>>
            PLATINUM_WING_EXECUTION_VISUAL = PACKET_TYPES.register("platinum_wing_execution_visual",
            () -> new PacketType<>(PlatinumWing.ExecutionVisualPacket.class, PlatinumWing.ExecutionVisualPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, CrossingTheAbyss.TogglePacket>>
            CROSSING_THE_ABYSS_TOGGLE = PACKET_TYPES.register("crossing_the_abyss_toggle",
            () -> new PacketType<>(CrossingTheAbyss.TogglePacket.class, CrossingTheAbyss.TogglePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, FriendlyFireSetting.SetPacket>>
            FRIENDLY_FIRE_SET = PACKET_TYPES.register("friendly_fire_set",
            () -> new PacketType<>(FriendlyFireSetting.SetPacket.class, FriendlyFireSetting.SetPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DestroyBlocksSetting.SetPacket>>
            DESTROY_BLOCKS_SET = PACKET_TYPES.register("destroy_blocks_set",
            () -> new PacketType<>(DestroyBlocksSetting.SetPacket.class, DestroyBlocksSetting.SetPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, DestroyBlocksSetting.SetSkillPacket>>
            DESTROY_BLOCKS_SKILL_SET = PACKET_TYPES.register("destroy_blocks_skill_set",
            () -> new PacketType<>(DestroyBlocksSetting.SetSkillPacket.class, DestroyBlocksSetting.SetSkillPacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, BloodflowReverse.ReverseBloodflowPacket>>
            REVERSE_BLOODFLOW = PACKET_TYPES.register("reverse_bloodflow",
            () -> new PacketType<>(BloodflowReverse.ReverseBloodflowPacket.class, BloodflowReverse.ReverseBloodflowPacket.CODEC));

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
    /**
     * Phase 1 - New Skills
     */
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PulseCharge.StartPacket>>
            PULSE_CHARGE_START = PACKET_TYPES.register("pulse_charge_start",
            () -> new PacketType<>(PulseCharge.StartPacket.class, PulseCharge.StartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PulseCharge.StopPacket>>
            PULSE_CHARGE_STOP = PACKET_TYPES.register("pulse_charge_stop",
            () -> new PacketType<>(PulseCharge.StopPacket.class, PulseCharge.StopPacket.CODEC));
    /**
     * Phase 2 - Aura and Toggle Skills
     */
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ElectricalContact.TogglePacket>>
            ELECTRICAL_CONTACT_TOGGLE = PACKET_TYPES.register("electrical_contact_toggle",
            () -> new PacketType<>(ElectricalContact.TogglePacket.class, ElectricalContact.TogglePacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, VectorReduction.TogglePacket>>
            VECTOR_REDUCTION_TOGGLE = PACKET_TYPES.register("vector_reduction_toggle",
            () -> new PacketType<>(VectorReduction.TogglePacket.class, VectorReduction.TogglePacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SpatialSynergy.TogglePacket>>
            SPATIAL_SYNERGY_TOGGLE = PACKET_TYPES.register("spatial_synergy_toggle",
            () -> new PacketType<>(SpatialSynergy.TogglePacket.class, SpatialSynergy.TogglePacket.CODEC));
    /**
     * Phase 3 - Charged and Context Skills
     */
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, HyperAccelerate.StartPacket>>
            HYPER_ACCELERATE_START = PACKET_TYPES.register("hyper_accelerate_start",
            () -> new PacketType<>(HyperAccelerate.StartPacket.class, HyperAccelerate.StartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, HyperAccelerate.LaunchPacket>>
            HYPER_ACCELERATE_LAUNCH = PACKET_TYPES.register("hyper_accelerate_launch",
            () -> new PacketType<>(HyperAccelerate.LaunchPacket.class, HyperAccelerate.LaunchPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PlasmaGeneration.StartPacket>>
            PLASMA_GENERATION_START = PACKET_TYPES.register("plasma_generation_start",
            () -> new PacketType<>(PlasmaGeneration.StartPacket.class, PlasmaGeneration.StartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, PlasmaGeneration.ReleasePacket>>
            PLASMA_GENERATION_RELEASE = PACKET_TYPES.register("plasma_generation_release",
            () -> new PacketType<>(PlasmaGeneration.ReleasePacket.class, PlasmaGeneration.ReleasePacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ThunderLance.StartPacket>>
            THUNDER_LANCE_START = PACKET_TYPES.register("thunder_lance_start",
            () -> new PacketType<>(ThunderLance.StartPacket.class, ThunderLance.StartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ThunderLance.QuickPacket>>
            THUNDER_LANCE_QUICK = PACKET_TYPES.register("thunder_lance_quick",
            () -> new PacketType<>(ThunderLance.QuickPacket.class, ThunderLance.QuickPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MiningBeam.StartPacket>>
            MINING_BEAM_START = PACKET_TYPES.register("mining_beam_start",
            () -> new PacketType<>(MiningBeam.StartPacket.class, MiningBeam.StartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MiningBeam.StopPacket>>
            MINING_BEAM_STOP = PACKET_TYPES.register("mining_beam_stop",
            () -> new PacketType<>(MiningBeam.StopPacket.class, MiningBeam.StopPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, LightShield.StartPacket>>
            LIGHT_SHIELD_START = PACKET_TYPES.register("light_shield_start",
            () -> new PacketType<>(LightShield.StartPacket.class, LightShield.StartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, LightShield.StopPacket>>
            LIGHT_SHIELD_STOP = PACKET_TYPES.register("light_shield_stop",
            () -> new PacketType<>(LightShield.StopPacket.class, LightShield.StopPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ParticleWaveCannon.StartPacket>>
            PARTICLE_WAVE_CANNON_START = PACKET_TYPES.register("particle_wave_cannon_start",
            () -> new PacketType<>(ParticleWaveCannon.StartPacket.class, ParticleWaveCannon.StartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ParticleWaveCannon.StopPacket>>
            PARTICLE_WAVE_CANNON_STOP = PACKET_TYPES.register("particle_wave_cannon_stop",
            () -> new PacketType<>(ParticleWaveCannon.StopPacket.class, ParticleWaveCannon.StopPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AutoCruiseBeamCannon.TogglePacket>>
            AUTO_CRUISE_BEAM_CANNON_TOGGLE = PACKET_TYPES.register("auto_cruise_beam_cannon_toggle",
            () -> new PacketType<>(AutoCruiseBeamCannon.TogglePacket.class, AutoCruiseBeamCannon.TogglePacket.CODEC));
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
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, SpacialExcision.ActivatePacket>>
            SPACIAL_EXCISION_ACTIVATE = PACKET_TYPES.register("spacial_excision_activate",
            () -> new PacketType<>(SpacialExcision.ActivatePacket.class, SpacialExcision.ActivatePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, TargetMisidentification.UsePacket>>
            TARGET_MISIDENTIFICATION_USE = PACKET_TYPES.register("target_misidentification_use",
            () -> new PacketType<>(TargetMisidentification.UsePacket.class, TargetMisidentification.UsePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MentalIntervention.UsePacket>>
            MENTAL_INTERVENTION_USE = PACKET_TYPES.register("mental_intervention_use",
            () -> new PacketType<>(MentalIntervention.UsePacket.class, MentalIntervention.UsePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MentalStupor.UsePacket>>
            MENTAL_STUPOR_USE = PACKET_TYPES.register("mental_stupor_use",
            () -> new PacketType<>(MentalStupor.UsePacket.class, MentalStupor.UsePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, ImpressionManipulation.UsePacket>>
            IMPRESSION_MANIPULATION_USE = PACKET_TYPES.register("impression_manipulation_use",
            () -> new PacketType<>(ImpressionManipulation.UsePacket.class, ImpressionManipulation.UsePacket.CODEC));

    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, MentaloutRosterPackets.FullStartPacket>>
            MENTALOUT_ROSTER_FULL_START = PACKET_TYPES.register("mentalout_roster_full_start",
            () -> new PacketType<>(MentaloutRosterPackets.FullStartPacket.class,
                    MentaloutRosterPackets.FullStartPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, MentaloutRosterPackets.FullChunkPacket>>
            MENTALOUT_ROSTER_FULL_CHUNK = PACKET_TYPES.register("mentalout_roster_full_chunk",
            () -> new PacketType<>(MentaloutRosterPackets.FullChunkPacket.class,
                    MentaloutRosterPackets.FullChunkPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, MentaloutRosterPackets.DeltaPacket>>
            MENTALOUT_ROSTER_DELTA = PACKET_TYPES.register("mentalout_roster_delta",
            () -> new PacketType<>(MentaloutRosterPackets.DeltaPacket.class,
                    MentaloutRosterPackets.DeltaPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, MentaloutRosterPackets.ClearPacket>>
            MENTALOUT_ROSTER_CLEAR = PACKET_TYPES.register("mentalout_roster_clear",
            () -> new PacketType<>(MentaloutRosterPackets.ClearPacket.class,
                    MentaloutRosterPackets.ClearPacket.CODEC));
    public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, MentaloutRosterPackets.ResyncPacket>>
            MENTALOUT_ROSTER_RESYNC = PACKET_TYPES.register("mentalout_roster_resync",
            () -> new PacketType<>(MentaloutRosterPackets.ResyncPacket.class,
                    MentaloutRosterPackets.ResyncPacket.CODEC));

    /**
     * Development packets
     */
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
