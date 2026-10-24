package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.misaka.MisakaNetManageSupport;
import org.academy.internal.server.world.level.storage.MisakaNetworkAllocations;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final List<PlayerPercent> percents;
    private final int pageIndex;

    public SetMisakaNetworkAllocationPacket(UUID misakaUuid, List<PlayerPercent> percents, int pageIndex) {
        this.misakaUuid = misakaUuid;
        this.percents = List.copyOf(MisakaNetworkAllocations.clampPlayerPercents(toMap(percents)).entrySet()
                .stream()
                .map(e -> new PlayerPercent(e.getKey(), e.getValue()))
                .toList());
        this.pageIndex = pageIndex;
    }

    public SetMisakaNetworkAllocationPacket(UUID misakaUuid, Map<UUID, Integer> percents, int pageIndex) {
        this(misakaUuid, percents.entrySet().stream()
                .map(e -> new PlayerPercent(e.getKey(), e.getValue()))
                .toList(), pageIndex);
    }

    private static Map<UUID, Integer> toMap(List<PlayerPercent> list) {
        var map = new LinkedHashMap<UUID, Integer>();
        if (list == null) {
            return map;
        }
        for (var entry : list) {
            if (entry != null && entry.playerUuid() != null) {
                map.put(entry.playerUuid(), entry.percent());
            }
        }
        return map;
    }

    private static void encode(ByteBuf buf, SetMisakaNetworkAllocationPacket packet) {
        MisakaPacketCodecs.encodeUuid(buf, packet.misakaUuid);
        ByteBufCodecs.VAR_INT.encode(buf, packet.percents.size());
        for (var entry : packet.percents) {
            MisakaPacketCodecs.encodeUuid(buf, entry.playerUuid());
            ByteBufCodecs.VAR_INT.encode(buf, entry.percent());
        }
        ByteBufCodecs.VAR_INT.encode(buf, packet.pageIndex);
    }

    private static SetMisakaNetworkAllocationPacket decode(ByteBuf buf) {
        UUID misakaUuid = MisakaPacketCodecs.decodeUuid(buf);
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        var list = new ArrayList<PlayerPercent>(count);
        for (int i = 0; i < count; i++) {
            UUID playerUuid = MisakaPacketCodecs.decodeUuid(buf);
            int percent = ByteBufCodecs.VAR_INT.decode(buf);
            list.add(new PlayerPercent(playerUuid, percent));
        }
        int pageIndex = ByteBufCodecs.VAR_INT.decode(buf);
        return new SetMisakaNetworkAllocationPacket(misakaUuid, list, pageIndex);
    }

    public UUID misakaUuid() {
        return misakaUuid;
    }

    public List<PlayerPercent> percents() {
        return percents;
    }

    public Map<UUID, Integer> percentMap() {
        return toMap(percents);
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
            if (MisakaNetManageSupport.setAllocations(player, packet.misakaUuid(), packet.percentMap())) {
                MisakaNetManageSupport.sendManagePage(player, packet.misakaUuid(), packet.pageIndex());
            }
        }
    }

    public record PlayerPercent(UUID playerUuid, int percent) {
    }
}
