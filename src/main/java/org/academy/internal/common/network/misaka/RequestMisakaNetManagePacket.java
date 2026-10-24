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

@PacketTarget(ThreadType.SERVER)
public final class RequestMisakaNetManagePacket
        extends Packet<ServerGamePacketListenerImpl, RequestMisakaNetManagePacket> {
    public static final StreamCodec<ByteBuf, RequestMisakaNetManagePacket> CODEC = StreamCodec.composite(
            MisakaPacketCodecs.UUID_STREAM_CODEC,
            RequestMisakaNetManagePacket::misakaUuid,
            ByteBufCodecs.VAR_INT,
            RequestMisakaNetManagePacket::pageIndex,
            RequestMisakaNetManagePacket::new
    );
    private static boolean serverInitialized;

    private final UUID misakaUuid;
    private final int pageIndex;

    public RequestMisakaNetManagePacket(UUID misakaUuid, int pageIndex) {
        this.misakaUuid = misakaUuid;
        this.pageIndex = pageIndex;
    }

    public UUID misakaUuid() {
        return misakaUuid;
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
    public PacketType<ServerGamePacketListenerImpl, RequestMisakaNetManagePacket> getPacketType() {
        return PacketTypes.REQUEST_MISAKA_NET_MANAGE.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(RequestMisakaNetManagePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            MisakaNetManageSupport.sendManagePage(player, packet.misakaUuid(), packet.pageIndex());
        }
    }
}
