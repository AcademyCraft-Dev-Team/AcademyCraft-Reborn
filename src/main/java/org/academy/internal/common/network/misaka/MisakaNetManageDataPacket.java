package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.internal.client.gui.screen.MisakaNetworkPanelScreen;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.world.level.storage.MisakaNetworkAllocations;
import org.misaka.MisakaNetworkClient;
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
    private final float totalDemandMsk;
    private final float yourAllocatedMsk;
    private final float yourSatisfaction;
    private final float priorityAllocatedMsk;
    private final float sharedAllocatedMsk;
    private final List<SisterSummary> sisters;
    private final List<PlayerPercent> playerPercents;
    private final boolean integrated;
    private final List<String> adminNames;
    private final List<MemberSummary> members;
    private final boolean viewerCanEditMembers;

    public MisakaNetManageDataPacket(
            UUID misakaUuid,
            int pageIndex,
            int totalCount,
            float totalMskPerSecond,
            float totalDemandMsk,
            float yourAllocatedMsk,
            float yourSatisfaction,
            float priorityAllocatedMsk,
            float sharedAllocatedMsk,
            List<SisterSummary> sisters,
            List<PlayerPercent> playerPercents,
            boolean integrated,
            List<String> adminNames,
            List<MemberSummary> members,
            boolean viewerCanEditMembers
    ) {
        this.misakaUuid = misakaUuid;
        this.pageIndex = pageIndex;
        this.totalCount = totalCount;
        this.totalMskPerSecond = totalMskPerSecond;
        this.totalDemandMsk = totalDemandMsk;
        this.yourAllocatedMsk = yourAllocatedMsk;
        this.yourSatisfaction = yourSatisfaction;
        this.priorityAllocatedMsk = priorityAllocatedMsk;
        this.sharedAllocatedMsk = sharedAllocatedMsk;
        this.sisters = sisters == null ? List.of() : List.copyOf(sisters);
        this.playerPercents = playerPercents == null
                ? List.of()
                : List.copyOf(toPercents(MisakaNetworkAllocations.clampPlayerPercents(toMap(playerPercents))));
        this.integrated = integrated;
        this.adminNames = adminNames == null ? List.of() : List.copyOf(adminNames);
        this.members = members == null ? List.of() : List.copyOf(members);
        this.viewerCanEditMembers = viewerCanEditMembers;
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

    private static List<PlayerPercent> toPercents(Map<UUID, Integer> map) {
        var list = new ArrayList<PlayerPercent>();
        map.forEach((uuid, percent) -> list.add(new PlayerPercent(uuid, percent)));
        return list;
    }

    private static void encode(ByteBuf buf, MisakaNetManageDataPacket packet) {
        MisakaPacketCodecs.encodeUuid(buf, packet.misakaUuid);
        ByteBufCodecs.VAR_INT.encode(buf, packet.pageIndex);
        ByteBufCodecs.VAR_INT.encode(buf, packet.totalCount);
        buf.writeFloat(packet.totalMskPerSecond);
        buf.writeFloat(packet.totalDemandMsk);
        buf.writeFloat(packet.yourAllocatedMsk);
        buf.writeFloat(packet.yourSatisfaction);
        buf.writeFloat(packet.priorityAllocatedMsk);
        buf.writeFloat(packet.sharedAllocatedMsk);
        ByteBufCodecs.VAR_INT.encode(buf, packet.sisters.size());
        for (var sister : packet.sisters) {
            MisakaPacketCodecs.encodeUuid(buf, sister.misakaUuid());
            ByteBufCodecs.VAR_INT.encode(buf, sister.serial());
            ByteBufCodecs.VAR_INT.encode(buf, sister.perception());
            buf.writeFloat(sister.msk());
            ByteBufCodecs.STRING_UTF8.encode(buf, sister.nodeName());
            buf.writeBoolean(sister.starving());
            buf.writeBoolean(sister.incapacitated());
            buf.writeBoolean(sister.inCoverage());
        }
        ByteBufCodecs.VAR_INT.encode(buf, packet.playerPercents.size());
        for (var entry : packet.playerPercents) {
            MisakaPacketCodecs.encodeUuid(buf, entry.playerUuid());
            ByteBufCodecs.VAR_INT.encode(buf, entry.percent());
        }
        buf.writeBoolean(packet.integrated);
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8)
                .encode(buf, new ArrayList<>(packet.adminNames));
        ByteBufCodecs.VAR_INT.encode(buf, packet.members.size());
        for (var member : packet.members) {
            MisakaPacketCodecs.encodeUuid(buf, member.playerUuid());
            ByteBufCodecs.STRING_UTF8.encode(buf, member.name());
            buf.writeBoolean(member.admin());
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8)
                    .encode(buf, new ArrayList<>(member.permissions()));
        }
        buf.writeBoolean(packet.viewerCanEditMembers);
    }

    private static MisakaNetManageDataPacket decode(ByteBuf buf) {
        UUID misakaUuid = MisakaPacketCodecs.decodeUuid(buf);
        int pageIndex = ByteBufCodecs.VAR_INT.decode(buf);
        int totalCount = ByteBufCodecs.VAR_INT.decode(buf);
        float totalMskPerSecond = buf.readFloat();
        float totalDemandMsk = buf.readFloat();
        float yourAllocatedMsk = buf.readFloat();
        float yourSatisfaction = buf.readFloat();
        float priorityAllocatedMsk = buf.readFloat();
        float sharedAllocatedMsk = buf.readFloat();
        int sisterCount = ByteBufCodecs.VAR_INT.decode(buf);
        var sisters = new ArrayList<SisterSummary>(sisterCount);
        for (int i = 0; i < sisterCount; i++) {
            sisters.add(new SisterSummary(
                    MisakaPacketCodecs.decodeUuid(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    buf.readFloat(),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean()
            ));
        }
        int percentCount = ByteBufCodecs.VAR_INT.decode(buf);
        var playerPercents = new ArrayList<PlayerPercent>(percentCount);
        for (int i = 0; i < percentCount; i++) {
            playerPercents.add(new PlayerPercent(
                    MisakaPacketCodecs.decodeUuid(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)
            ));
        }
        boolean integrated = buf.readBoolean();
        List<String> adminNames = ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).decode(buf);
        int memberCount = ByteBufCodecs.VAR_INT.decode(buf);
        var members = new ArrayList<MemberSummary>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            members.add(new MemberSummary(
                    MisakaPacketCodecs.decodeUuid(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    buf.readBoolean(),
                    ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).decode(buf)
            ));
        }
        boolean viewerCanEditMembers = buf.readBoolean();
        return new MisakaNetManageDataPacket(
                misakaUuid,
                pageIndex,
                totalCount,
                totalMskPerSecond,
                totalDemandMsk,
                yourAllocatedMsk,
                yourSatisfaction,
                priorityAllocatedMsk,
                sharedAllocatedMsk,
                sisters,
                playerPercents,
                integrated,
                adminNames,
                members,
                viewerCanEditMembers
        );
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

    public float totalDemandMsk() {
        return totalDemandMsk;
    }

    public float yourAllocatedMsk() {
        return yourAllocatedMsk;
    }

    public float yourSatisfaction() {
        return yourSatisfaction;
    }

    public float priorityAllocatedMsk() {
        return priorityAllocatedMsk;
    }

    public float sharedAllocatedMsk() {
        return sharedAllocatedMsk;
    }

    public List<SisterSummary> sisters() {
        return sisters;
    }

    public List<PlayerPercent> playerPercents() {
        return playerPercents;
    }

    public boolean integrated() {
        return integrated;
    }

    public List<String> adminNames() {
        return adminNames;
    }

    public List<MemberSummary> members() {
        return members;
    }

    public boolean viewerCanEditMembers() {
        return viewerCanEditMembers;
    }

    public int allocatedSum() {
        return MisakaNetworkAllocations.sum(toMap(playerPercents));
    }

    @Override
    public PacketType<ClientPacketListener, MisakaNetManageDataPacket> getPacketType() {
        return PacketTypes.MISAKA_NET_MANAGE_DATA.get();
    }

    public record SisterSummary(
            UUID misakaUuid,
            int serial,
            int perception,
            float msk,
            String nodeName,
            boolean starving,
            boolean incapacitated,
            boolean inCoverage
    ) {
        public SisterSummary {
            nodeName = nodeName == null ? "" : nodeName;
        }
    }

    public record PlayerPercent(UUID playerUuid, int percent) {
    }

    public record MemberSummary(UUID playerUuid, String name, boolean admin, List<String> permissions) {
        public MemberSummary {
            name = name == null ? "" : name;
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
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
