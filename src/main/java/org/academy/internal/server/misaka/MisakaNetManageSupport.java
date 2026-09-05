package org.academy.internal.server.misaka;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.network.misaka.MisakaNetManageDataPacket;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.world.level.storage.MisakaNetworkAllocations;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.misaka.MisakaNetworkServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MisakaNetManageSupport {
    public static final int PAGE_SIZE = WirelessForwardingMisakaNAT.MANAGE_PAGE_SIZE;

    private MisakaNetManageSupport() {
    }

    public static boolean canManage(ServerPlayer player, MisakaSisterRecord record) {
        if (record == null || player == null) {
            return false;
        }
        if (record.perception < 101 || record.networkNodePos == null) {
            return false;
        }
        return FavorService.isPrivilegePlayer(record, player.getGameProfile().name());
    }

    public static void sendManagePage(ServerPlayer player, UUID misakaUuid, int pageIndex) {
        var packet = buildManagePage(player, misakaUuid, pageIndex);
        if (packet != null) {
            MisakaNetworkServer.send(player, packet);
        }
    }

    public static MisakaNetManageDataPacket buildManagePage(
            ServerPlayer player,
            UUID misakaUuid,
            int pageIndex
    ) {
        var server = player.level().getServer();
        if (server == null || misakaUuid == null) {
            return null;
        }
        var record = MisakaSisterRoster.get(server).get(misakaUuid).orElse(null);
        if (!canManage(player, record)) {
            return null;
        }
        var level = (ServerLevel) player.level();
        MisakaComputeIndex.get().rebuildIfDirty(server);
        var networkId = MisakaNAT.get().resolveNetworkId(level, record.networkNodePos);
        int total = MisakaNAT.get().countNetworkSisters(level, record.networkNodePos);
        int safePage = Math.max(0, pageIndex);
        int maxPage = total <= 0 ? 0 : (total - 1) / PAGE_SIZE;
        if (safePage > maxPage) {
            safePage = maxPage;
        }
        int offset = safePage * PAGE_SIZE;
        var sisters = MisakaNAT.get().listNetworkSisters(level, record.networkNodePos, offset, PAGE_SIZE);
        var summaries = new ArrayList<MisakaNetManageDataPacket.SisterSummary>(sisters.size());
        var wireless = WirelessNetworkData.get(level);
        for (var sister : sisters) {
            String nodeName = "";
            if (sister.networkNodePos != null) {
                var config = wireless.getNodeConfig(sister.networkNodePos);
                if (config != null) {
                    nodeName = config.name;
                }
            }
            summaries.add(new MisakaNetManageDataPacket.SisterSummary(
                    sister.serial,
                    sister.perception,
                    MisakaComputeContribution.mskPerSecond(sister.perception),
                    nodeName,
                    sister.starving
            ));
        }
        int[] percents = MisakaNetworkAllocations.get(server).get(networkId);
        float totalMsk = MisakaComputeIndex.get().networkTotals().getOrDefault(networkId.immutable(), 0f);
        return new MisakaNetManageDataPacket(
                misakaUuid,
                safePage,
                total,
                totalMsk,
                summaries,
                percents
        );
    }

    public static boolean setAllocations(ServerPlayer player, UUID misakaUuid, int[] percents) {
        var server = player.level().getServer();
        if (server == null || misakaUuid == null) {
            return false;
        }
        var record = MisakaSisterRoster.get(server).get(misakaUuid).orElse(null);
        if (!canManage(player, record)) {
            return false;
        }
        var level = (ServerLevel) player.level();
        var networkId = MisakaNAT.get().resolveNetworkId(level, record.networkNodePos);
        MisakaNetworkAllocations.get(server).set(networkId, percents);
        return true;
    }
}
