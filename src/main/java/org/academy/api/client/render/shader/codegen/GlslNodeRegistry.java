package org.academy.api.client.render.shader.codegen;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点类型 id → GLSL 生成器 的目录。
 */
public final class GlslNodeRegistry {
    private final Map<String, GlslNodeGenerator> generators = new LinkedHashMap<>();

    public void register(String typeId, GlslNodeGenerator generator) {
        generators.put(typeId, generator);
    }

    @Nullable
    public GlslNodeGenerator find(String typeId) {
        return generators.get(typeId);
    }
}
