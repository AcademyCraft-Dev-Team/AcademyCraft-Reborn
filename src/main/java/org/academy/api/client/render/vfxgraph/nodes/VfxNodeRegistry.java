package org.academy.api.client.render.vfxgraph.nodes;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 节点类型 id → VFX 节点工厂 的目录。
 */
public final class VfxNodeRegistry {
    private final Map<String, VfxNodeFactory> factories = new LinkedHashMap<>();

    public void register(String typeId, VfxNodeFactory factory) {
        factories.put(typeId, factory);
    }

    @Nullable
    public VfxNodeFactory find(String typeId) {
        return factories.get(typeId);
    }
}
