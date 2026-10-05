package org.academy.api.client.render.vfxgraph.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.academy.AcademyCraft;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@EventBusSubscriber(Dist.CLIENT)
public final class VfxGraphAssetLoader implements PreparableReloadListener {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    public static final VfxGraphAssetLoader INSTANCE = new VfxGraphAssetLoader();

    private static final String DIRECTORY = "vfxgraph";
    private static final Gson GSON = new Gson();

    private VfxGraphAssetLoader() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddClientReloadListenersEvent event) {
        event.addListener(AcademyCraft.academy("vfx_graph_assets"), INSTANCE);
    }

    @Override
    public CompletableFuture<Void> reload(
            SharedState currentReload,
            Executor taskExecutor,
            PreparationBarrier preparationBarrier,
            Executor reloadExecutor
    ) {
        var resources = currentReload.resourceManager().listResources(DIRECTORY,
                path -> path.getPath().endsWith(".json") && !path.getPath().endsWith(".editor.json"));
        return CompletableFuture.supplyAsync(() -> {
                    var parsed = new LinkedHashMap<Identifier, JsonObject>();
                    for (var entry : resources.entrySet()) {
                        try (var reader = entry.getValue().openAsReader()) {
                            parsed.put(entry.getKey(), GSON.fromJson(reader, JsonObject.class));
                        } catch (Exception exception) {
                            LOGGER.error("Unable to parse vfx graph asset: {}", entry.getKey(), exception);
                        }
                    }
                    return parsed;
                }, taskExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAccept(VfxGraphAssetLoader::applyOnMainThread);
    }

    private static void applyOnMainThread(Map<Identifier, JsonObject> parsed) {
        if (parsed.isEmpty()) return;

        var manager = VfxGraphManager.INSTANCE;
        manager.invalidateAll();
        for (var entry : parsed.entrySet()) {
            var id = entry.getKey();
            if (id.getPath().endsWith(".json")) {
                id = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - 5));
            }
            manager.registerAsset(id, entry.getValue());
        }
    }
}
