package org.academy.api.client.render.vfxgraph.model;

import java.util.List;

/**
 * VFX context 容器（M23）。固定阶段（{@link VfxContextType}）+ 画布坐标 + 内部块列表。
 * context 间经 flow 边（{@link VfxFlowEdge}）串联成流水线。
 *
 * @param id     实例 id（全局唯一，供 flow 边引用）
 * @param type   阶段类型
 * @param name   显示名（可选，默认取阶段名）
 * @param blocks 内部块（按列表顺序执行）
 * @param x      画布坐标
 * @param y      画布坐标
 */
public record VfxContext(
        String id,
        VfxContextType type,
        String name,
        List<VfxBlock> blocks,
        float x,
        float y
) {
    public VfxContext {
        blocks = List.copyOf(blocks);
    }

    /** 显示名：显式 name 优先，否则阶段名。 */
    public String displayName() {
        return name == null || name.isBlank() ? type.name() : name;
    }
}
