package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimContext;

/**
 * 块输入端口值源（M25）：数据流绑定经此向块提供端口值。
 *
 * <p>块工厂在构建 SimNode 时经 {@link #eval} 查询端口——若有算子驱动则返回算子求值结果
 * （可逐粒子），否则返回 null（块回退到属性默认值）。</p>
 */
public interface PortValueSource {
    /**
     * 求值块输入端口值；无数据流绑定返回 null。
     *
     * @param portId        端口 id（如 {@code vx}）
     * @param particleIndex 当前粒子索引
     * @param buffer        粒子缓冲
     * @param ctx           模拟帧上下文
     */
    Value eval(String portId, int particleIndex, ParticleBuffer buffer, SimContext ctx);

    /**
     * 无绑定的空实现：所有端口恒返回 null（块用属性默认值）。
     */
    static PortValueSource none() {
        return (portId, particleIndex, buffer, ctx) -> null;
    }
}
