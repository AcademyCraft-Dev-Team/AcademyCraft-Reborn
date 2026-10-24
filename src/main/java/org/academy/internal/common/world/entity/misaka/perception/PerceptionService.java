package org.academy.internal.common.world.entity.misaka.perception;

import net.minecraft.server.MinecraftServer;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.misaka.MisakaComputeIndex;
import org.academy.internal.server.misaka.MisakaPlayers;
import org.academy.internal.server.world.level.storage.MisakaNetworkGovernance;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class PerceptionService {
    public static final int AWAKE_WINDOW_TICKS = 1000;
    public static final int BASE_CAP = 100;
    public static final int PROMAX_CAP = 110;
    public static final int HIGH_TIER_CAP = 200;

    private PerceptionService() {
    }

    public static void awakenWithTower(MisakaSisterRecord record, String feeder, long gameTime) {
        awakenWithTower(record, feeder, gameTime, null);
    }

    public static void awakenWithTower(
            MisakaSisterRecord record,
            String feeder,
            long gameTime,
            @Nullable MinecraftServer server
    ) {
        if (record.awakened) {
            return;
        }
        record.awakened = true;
        record.perception = 1;
        record.awakeWindowEndGameTime = gameTime + AWAKE_WINDOW_TICKS;
        if (server != null) {
            FavorService.modifyFavorLan(server, record, feeder, 1);
        } else {
            FavorService.modifyFavor(record, feeder, 1);
        }
    }

    public static boolean tryBreakLimit(MinecraftServer server, MisakaSisterRecord record) {
        if (!record.awakened || record.promaxUsed) {
            return false;
        }
        if (hasOtherReconstructionWork(server, record)) {
            return false;
        }
        record.promaxUsed = true;
        record.perceptionCap = PROMAX_CAP;
        gain(server, record, 10);
        return true;
    }

    public static int gain(MinecraftServer server, MisakaSisterRecord record, int amount) {
        if (!record.awakened || amount <= 0) {
            return 0;
        }
        boolean wasReconstruction = record.isReconstruction || record.perception >= 101;
        int next = Math.min(record.perceptionCap, record.perception + amount);
        if (next >= 101 && hasOtherReconstructionWork(server, record)) {
            next = Math.min(next, 100);
        }
        int gained = next - record.perception;
        record.perception = next;
        // Reconstruction identity begins at perception >= 101 (design §3.4).
        if (record.perception >= 101) {
            record.isReconstruction = true;
            if (!record.highTierUnlocked) {
                record.highTierUnlocked = true;
                record.perceptionCap = HIGH_TIER_CAP;
            }
            if (!wasReconstruction) {
                tryIntegrateIfEligible(server, record);
            }
        }
        if (gained > 0 && server != null) {
            MisakaSisterRoster.get(server).markEntitySyncDirty(record.misakaUuid);
        }
        return gained;
    }

    /**
     * First reconstruction sister on a network while bound + privilege-resolvable
     * triggers network integration (first ADMIN). Safe to call repeatedly — no-ops when
     * already integrated, not reconstruction, unbound, or privilege cannot be resolved.
     * <p>
     * Must also run on bind / privilege-touch: becoming reconstruction while unbound
     * used to miss integration forever and leave manage UI on “await first integration”.
     */
    public static void tryIntegrateIfEligible(MinecraftServer server, MisakaSisterRecord record) {
        if (server == null || record == null) {
            return;
        }
        if (!(record.isReconstruction || record.perception >= 101)) {
            return;
        }
        if (record.networkNodePos == null) {
            return;
        }
        var overworld = server.overworld();
        var networkId = MisakaNAT.get().resolveNetworkId(overworld, record.networkNodePos);
        var governance = MisakaNetworkGovernance.get(server);
        if (governance.hasEverIntegrated(networkId)) {
            governance.setReconstructionUuid(networkId, record.misakaUuid);
            return;
        }
        String name = record.lastInteractedBenevolentPlayerName;
        if (name == null || name.isEmpty() || !FavorService.isPrivilegePlayer(record, name)) {
            // No privilege feeder yet — leave integration for a later successful path.
            return;
        }
        UUID playerUuid = MisakaComputeIndex.get(server).resolvePlayerUuid(name);
        if (playerUuid == null) {
            var online = MisakaPlayers.findOnlineByName(server, name);
            if (online != null) {
                playerUuid = online.getUUID();
                MisakaComputeIndex.get(server).putPlayerName(name, playerUuid);
            }
        }
        if (playerUuid == null) {
            return;
        }
        if (governance.onFirstIntegration(networkId, playerUuid, name)) {
            governance.setReconstructionUuid(networkId, record.misakaUuid);
        }
    }

    public static void applyDailyDecay(MisakaSisterRecord record) {
        if (!record.awakened) {
            return;
        }
        int loss = 1 + (record.perception / 20);
        int floor = record.isReconstruction || record.highTierUnlocked ? 101 : 1;
        record.perception = Math.max(floor, record.perception - loss);
        if (record.perception < 101) {
            record.isReconstruction = false;
        }
    }

    private static boolean hasOtherReconstructionWork(MinecraftServer server, MisakaSisterRecord record) {
        return record.networkNodePos != null
                && MisakaNAT.get().hasReconstructionWork(server, record.networkNodePos, record.misakaUuid);
    }
}
