package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.misaka.MisakaDeviceQuotaSync;
import org.academy.internal.server.misaka.ServerMisakaComputeAccess;
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

@PacketTarget(ThreadType.SERVER)
public final class SetMisakaDeviceAllocationPacket
        extends Packet<ServerGamePacketListenerImpl, SetMisakaDeviceAllocationPacket> {
    public static final StreamCodec<ByteBuf, SetMisakaDeviceAllocationPacket> CODEC = StreamCodec.of(
            SetMisakaDeviceAllocationPacket::encode,
            SetMisakaDeviceAllocationPacket::decode
    );
    private static boolean serverInitialized;

    private final List<DevicePercent> percents;

    public SetMisakaDeviceAllocationPacket(List<DevicePercent> percents) {
        this.percents = percents == null ? List.of() : List.copyOf(percents);
    }

    private static void encode(ByteBuf buf, SetMisakaDeviceAllocationPacket packet) {
        ByteBufCodecs.VAR_INT.encode(buf, packet.percents.size());
        for (var entry : packet.percents) {
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.id().toString());
            ByteBufCodecs.VAR_INT.encode(buf, entry.percent());
        }
    }

    private static SetMisakaDeviceAllocationPacket decode(ByteBuf buf) {
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        var list = new ArrayList<DevicePercent>(count);
        for (int i = 0; i < count; i++) {
            var id = Identifier.parse(ByteBufCodecs.STRING_UTF8.decode(buf));
            int percent = ByteBufCodecs.VAR_INT.decode(buf);
            list.add(new DevicePercent(id, percent));
        }
        return new SetMisakaDeviceAllocationPacket(list);
    }

    public List<DevicePercent> percents() {
        return percents;
    }

    public static synchronized void initServer() {
        if (serverInitialized) {
            return;
        }
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, SetMisakaDeviceAllocationPacket> getPacketType() {
        return PacketTypes.SET_MISAKA_DEVICE_ALLOCATION.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(SetMisakaDeviceAllocationPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Map<Identifier, Integer> map = new LinkedHashMap<>();
            for (var entry : packet.percents()) {
                if (entry.id() != null) {
                    map.put(entry.id(), entry.percent());
                }
            }
            ServerMisakaComputeAccess.INSTANCE.setDeviceAllocations(player, map);
            MisakaDeviceQuotaSync.sync(player);
        }
    }

    public record DevicePercent(Identifier id, int percent) {
    }
}
