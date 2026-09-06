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
        MisakaComputeIndex.get(server).rebuildIfDirty(server);
        var overworld = server.overworld();
        var networkId = MisakaNAT.get().resolveNetworkId(overworld, record.networkNodePos);
        int total = MisakaNAT.get().countNetworkSisters(overworld, record.networkNodePos);
        int safePage = Math.max(0, pageIndex);
        int maxPage = total <= 0 ? 0 : (total - 1) / PAGE_SIZE;
        if (safePage > maxPage) {
            safePage = maxPage;
        }
        int offset = safePage * PAGE_SIZE;
        var sisterUuids = MisakaNAT.get().listNetworkSisters(overworld, record.networkNodePos, offset, PAGE_SIZE);
        var roster = MisakaSisterRoster.get(server);
        var summaries = new ArrayList<MisakaNetManageDataPacket.SisterSummary>(sisterUuids.size());
        for (var sisterUuid : sisterUuids) {
            var sister = roster.get(sisterUuid).orElse(null);
            if (sister == null) {
                continue;
            }
            String nodeName = WirelessNetworkData.displayName(level, sister.networkNodePos, true);
            var sisterNetworkId = sister.networkNodePos == null
                    ? networkId
                    : MisakaNAT.get().resolveNetworkId(overworld, sister.networkNodePos);
            var loadedSister = MisakaNetworkCoverage.findLoadedSister(server, sister.misakaUuid);
            var sampleLevel = MisakaNetworkCoverage.sampleLevel(server, sister, loadedSister);
            var sample = MisakaNetworkCoverage.samplePos(sister, loadedSister);
            boolean inCoverage = MisakaNAT.get().canUseMisakaService(sampleLevel, sisterNetworkId, sample);
            float msk = (!sister.starving && inCoverage)
                    ? MisakaComputeContribution.mskPerSecond(sister.perception)
                    : 0.0f;
            summaries.add(new MisakaNetManageDataPacket.SisterSummary(
                    sister.serial,
                    sister.perception,
                    msk,
                    nodeName,
                    sister.starving,
                    inCoverage
            ));
        }
        int[] percents = MisakaNetworkAllocations.get(server).get(networkId);
        float totalMsk = MisakaComputeIndex.get(server).networkTotals().getOrDefault(networkId.immutable(), 0f);
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
        MisakaComputeIndex.get(server).rebuildIfDirty(server);
        var overworld = server.overworld();
        var networkId = MisakaNAT.get().resolveNetworkId(overworld, record.networkNodePos);
        MisakaNetworkAllocations.get(server).set(networkId, percents);
        return true;
    }
}
