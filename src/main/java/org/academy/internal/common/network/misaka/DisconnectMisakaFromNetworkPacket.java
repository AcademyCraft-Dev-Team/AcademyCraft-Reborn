package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.misaka.MisakaNetManageSupport;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.UUID;

/**
 * Eject a sister from the network opened through {@code misakaUuid}'s manage console.
 */
@PacketTarget(ThreadType.SERVER)
public final class DisconnectMisakaFromNetworkPacket
        extends Packet<ServerGamePacketListenerImpl, DisconnectMisakaFromNetworkPacket> {
    public static final StreamCodec<ByteBuf, DisconnectMisakaFromNetworkPacket> CODEC = StreamCodec.composite(
            MisakaPacketCodecs.UUID_STREAM_CODEC,
            DisconnectMisakaFromNetworkPacket::misakaUuid,
            MisakaPacketCodecs.UUID_STREAM_CODEC,
            DisconnectMisakaFromNetworkPacket::targetMisakaUuid,
            ByteBufCodecs.VAR_INT,
            DisconnectMisakaFromNetworkPacket::pageIndex,
            DisconnectMisakaFromNetworkPacket::new
    );
    private static boolean serverInitialized;

    private final UUID misakaUuid;
    private final UUID targetMisakaUuid;
    private final int pageIndex;

    public DisconnectMisakaFromNetworkPacket(UUID misakaUuid, UUID targetMisakaUuid, int pageIndex) {
        this.misakaUuid = misakaUuid;
        this.targetMisakaUuid = targetMisakaUuid;
        this.pageIndex = pageIndex;
    }

    public UUID misakaUuid() {
        return misakaUuid;
    }

    public UUID targetMisakaUuid() {
        return targetMisakaUuid;
    }

    public int pageIndex() {
        return pageIndex;
    }

    public static synchronized void initServer() {
        if (serverInitialized) {
            return;
        }
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, DisconnectMisakaFromNetworkPacket> getPacketType() {
        return PacketTypes.DISCONNECT_MISAKA_FROM_NETWORK.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(DisconnectMisakaFromNetworkPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (MisakaNetManageSupport.disconnectSister(
                    player,
                    packet.misakaUuid(),
                    packet.targetMisakaUuid()
            )) {
                MisakaNetManageSupport.sendManagePage(player, packet.misakaUuid(), packet.pageIndex());
            }
        }
    }
}
