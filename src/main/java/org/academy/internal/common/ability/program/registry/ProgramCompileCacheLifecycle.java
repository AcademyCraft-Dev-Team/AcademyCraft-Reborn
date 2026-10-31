package org.academy.internal.common.ability.program.registry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.academy.AcademyCraft;

/** Package-private handlers are supported by NeoForge's setAccessible/unreflect subscriber path. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
final class ProgramCompileCacheLifecycle {
    private ProgramCompileCacheLifecycle() {}

    static void clearAll() { AbilityProgramDefinitions.all().forEach(AbilityProgramDefinition::clearCompilationCache); }

    @SubscribeEvent
    static void serverStopped(ServerStoppedEvent event) { clearAll(); }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
    static final class Client {
        private Client() {}

        @SubscribeEvent
        static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { clearAll(); }

        @SubscribeEvent
        static void stopped(ClientStoppedEvent event) { clearAll(); }
    }
}
