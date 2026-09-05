package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.internal.client.gui.screen.MisakaNetworkPanelScreen;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.misaka.MisakaComputeSink;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PacketTarget(ThreadType.CLIENT)
public final class MisakaNetManageDataPacket
        extends Packet<ClientPacketListener, MisakaNetManageDataPacket> {
    public static final StreamCodec<ByteBuf, MisakaNetManageDataPacket> CODEC = StreamCodec.of(
            MisakaNetManageDataPacket::encode,
            MisakaNetManageDataPacket::decode
    );
    private static boolean clientInitialized;

    private final UUID misakaUuid;
    private final int pageIndex;
    private final int totalCount;
    private final float totalMskPerSecond;
    private final List<SisterSummary> sisters;
    private final int[] percents;

    public MisakaNetManageDataPacket(
            UUID misakaUuid,
            int pageIndex,
            int totalCount,
            float totalMskPerSecond,
            List<SisterSummary> sisters,
            int[] percents
    ) {
        this.misakaUuid = misakaUuid;
        this.pageIndex = pageIndex;
        this.totalCount = totalCount;
        this.totalMskPerSecond = totalMskPerSecond;
        this.sisters = sisters == null ? List.of() : List.copyOf(sisters);
        this.percents = MisakaComputeSink.clampAllocations(percents);
    }

    private static void encode(ByteBuf buf, MisakaNetManageDataPacket packet) {
        MisakaPacketCodecs.encodeUuid(buf, packet.misakaUuid);
        ByteBufCodecs.VAR_INT.encode(buf, packet.pageIndex);
        ByteBufCodecs.VAR_INT.encode(buf, packet.totalCount);
        buf.writeFloat(packet.totalMskPerSecond);
        ByteBufCodecs.VAR_INT.encode(buf, packet.sisters.size());
        for (var sister : packet.sisters) {
            ByteBufCodecs.VAR_INT.encode(buf, sister.serial());
            ByteBufCodecs.VAR_INT.encode(buf, sister.perception());
            buf.writeFloat(sister.msk());
            ByteBufCodecs.STRING_UTF8.encode(buf, sister.nodeName());
            buf.writeBoolean(sister.starving());
        }
        for (int i = 0; i < MisakaComputeSink.COUNT; i++) {
            ByteBufCodecs.VAR_INT.encode(buf, packet.percents[i]);
        }
    }

    private static MisakaNetManageDataPacket decode(ByteBuf buf) {
        UUID misakaUuid = MisakaPacketCodecs.decodeUuid(buf);
        int pageIndex = ByteBufCodecs.VAR_INT.decode(buf);
        int totalCount = ByteBufCodecs.VAR_INT.decode(buf);
        float totalMskPerSecond = buf.readFloat();
        int sisterCount = ByteBufCodecs.VAR_INT.decode(buf);
        var sisters = new ArrayList<SisterSummary>(sisterCount);
        for (int i = 0; i < sisterCount; i++) {
            sisters.add(new SisterSummary(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    buf.readFloat(),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    buf.readBoolean()
            ));
        }
        int[] percents = new int[MisakaComputeSink.COUNT];
        for (int i = 0; i < MisakaComputeSink.COUNT; i++) {
            percents[i] = ByteBufCodecs.VAR_INT.decode(buf);
        }
        return new MisakaNetManageDataPacket(
                misakaUuid, pageIndex, totalCount, totalMskPerSecond, sisters, percents);
    }

    public static synchronized void initClient() {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public UUID misakaUuid() {
        return misakaUuid;
    }

    public int pageIndex() {
        return pageIndex;
    }

    public int totalCount() {
        return totalCount;
    }

    public float totalMskPerSecond() {
        return totalMskPerSecond;
    }

    public List<SisterSummary> sisters() {
        return sisters;
    }

    public int[] percents() {
        return percents.clone();
    }

    public int allocatedSum() {
        return MisakaComputeSink.sum(percents);
    }

    @Override
    public PacketType<ClientPacketListener, MisakaNetManageDataPacket> getPacketType() {
        return PacketTypes.MISAKA_NET_MANAGE_DATA.get();
    }

    public record SisterSummary(int serial, int perception, float msk, String nodeName, boolean starving) {
        public SisterSummary {
            nodeName = nodeName == null ? "" : nodeName;
        }
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handle(MisakaNetManageDataPacket packet) {
            Minecraft.getInstance().execute(() -> {
                var screen = Minecraft.getInstance().gui.screen();
                if (screen instanceof MisakaNetworkPanelScreen panel) {
                    panel.applyManageData(packet);
                }
            });
        }
    }
}
