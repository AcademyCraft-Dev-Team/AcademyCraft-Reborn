package org.academy.api.client.render.vfxgraph.model;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.model.Port;

/**
 * VFX 容器图节点（块或算子）的公共视图（M23）：id/type/属性/端口。
 *
 * <p>{@link VfxBlock}（context 内块）与 {@link VfxOperatorNode}（自由算子）都实现本接口，
 * 数据边（{@link VfxDataEdge}）可统一引用二者。端口由 {@link org.academy.api.client.render.graph.registry.NodeType}
 * 派生（目录是端口规格的唯一事实源，与核心 {@code GraphNode} 同约定）。</p>
 */
public interface VfxNode {
    String id();

    String type();

    Map<String, String> properties();

    List<Port> ports();
}
