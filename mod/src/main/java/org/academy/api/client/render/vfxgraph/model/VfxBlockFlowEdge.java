package org.academy.api.client.render.vfxgraph.model;

/**
 * 块级批次 flow 边（M28b）：spawn 块 → init 块的批次配对。
 *
 * <p>与 context 级 flow（{@link VfxFlowEdge}）互补：当同一 INITIALIZE context 内有多个 init 块、
 * 且需把不同 spawn 块的批次分给不同 init 块时，用本边精确指定「该 init 块处理哪个 spawn 块的批次」。
 * 若某 init 块无块级 flow 上游，则回退到其所在 context 的 context 级上游批次。</p>
 *
 * @param fromBlockId 上游 spawn 块 id
 * @param toBlockId   下游 init 块 id
 */
public record VfxBlockFlowEdge(String fromBlockId, String toBlockId) {
}
