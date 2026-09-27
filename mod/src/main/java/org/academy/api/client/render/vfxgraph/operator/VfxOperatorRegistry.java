package org.academy.api.client.render.vfxgraph.operator;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 算子类型 id → 算子工厂 的目录（M25）。
 */
public final class VfxOperatorRegistry {
    private final Map<String, VfxOperatorFactory> factories = new LinkedHashMap<>();

    public void register(String typeId, VfxOperatorFactory factory) {
        factories.put(typeId, factory);
    }

    @Nullable
    public VfxOperatorFactory find(String typeId) {
        return factories.get(typeId);
    }
}
