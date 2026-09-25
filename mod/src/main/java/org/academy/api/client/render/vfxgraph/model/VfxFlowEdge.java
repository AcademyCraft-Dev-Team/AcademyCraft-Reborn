package org.academy.api.client.render.vfxgraph.model;

/**
 * Context 间 flow 边（M23）：把上游 context 的「本帧新粒子批次」传给下游 context。
 * 下游 init 块只处理经 flow 边传入的批次，替代旧 `SimContext.spawnStart` 单点耦合。
 *
 * @param fromContextId 上游 context id
 * @param toContextId   下游 context id
 */
public record VfxFlowEdge(String fromContextId, String toContextId) {
}
