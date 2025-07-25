package org.academy.internal.common.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import org.academy.api.common.ability.ExpSyncPacket;
import org.academy.api.common.ability.PlayerSyncPacket;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.packet.FutureRequestPacket;
import org.academy.api.common.network.packet.FutureResponsePacket;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.OpenScreenPacket;
import org.academy.api.common.wireless.ConnectNodePacket;
import org.academy.api.common.wireless.DisconnectNodePacket;
import org.academy.api.common.wireless.SetNodeNamePacket;
import org.academy.api.common.wireless.SetNodePassPacket;
import org.academy.internal.common.ability.builtin.accelerator.skills.*;
import org.academy.internal.common.ability.builtin.electromaster.skills.ArcGenerate;
import org.academy.internal.common.ability.builtin.electromaster.skills.Railgun;
import org.academy.internal.common.ability.builtin.meltdowner.skills.SingleHighSpeedElectronBeam;
import org.academy.internal.common.ability.builtin.teleport.skills.SelfTeleport;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.core.particles.SpawnArcMediumParticlePacket;
import org.academy.internal.common.world.item.CoinItem;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@SuppressWarnings({"unused"})
public final class Packets {
    private static final Map<Class<? extends IPacket<?>>, Function<? extends PacketListener, ? extends IPacket<?>>>
            REGISTERED_FACTORIES = new HashMap<>();

    private Packets() {
    }

    private static <T extends IPacket<PL>, PL extends PacketListener> Class<T> register(
            Class<T> packetClass, Function<PL, T> factory) {
        REGISTERED_FACTORIES.put(packetClass, factory);
        return packetClass;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final Class<AttachmentTypes.AttachmentDataSyncPacket> ATTACHMENT_DATA_SYNC =
            register(AttachmentTypes.AttachmentDataSyncPacket.class, (Function<ClientPacketListener, AttachmentTypes.AttachmentDataSyncPacket>) AttachmentTypes.AttachmentDataSyncPacket::new);

    public static final Class<PlayerSyncPacket> PLAYER_SYNC =
            register(PlayerSyncPacket.class, PlayerSyncPacket::new);
    public static final Class<ExpSyncPacket> EXP_SYNC =
            register(ExpSyncPacket.class, ExpSyncPacket::new);
    public static final Class<ConnectNodePacket> CONNECT_NODE =
            register(ConnectNodePacket.class, ConnectNodePacket::new);
    public static final Class<DisconnectNodePacket> DISCONNECT_NODE =
            register(DisconnectNodePacket.class, DisconnectNodePacket::new);
    public static final Class<SetNodeNamePacket> SET_NODE_NAME =
            register(SetNodeNamePacket.class, SetNodeNamePacket::new);
    public static final Class<SetNodePassPacket> SET_NODE_PASS =
            register(SetNodePassPacket.class, SetNodePassPacket::new);
    public static final Class<OpenScreenPacket> OPEN_SCREEN =
            register(OpenScreenPacket.class, OpenScreenPacket::new);
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final Class<FutureRequestPacket> FUTURE_REQUEST =
            register(FutureRequestPacket.class, FutureRequestPacket::new);
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final Class<FutureResponsePacket> FUTURE_RESPONSE =
            register(FutureResponsePacket.class, FutureResponsePacket::new);
    public static final Class<CoinItem.ThrowCoinPacket> THROW_COIN_WITH_VELOCITY =
            register(CoinItem.ThrowCoinPacket.class, CoinItem.ThrowCoinPacket::new);

    public static final Class<StormWing.TogglePacket> STORM_WING_TOGGLE =
            register(StormWing.TogglePacket.class, StormWing.TogglePacket::new);
    public static final Class<StormWing.ControlPacket> STORM_WING_CONTROL =
            register(StormWing.ControlPacket.class, StormWing.ControlPacket::new);
    public static final Class<VectorAccel.DashPacket> VECTOR_ACCEL_DASH =
            register(VectorAccel.DashPacket.class, VectorAccel.DashPacket::new);
    public static final Class<ArcGenerate.GeneratePacket> ARC_GENERATE_GENERATE =
            register(ArcGenerate.GeneratePacket.class, ArcGenerate.GeneratePacket::new);
    public static final Class<Railgun.ShootPacket> RAILGUN_SHOOT =
            register(Railgun.ShootPacket.class, Railgun.ShootPacket::new);
    public static final Class<Railgun.StartChargePacket> RAILGUN_START_CHARGE =
            register(Railgun.StartChargePacket.class, Railgun.StartChargePacket::new);
    public static final Class<Railgun.ConfirmChargePacket> RAILGUN_CONFIRM_CHARGE =
            register(Railgun.ConfirmChargePacket.class, Railgun.ConfirmChargePacket::new);
    public static final Class<Railgun.ChargeEndPacket> RAILGUN_CHARGE_END =
            register(Railgun.ChargeEndPacket.class, Railgun.ChargeEndPacket::new);
    public static final Class<SingleHighSpeedElectronBeam.ShootPacket> SINGLE_HIGH_SPEED_ELECTRON_BEAM_SHOOT =
            register(SingleHighSpeedElectronBeam.ShootPacket.class, SingleHighSpeedElectronBeam.ShootPacket::new);
    public static final Class<SelfTeleport.SelfTeleportPacket> SELF_TELEPORT =
            register(SelfTeleport.SelfTeleportPacket.class, SelfTeleport.SelfTeleportPacket::new);
    public static final Class<DirStrike.ActionPacket> DIR_STRIKE =
            register(DirStrike.ActionPacket.class, DirStrike.ActionPacket::new);
    public static final Class<KineticEnergyApplied.TogglePacket> KINETIC_ENERGY_APPLIED_TOGGLE =
            register(KineticEnergyApplied.TogglePacket.class, KineticEnergyApplied.TogglePacket::new);
    public static final Class<VectorReflection.TogglePacket> VECTOR_REFLECTION_TOGGLE =
            register(VectorReflection.TogglePacket.class, VectorReflection.TogglePacket::new);
    public static final Class<BloodflowReverse.ReverseBloodflowPacket> REVERSE_BLOODFLOW =
            register(BloodflowReverse.ReverseBloodflowPacket.class, BloodflowReverse.ReverseBloodflowPacket::new);
    public static final Class<SpawnArcMediumParticlePacket> SPAWN_ARC_MEDIUM_PARTICLE =
            register(SpawnArcMediumParticlePacket.class, SpawnArcMediumParticlePacket::new);

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerAll(NetworkSystem networkSystem) {
        for (var entry : REGISTERED_FACTORIES.entrySet()) {
            networkSystem.registerPacketType((Class) entry.getKey(), (Function) entry.getValue());
        }
    }
}