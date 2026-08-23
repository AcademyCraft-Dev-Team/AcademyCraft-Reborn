package org.academy.api.client.render.vfxgraph.operator;

import org.academy.api.client.render.graph.type.Value;

/**
 * 算子求值器（M25）：在给定粒子上下文下求值输出端口值。
 *
 * <p>常量/参数/数学等不依赖粒子的算子可在编译期折叠；attr-read 算子逐粒子求值。</p>
 */
public interface VfxOperator {
    Value eval(OperatorContext ctx);
}
