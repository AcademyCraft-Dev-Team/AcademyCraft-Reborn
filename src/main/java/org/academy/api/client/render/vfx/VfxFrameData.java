package org.academy.api.client.render.vfx;

import org.jspecify.annotations.Nullable;

import java.util.*;

public final class VfxFrameData implements VfxSink {
    private final Map<Class<?>, List<VfxRenderData>> buckets = new HashMap<>();

    @Override
    public <T extends VfxRenderData> void push(T data) {
        Objects.requireNonNull(data, "data");
        buckets.computeIfAbsent(data.getClass(), _ -> new ArrayList<>()).add(data);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends VfxRenderData> List<T> get(Class<T> type) {
        return (List<T>) buckets.get(type);
    }

    public boolean isEmpty() {
        for (var bucket : buckets.values()) {
            if (!bucket.isEmpty()) return false;
        }
        return true;
    }

    public void clear() {
        for (var bucket : buckets.values()) {
            bucket.clear();
        }
    }
}
