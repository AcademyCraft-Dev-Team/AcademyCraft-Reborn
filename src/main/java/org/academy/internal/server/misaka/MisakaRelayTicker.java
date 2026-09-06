package org.academy.internal.server.misaka;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MisakaRelayTicker {
    private MisakaRelayTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        MisakaRelayRegistry.get(server).endTick(server);
    }
}
