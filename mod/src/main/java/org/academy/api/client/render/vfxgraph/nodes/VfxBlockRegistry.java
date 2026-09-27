package org.academy.api.client.render.vfxgraph.nodes;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 块类型 id → 容器块工厂 的目录（M24）。
 */
public final class VfxBlockRegistry {
    private final Map<String, VfxBlockFactory> factories = new LinkedHashMap<>();

    public void register(String typeId, VfxBlockFactory factory) {
        factories.put(typeId, factory);
    }

    @Nullable
    public VfxBlockFactory find(String typeId) {
        return factories.get(typeId);
    }
}
