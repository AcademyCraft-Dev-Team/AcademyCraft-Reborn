package org.academy.internal.server.misaka;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.entity.misaka.MisakaDayTime;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.common.world.entity.misaka.perception.PerceptionService;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

import java.util.HashSet;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MisakaDailyTicker {
    private MisakaDailyTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        // settleAndApply self-gates to every SETTLE_INTERVAL_TICKS (1s) with full MSk/s.
        MisakaComputeContribution.settleAndApply(server);

        if (server.getTickCount() % 20 != 0) {
            return;
        }
        int day = MisakaDayTime.dayIndex(server.overworld());
        var roster = MisakaSisterRoster.get(server);
        boolean dirty = false;
        var namesToRefresh = new HashSet<String>();
        for (var record : roster.all()) {
            if (record.lastDailyResetDay == day) {
                continue;
            }
            if (record.lastDailyResetDay >= 0) {
                PerceptionService.applyDailyDecay(record);
            }
            record.dailyPet = false;
            record.dailySleep = false;
            record.dailySocial = false;
            record.dailyFavoriteFavor = false;
            record.dailyCakeFavor = false;
            record.lastDailyResetDay = day;
            dirty = true;
            if (record.lastInteractedBenevolentPlayerName != null
                    && !record.lastInteractedBenevolentPlayerName.isEmpty()) {
                namesToRefresh.add(record.lastInteractedBenevolentPlayerName);
            }
            namesToRefresh.addAll(FavorService.benevolentNames(record));
        }
        if (dirty) {
            roster.setDirty();
            MisakaComputeIndex.get(server).markDirty();
            // Refresh only players who actually hold favor/privilege on day-changed sisters.
            MisakaComputeContribution.refreshCpForNames(server, namesToRefresh);
        }
    }
}
