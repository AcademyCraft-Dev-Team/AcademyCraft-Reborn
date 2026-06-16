package org.academy.api.client.gui.msdf.font;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.academy.AcademyCraft;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@EventBusSubscriber(Dist.CLIENT)
public enum FontLoader implements PreparableReloadListener {
    INSTANCE;

    @Override
    public CompletableFuture<Void> reload(
            SharedState currentReload,
            Executor taskExecutor,
            PreparationBarrier preparationBarrier,
            Executor reloadExecutor
    ) {
        var manager = currentReload.resourceManager();
        var resources = manager.listResources("fonts", MsdfFontService::isFont).keySet();

        MsdfFontService.setFontSearchOrder(new ArrayList<>(resources));

        var futures = resources.stream()
                .map(resource -> loadFont(resource, taskExecutor))
                .toArray(CompletableFuture<?>[]::new);
        return CompletableFuture.allOf(futures)
                .thenCompose(preparationBarrier::wait);
    }

    private static CompletableFuture<Void> loadFont(Identifier resource, Executor taskExecutor) {
        return CompletableFuture.runAsync(() -> MsdfFontService.loadFont(resource), taskExecutor);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddClientReloadListenersEvent event) {
        event.addListener(AcademyCraft.academy("font_loader"), INSTANCE);
    }
}
