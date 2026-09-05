package org.academy.internal.common.world.entity.misaka.perception;

import net.minecraft.server.MinecraftServer;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.jspecify.annotations.Nullable;

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
        int next = Math.min(record.perceptionCap, record.perception + amount);
        if (next >= 101 && hasOtherReconstructionWork(server, record)) {
            next = Math.min(next, 100);
        }
        int gained = next - record.perception;
        record.perception = next;
        if (record.perception > 101 && !record.highTierUnlocked) {
            record.highTierUnlocked = true;
            record.perceptionCap = HIGH_TIER_CAP;
        }
        return gained;
    }

    public static void applyDailyDecay(MisakaSisterRecord record) {
        if (!record.awakened) {
            return;
        }
        int loss = 1 + (record.perception / 20);
        int floor = record.highTierUnlocked ? 101 : 1;
        record.perception = Math.max(floor, record.perception - loss);
    }

    private static boolean hasOtherReconstructionWork(MinecraftServer server, MisakaSisterRecord record) {
        return record.networkNodePos != null
                && MisakaNAT.get().hasReconstructionWork(server, record.networkNodePos, record.misakaUuid);
    }
}
