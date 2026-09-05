package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.misaka.MisakaComputeSink;
import org.academy.internal.server.misaka.MisakaNetManageSupport;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.UUID;

@PacketTarget(ThreadType.SERVER)
public final class SetMisakaNetworkAllocationPacket
        extends Packet<ServerGamePacketListenerImpl, SetMisakaNetworkAllocationPacket> {
    public static final StreamCodec<ByteBuf, SetMisakaNetworkAllocationPacket> CODEC = StreamCodec.of(
            SetMisakaNetworkAllocationPacket::encode,
            SetMisakaNetworkAllocationPacket::decode
    );
    private static boolean serverInitialized;

    private final UUID misakaUuid;
    private final int[] percents;
    private final int pageIndex;

    public SetMisakaNetworkAllocationPacket(UUID misakaUuid, int[] percents, int pageIndex) {
        this.misakaUuid = misakaUuid;
        this.percents = MisakaComputeSink.clampAllocations(percents);
        this.pageIndex = pageIndex;
    }

    private static void encode(ByteBuf buf, SetMisakaNetworkAllocationPacket packet) {
        MisakaPacketCodecs.encodeUuid(buf, packet.misakaUuid);
        for (int i = 0; i < MisakaComputeSink.COUNT; i++) {
            ByteBufCodecs.VAR_INT.encode(buf, packet.percents[i]);
        }
        ByteBufCodecs.VAR_INT.encode(buf, packet.pageIndex);
    }

    private static SetMisakaNetworkAllocationPacket decode(ByteBuf buf) {
        UUID misakaUuid = MisakaPacketCodecs.decodeUuid(buf);
        int[] percents = new int[MisakaComputeSink.COUNT];
        for (int i = 0; i < MisakaComputeSink.COUNT; i++) {
            percents[i] = ByteBufCodecs.VAR_INT.decode(buf);
        }
        int pageIndex = ByteBufCodecs.VAR_INT.decode(buf);
        return new SetMisakaNetworkAllocationPacket(misakaUuid, percents, pageIndex);
    }

    public UUID misakaUuid() {
        return misakaUuid;
    }

    public int[] percents() {
        return percents.clone();
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
    public PacketType<ServerGamePacketListenerImpl, SetMisakaNetworkAllocationPacket> getPacketType() {
        return PacketTypes.SET_MISAKA_NETWORK_ALLOCATION.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(SetMisakaNetworkAllocationPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (MisakaNetManageSupport.setAllocations(player, packet.misakaUuid(), packet.percents())) {
                MisakaNetManageSupport.sendManagePage(player, packet.misakaUuid(), packet.pageIndex());
            }
        }
    }
}
