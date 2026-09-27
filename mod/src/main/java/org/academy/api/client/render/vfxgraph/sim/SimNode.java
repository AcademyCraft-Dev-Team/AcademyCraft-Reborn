package org.academy.api.client.render.vfxgraph.sim;

/**
 * 模拟节点（契约）。逐帧对共享 [ParticleBuffer] 执行：spawn 增粒，update 逐粒修改。
 */
public interface SimNode {
    void step(ParticleBuffer buffer, SimContext ctx);
}
