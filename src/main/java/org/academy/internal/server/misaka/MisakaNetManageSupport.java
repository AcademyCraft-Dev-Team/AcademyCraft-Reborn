package org.academy.internal.server.misaka;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.misaka.MisakaNetworkPermission;
import org.academy.internal.common.network.misaka.MisakaNetManageDataPacket;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.world.level.storage.MisakaNetworkAllocations;
import org.academy.internal.server.world.level.storage.MisakaNetworkGovernance;
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

    /**
     * Whether {@code player} may open/modify network manage UI via {@code record}'s console entry.
     * Prefers network {@link MisakaNetworkPermission#ENTITY_CONFIG}/{@link MisakaNetworkPermission#ADMIN};
     * falls back to privilege + reconstruction when governance has no admins yet.
     */
    public static boolean canManage(ServerPlayer player, MisakaSisterRecord record) {
        if (record == null || player == null || record.networkNodePos == null) {
            return false;
        }
        var server = player.level().getServer();
        if (server == null) {
            return false;
        }
        var overworld = server.overworld();
        var networkId = MisakaNAT.get().resolveNetworkId(overworld, record.networkNodePos);
        var governance = MisakaNetworkGovernance.get(server);
        if (governance.hasPermission(player, networkId, MisakaNetworkPermission.ENTITY_CONFIG)
                || governance.hasPermission(player, networkId, MisakaNetworkPermission.ADMIN)) {
            return true;
        }
        boolean reconstruction = record.isReconstruction || record.perception >= 101;
        return reconstruction && FavorService.isPrivilegePlayer(record, player.getGameProfile().name());
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
            float msk = (!sister.starving && !sister.incapacitated && inCoverage)
                    ? MisakaComputeContribution.mskPerSecond(sister.perception)
                    : 0.0f;
            summaries.add(new MisakaNetManageDataPacket.SisterSummary(
                    sister.misakaUuid,
                    sister.serial,
                    sister.perception,
                    msk,
                    nodeName,
                    sister.starving,
                    inCoverage
            ));
        }
        int[] percents = MisakaNetworkAllocations.get(server).get(networkId);
        float totalMsk = MisakaComputeIndex.get(server).networkTotals().getOrDefault(networkId, 0f);
        float demandMsk = MisakaComputeContribution.networkDemandMsk(server, networkId);
        String playerName = player.getGameProfile().name();
        float yourAllocated = MisakaComputeContribution.lastAllocatedMsk(playerName);
        float yourSatisfaction = MisakaComputeContribution.lastSatisfactionByName.containsKey(playerName)
                ? MisakaComputeContribution.lastSatisfaction(playerName)
                : MisakaComputeContribution.networkSatisfaction(totalMsk, demandMsk);
        var governance = MisakaNetworkGovernance.get(server);
        var memberRows = governance.listMemberRows(networkId);
        var members = new ArrayList<MisakaNetManageDataPacket.MemberSummary>(memberRows.size());
        for (var row : memberRows) {
            var permNames = new ArrayList<String>(row.permissions().size());
            for (var perm : row.permissions()) {
                permNames.add(perm.name());
            }
            members.add(new MisakaNetManageDataPacket.MemberSummary(row.name(), row.admin(), permNames));
        }
        boolean canEdit = governance.hasPermission(player, networkId, MisakaNetworkPermission.ADMIN);
        return new MisakaNetManageDataPacket(
                misakaUuid,
                safePage,
                total,
                totalMsk,
                demandMsk,
                yourAllocated,
                yourSatisfaction,
                MisakaComputeContribution.lastNetworkPriorityAllocatedMsk(networkId),
                MisakaComputeContribution.lastNetworkSharedAllocatedMsk(networkId),
                summaries,
                percents,
                governance.listAdminDisplayNames(networkId),
                members,
                canEdit
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

    /**
     * Eject a sister from the network the console belongs to (design §7.2 "断开成员").
     * Only removes membership: the target must already be on this network, so this cannot be
     * used to pull sisters out of someone else's network. The sister's privilege player may
     * reconnect unless ACCESS is also revoked.
     */
    public static boolean disconnectSister(
            ServerPlayer actor,
            UUID consoleMisakaUuid,
            UUID targetMisakaUuid
    ) {
        if (actor == null || consoleMisakaUuid == null || targetMisakaUuid == null) {
            return false;
        }
        var server = actor.level().getServer();
        if (server == null) {
            return false;
        }
        var roster = MisakaSisterRoster.get(server);
        var console = roster.get(consoleMisakaUuid).orElse(null);
        if (!canManage(actor, console)) {
            return false;
        }
        // Keeping the console anchor connected means the page can still refresh afterwards.
        if (consoleMisakaUuid.equals(targetMisakaUuid)) {
            return false;
        }
        var target = roster.get(targetMisakaUuid).orElse(null);
        if (target == null || target.networkNodePos == null) {
            return false;
        }
        var overworld = server.overworld();
        var consoleNetwork = MisakaNAT.get().resolveNetworkId(overworld, console.networkNodePos);
        var targetNetwork = MisakaNAT.get().resolveNetworkId(overworld, target.networkNodePos);
        if (!consoleNetwork.equals(targetNetwork)) {
            return false;
        }
        return MisakaNAT.get().unbindSister(server, targetMisakaUuid);
    }

    /**
     * Grant/revoke a member permission. Requires ADMIN on the network. OWNER grants are rejected
     * from the panel (device ownership is separate).
     */
    public static boolean setMemberPermission(
            ServerPlayer actor,
            UUID misakaUuid,
            String targetPlayerName,
            MisakaNetworkPermission permission,
            boolean grant
    ) {
        if (actor == null || misakaUuid == null || targetPlayerName == null || targetPlayerName.isBlank()) {
            return false;
        }
        if (permission == null || permission == MisakaNetworkPermission.OWNER) {
            return false;
        }
        var server = actor.level().getServer();
        if (server == null) {
            return false;
        }
        var record = MisakaSisterRoster.get(server).get(misakaUuid).orElse(null);
        if (record == null || record.networkNodePos == null) {
            return false;
        }
        var networkId = MisakaNAT.get().resolveNetworkId(server.overworld(), record.networkNodePos);
        var governance = MisakaNetworkGovernance.get(server);
        if (!governance.hasPermission(actor, networkId, MisakaNetworkPermission.ADMIN)) {
            return false;
        }
        var onlineTarget = MisakaPlayers.findOnlineByName(server, targetPlayerName);
        UUID targetUuid = onlineTarget == null ? null : onlineTarget.getUUID();
        String nameCache = onlineTarget == null ? targetPlayerName.trim() : onlineTarget.getGameProfile().name();
        if (targetUuid == null) {
            // Fall back to matching an existing governance row by display name.
            for (var row : governance.listMemberRows(networkId)) {
                if (row.name().equalsIgnoreCase(nameCache)) {
                    // Cannot resolve offline UUID from name alone for new grants.
                    if (grant) {
                        return false;
                    }
                    // Revoke: find UUID via admin/member maps by scanning name caches is not available;
                    // require online player for both grant and revoke for safety.
                    return false;
                }
            }
            return false;
        }
        if (grant) {
            governance.grant(networkId, targetUuid, nameCache, permission);
        } else {
            governance.revoke(networkId, targetUuid, permission);
        }
        return true;
    }
}
