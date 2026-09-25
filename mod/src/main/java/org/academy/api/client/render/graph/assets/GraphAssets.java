package org.academy.api.client.render.graph.assets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.serialize.GraphCodec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GraphAssets {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private final GraphCodec codec;
    private final Gson gson = new Gson();
    private final Map<String, Graph> cache = new LinkedHashMap<>();

    public GraphAssets(GraphCodec codec) {
        this.codec = codec;
    }

    public @Nullable Graph load(String key, JsonObject json) {
        try {
            var graph = codec.decode(json);
            cache.put(key, graph);
            return graph;
        } catch (Exception exception) {
            LOGGER.error("Unable to decode vfx graph asset: {}", key, exception);
            return null;
        }
    }

    public @Nullable Graph load(String key, String jsonText) {
        return load(key, gson.fromJson(jsonText, JsonObject.class));
    }

    public @Nullable Graph load(Path file) throws IOException {
        return load(file.toAbsolutePath().toString(), Files.readString(file));
    }

    @Nullable
    public Graph get(String key) {
        return cache.get(key);
    }

    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    public void invalidate(String key) {
        cache.remove(key);
    }

    public void invalidateAll() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
