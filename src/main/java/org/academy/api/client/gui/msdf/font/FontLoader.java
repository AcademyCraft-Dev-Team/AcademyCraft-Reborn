package org.academy.api.client.gui.msdf.font;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.academy.AcademyCraft.academy;

@EventBusSubscriber(Dist.CLIENT)
public class FontLoader implements PreparableReloadListener {
    @SubscribeEvent
    public static void onAddReloadListener(AddClientReloadListenersEvent event) {
        event.addListener(academy("font_loader"), new FontLoader());
    }

    @Override
    public CompletableFuture<Void> reload(
            SharedState currentReload,
            Executor taskExecutor,
            PreparationBarrier preparationBarrier,
            Executor reloadExecutor
    ) {
        var manager = currentReload.resourceManager();
        var resources = manager.listResources("fonts", MsdfFontService::isFont).keySet();

        MsdfFontService.getInstance().setFontSearchOrder(new ArrayList<>(resources));

        var futuresStream = resources.stream()
                .map(resource -> loadFont(resource, taskExecutor));
        return CompletableFuture
                .allOf(futuresStream.toArray(CompletableFuture[]::new))
                .thenCompose(preparationBarrier::wait);
    }

    private CompletableFuture<Void> loadFont(Identifier resource, Executor taskExecutor) {
        return CompletableFuture.runAsync(
                () -> MsdfFontService.getInstance().loadFont(resource), taskExecutor
        );
    }
}