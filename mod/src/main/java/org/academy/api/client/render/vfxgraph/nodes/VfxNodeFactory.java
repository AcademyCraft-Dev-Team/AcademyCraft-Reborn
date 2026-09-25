package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

/**
 * VFX 节点工厂（契约）。由节点实例（读取其属性）构建 [SimNode]。
 */
public interface VfxNodeFactory {
    SimNode create(GraphNode node);
}
