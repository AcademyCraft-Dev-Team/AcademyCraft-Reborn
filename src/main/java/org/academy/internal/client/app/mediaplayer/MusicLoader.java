package org.academy.internal.client.app.mediaplayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.academy.AcademyCraft;
import org.jetbrains.annotations.NotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class MusicLoader implements PreparableReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final String FILE_PATH = "musics/music_player.json";

    @Override
    public @NotNull CompletableFuture<Void> reload(
            PreparationBarrier barrier, @NotNull ResourceManager resourceManager,
            @NotNull ProfilerFiller preparationsProfiler, @NotNull ProfilerFiller reloadProfiler,
            @NotNull Executor backgroundExecutor, @NotNull Executor gameExecutor
    ) {
        var future = CompletableFuture.supplyAsync(() -> {
            var combinedMap = new HashMap<String, MusicData>();

            for (var namespace : resourceManager.getNamespaces()) {
                try {
                    for (var resource : resourceManager.getResourceStack(AcademyCraft.getResourceLocation(namespace, FILE_PATH))) {
                        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                            var map = GSON.<Map<String, MusicData>>fromJson(reader, MediaPlayerBackend.MUSIC_DATA_MAP_TYPE);
                            if (map != null) {
                                combinedMap.putAll(map);
                            }
                        } catch (Exception e) {
                            AcademyCraft.LOGGER.error("Failed to parse '{}' from resource pack '{}'", FILE_PATH, resource.sourcePackId(), e);
                        }
                    }
                } catch (Exception e) {
                    AcademyCraft.LOGGER.error("Failed to read '{}' in namespace '{}'", FILE_PATH, namespace, e);
                }
            }
            return combinedMap;
        }, backgroundExecutor);

        return future.thenCompose(barrier::wait).thenAcceptAsync(data -> MediaPlayerBackend.updatePlaylistFromData(data, "All Resource Packs"), gameExecutor);
    }
}