package org.academy.api.client.render.vfxgraph.operator;

import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimContext;

/**
 * 算子求值上下文（M25）：当前粒子索引 + 缓冲 + 模拟帧上下文。
 *
 * <p>{@code particleIndex == -1} 表示「非粒子上下文」（编译期折叠/参数缺省求值），
 * attr-read 算子在此上下文应返回默认值而非读取缓冲。</p>
 */
public record OperatorContext(ParticleBuffer buffer, int particleIndex, SimContext simContext) {
    public static OperatorContext nonParticle(SimContext simContext) {
        return new OperatorContext(null, -1, simContext);
    }
}
