package org.academy.api.client.render.vfxgraph.operator;

import org.academy.api.client.render.vfxgraph.model.VfxOperatorNode;

import java.util.Map;

/**
 * 算子工厂（M25）：由算子实例（属性 + 输入端口求值器）构建 [VfxOperator]。
 *
 * <p>{@code inputs} 为该算子输入端口 → 上游算子求值器（来自数据边），
 * 算子内部可引用（如数学算子的 a/b、曲线算子的 t）。</p>
 */
public interface VfxOperatorFactory {
    VfxOperator create(VfxOperatorNode node, Map<String, VfxOperator> inputs);
}
