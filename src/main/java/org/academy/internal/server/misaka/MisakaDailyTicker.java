package org.academy.internal.server.misaka;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.entity.misaka.MisakaDayTime;
import org.academy.internal.common.world.entity.misaka.perception.PerceptionService;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MisakaDailyTicker {
    private MisakaDailyTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        int day = MisakaDayTime.dayIndex(server.overworld());
        var roster = MisakaSisterRoster.get(server);
        boolean dirty = false;
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
        }
        if (dirty) {
            roster.setDirty();
            for (var record : roster.all()) {
                MisakaComputeContribution.refreshCpForRecord(server, record);
            }
        }
    }
}
