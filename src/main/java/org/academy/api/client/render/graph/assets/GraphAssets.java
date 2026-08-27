package org.academy.api.client.render.graph.assets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.serialize.GraphCodec;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图资产加载/缓存。以字符串键缓存解码后的 [Graph]，支持按键失效（热重载）。
 *
 * <p>版本迁移由 [GraphCodec] 内部完成（{@code JsonGraphCodec} 携带迁移链）。</p>
 */
public final class GraphAssets {
    private final GraphCodec codec;
    private final Gson gson = new Gson();
    private final Map<String, Graph> cache = new LinkedHashMap<>();

    public GraphAssets(GraphCodec codec) {
        this.codec = codec;
    }

    /**
     * 解码并缓存；解码失败（如误入的非图 JSON）记日志并返回 {@code null}，不中断资源重载。
     */
    public @Nullable Graph load(String key, JsonObject json) {
        try {
            var graph = codec.decode(json);
            cache.put(key, graph);
            return graph;
        } catch (Exception exception) {
            AcademyCraft.getLogger().error("Unable to decode vfx graph asset: {}", key, exception);
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
