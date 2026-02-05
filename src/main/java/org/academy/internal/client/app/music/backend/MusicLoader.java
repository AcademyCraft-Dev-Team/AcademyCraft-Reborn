package org.academy.internal.client.app.music.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.academy.AcademyCraft;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.academy.AcademyCraft.academy;

@EventBusSubscriber(Dist.CLIENT)
public final class MusicLoader implements PreparableReloadListener {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    public static final TypeToken<Map<String, MusicData>> MUSIC_DATA_MAP_TYPE = new TypeToken<>() {
    };

    private static final Gson GSON = new GsonBuilder().create();
    private static final String FILE_PATH = "musics/music_player.json";

    @SubscribeEvent
    public static void onAddReloadListener(AddClientReloadListenersEvent event) {
        event.addListener(academy("music_loader"), new MusicLoader());
    }

    @Override
    public CompletableFuture<Void> reload(
            SharedState currentReload,
            Executor taskExecutor,
            PreparationBarrier preparationBarrier,
            Executor reloadExecutor
    ) {
        var manager = currentReload.resourceManager();
        var future = CompletableFuture.supplyAsync(() -> loadAllMusicData(manager), taskExecutor);
        return future.thenCompose(preparationBarrier::wait).thenAcceptAsync(this::applyToBackend, reloadExecutor);
    }

    private Map<String, MusicData> loadAllMusicData(ResourceManager manager) {
        var combinedMap = new HashMap<String, MusicData>();
        for (var namespace : manager.getNamespaces()) loadFromNamespace(manager, namespace, combinedMap);
        return combinedMap;
    }

    private void loadFromNamespace(ResourceManager manager, String namespace, Map<String, MusicData> destination) {
        var resources = manager.getResourceStack(AcademyCraft.custom(namespace, FILE_PATH));
        for (var resource : resources) parseResource(resource, destination);
    }

    private void parseResource(Resource resource, Map<String, MusicData> destination) {
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            var map = GSON.fromJson(reader, MUSIC_DATA_MAP_TYPE);
            destination.putAll(map);
        } catch (Exception e) {
            LOGGER.error(
                    "Failed to parse '{}' from resource pack '{}'",
                    FILE_PATH, resource.sourcePackId(), e
            );
        }
    }

    private void applyToBackend(Map<String, MusicData> data) {
        MusicPlayerBackend.getInstance().updatePlaylistFromData(data, "All Resource Packs");
    }
}