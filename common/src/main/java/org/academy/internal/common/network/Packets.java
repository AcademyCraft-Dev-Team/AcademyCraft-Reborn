package org.academy.internal.common.network;

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
import org.academy.internal.common.ability.builtin.accelerator.skills.DirStrike;
import org.academy.internal.common.ability.builtin.accelerator.skills.KineticEnergyApplied;
import org.academy.internal.common.ability.builtin.accelerator.skills.StormWing;
import org.academy.internal.common.ability.builtin.accelerator.skills.VectorAccel;
import org.academy.internal.common.ability.builtin.electromaster.skills.ArcGenerate;
import org.academy.internal.common.ability.builtin.electromaster.skills.Railgun;
import org.academy.internal.common.ability.builtin.meltdowner.skills.SingleHighSpeedElectronBeam;
import org.academy.internal.common.ability.builtin.teleport.skills.SelfTeleport;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@SuppressWarnings({"unused", "rawtypes"})
public final class Packets {
    private static final Map<Class<? extends IPacket<?>>, Function<? extends PacketListener, ? extends IPacket<?>>>
            REGISTERED_FACTORIES = new HashMap<>();

    private Packets() {
    }

    private static <T extends IPacket<?>, PL extends PacketListener> Class<T> register(
            Class<T> packetClass, Function<PL, T> factory) {
        REGISTERED_FACTORIES.put(packetClass, factory);
        return packetClass;
    }

    public static final Class<PlayerSyncPacket> PLAYER_SYNC_PACKET =
            register(PlayerSyncPacket.class, listener -> new PlayerSyncPacket());
    public static final Class<ExpSyncPacket> EXP_SYNC_PACKET =
            register(ExpSyncPacket.class, listener -> new ExpSyncPacket());
    public static final Class<ConnectNodePacket> CONNECT_NODE_PACKET =
            register(ConnectNodePacket.class, listener -> new ConnectNodePacket());
    public static final Class<DisconnectNodePacket> DISCONNECT_NODE_PACKET =
            register(DisconnectNodePacket.class, listener -> new DisconnectNodePacket());
    public static final Class<SetNodeNamePacket> SET_NODE_NAME_PACKET =
            register(SetNodeNamePacket.class, listener -> new SetNodeNamePacket());
    public static final Class<SetNodePassPacket> SET_NODE_PASS_PACKET =
            register(SetNodePassPacket.class, listener -> new SetNodePassPacket());
    public static final Class<OpenScreenPacket> OPEN_SCREEN_PACKET =
            register(OpenScreenPacket.class, listener -> new OpenScreenPacket());
    public static final Class<FutureRequestPacket> FUTURE_REQUEST_PACKET =
            register(FutureRequestPacket.class, listener -> new FutureRequestPacket<>());
    public static final Class<FutureResponsePacket> FUTURE_RESPONSE_PACKET =
            register(FutureResponsePacket.class, listener -> new FutureResponsePacket<>());

    public static final Class<StormWing.TogglePacket> STORM_WING_TOGGLE_PACKET =
            register(StormWing.TogglePacket.class, listener -> new StormWing.TogglePacket());
    public static final Class<StormWing.ControlPacket> STORM_WING_CONTROL_PACKET =
            register(StormWing.ControlPacket.class, listener -> new StormWing.ControlPacket());
    public static final Class<VectorAccel.DashPacket> VECTOR_ACCEL_DASH_PACKET =
            register(VectorAccel.DashPacket.class, listener -> new VectorAccel.DashPacket());
    public static final Class<ArcGenerate.GeneratePacket> ARC_GENERATE_GENERATE_PACKET =
            register(ArcGenerate.GeneratePacket.class, listener -> new ArcGenerate.GeneratePacket());
    public static final Class<Railgun.ShootPacket> RAILGUN_SHOOT_PACKET =
            register(Railgun.ShootPacket.class, listener -> new Railgun.ShootPacket());
    public static final Class<SingleHighSpeedElectronBeam.ShootPacket> SINGLE_HIGH_SPEED_ELECTRON_BEAM_SHOOT_PACKET =
            register(SingleHighSpeedElectronBeam.ShootPacket.class, listener -> new SingleHighSpeedElectronBeam.ShootPacket());
    public static final Class<SelfTeleport.SelfTeleportPacket> SELF_TELEPORT_PACKET =
            register(SelfTeleport.SelfTeleportPacket.class, listener -> new SelfTeleport.SelfTeleportPacket());
    public static final Class<DirStrike.StartPacket> DIR_STRIKE_START_PACKET =
            register(DirStrike.StartPacket.class, listener -> new DirStrike.StartPacket());
    public static final Class<DirStrike.EndPacket> DIR_STRIKE_END_PACKET =
            register(DirStrike.EndPacket.class, listener -> new DirStrike.EndPacket());
    public static final Class<KineticEnergyApplied.TogglePacket> KINETIC_ENERGY_APPLIED_TOGGLE_PACKET =
            register(KineticEnergyApplied.TogglePacket.class, listener -> new KineticEnergyApplied.TogglePacket());

    @SuppressWarnings({"unchecked"})
    public static void registerAll(NetworkSystem networkSystem) {
        for (Map.Entry<Class<? extends IPacket<?>>, Function<? extends PacketListener, ? extends IPacket<?>>> entry : REGISTERED_FACTORIES.entrySet()) {
            networkSystem.registerPacketType((Class) entry.getKey(), (Function) entry.getValue());
        }
    }
}