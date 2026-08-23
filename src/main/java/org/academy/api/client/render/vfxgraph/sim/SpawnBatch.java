package org.academy.api.client.render.vfxgraph.sim;

/**
 * 本帧新 spawn 粒子的索引批次（M24）：{@code [start, end)} 左闭右开。
 *
 * <p>由 spawn 块在批量生成后记录（{@code SimContext.emitBatch}），经 flow 边传给下游
 * init 块；init 只处理传入批次，替代旧 {@code SimContext.spawnStart} 单点耦合。</p>
 */
public record SpawnBatch(int start, int end) {
    public SpawnBatch {
        if (end < start) {
            throw new IllegalArgumentException("spawn batch end < start: " + start + " > " + end);
        }
    }

    public int size() {
        return end - start;
    }
}
