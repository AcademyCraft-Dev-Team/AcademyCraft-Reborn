package org.academy.api.client.render.vfxgraph.sim;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VfxLayers {
    @FunctionalInterface
    public interface Registry {
        void register(String name, byte id);
    }

    private static final Map<String, Byte> IDS = new ConcurrentHashMap<>();

    private VfxLayers() {
    }

    public static void register(String name, byte id) {
        if (name == null || name.isEmpty() || id == 0) {
            throw new IllegalArgumentException("Invalid particle layer");
        }
        var previous = IDS.putIfAbsent(name, id);
        if (previous != null && previous != id) {
            throw new IllegalStateException("Duplicate particle layer: " + name);
        }
    }

    public static byte id(@Nullable String name) {
        if (name == null || name.isEmpty()) return 0;
        var id = IDS.get(name);
        return id == null ? 0 : id;
    }

    public static void clear() {
        IDS.clear();
    }
}
