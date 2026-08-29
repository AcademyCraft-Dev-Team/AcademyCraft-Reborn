package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.ability.accelerator.skills.lv5.BlackWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlatinumWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.WhiteWing;
import org.academy.internal.common.ability.electromaster.skills.lv4.IronSandArsenal;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

/** Main-hand swing originating from left click, including a swing that hits nothing. */
@PacketTarget(ThreadType.SERVER)
public final class PlayerLeftClickSwingPacket
        extends Packet<ServerGamePacketListenerImpl, PlayerLeftClickSwingPacket> {
    public static final PlayerLeftClickSwingPacket INSTANCE = new PlayerLeftClickSwingPacket();
    public static final StreamCodec<ByteBuf, PlayerLeftClickSwingPacket> CODEC =
            StreamCodec.unit(INSTANCE);
    private static boolean serverInitialized;

    private PlayerLeftClickSwingPacket() {
    }

    public static synchronized void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, PlayerLeftClickSwingPacket> getPacketType() {
        return PacketTypes.PLAYER_LEFT_CLICK_SWING.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(PlayerLeftClickSwingPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!player.isAlive()
                    || player.isSpectator()
                    || PlayerControlSessionManager.blocksUntrustedWorldAction(player)
                    || MentalControlRuntime.isFrozen(player)) {
                return;
            }
            BlackWing.Server.onLeftClickSwing(player);
            WhiteWing.Server.onLeftClickSwing(player);
            PlatinumWing.Server.onLeftClickSwing(player);
            IronSandArsenal.Server.onLeftClickSwing(player);
        }
    }
}
