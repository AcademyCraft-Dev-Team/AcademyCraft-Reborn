package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.fml.ModList;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.compatibility.MagneticHookCuriosCompat;
import org.academy.internal.common.world.item.MagneticHookItem;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

/** Middle-mouse action sent while the magnetic hook is equipped in a Curios belt slot. */
@PacketTarget(ThreadType.SERVER)
public final class MagneticHookActionPacket
        extends Packet<ServerGamePacketListenerImpl, MagneticHookActionPacket> {
    public static final StreamCodec<ByteBuf, MagneticHookActionPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            MagneticHookActionPacket::recall,
            MagneticHookActionPacket::new
    );
    private static boolean serverInitialized;
    private final boolean recall;

    public MagneticHookActionPacket(boolean recall) {
        this.recall = recall;
    }

    public boolean recall() {
        return recall;
    }

    public static synchronized void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, MagneticHookActionPacket> getPacketType() {
        return PacketTypes.MAGNETIC_HOOK_ACTION.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(MagneticHookActionPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!ModList.get().isLoaded("curios")
                    || !player.isAlive()
                    || player.isSpectator()
                    || PlayerControlSessionManager.blocksUntrustedWorldAction(player)
                    || MentalControlRuntime.isFrozen(player)) {
                return;
            }
            MagneticHookCuriosCompat.findEquippedBeltHook(player)
                    .ifPresent(stack -> MagneticHookItem.performAction(player, stack, packet.recall()));
        }
    }
}
