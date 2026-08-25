package org.academy.api.client.render.graph.compile;

import org.academy.api.client.render.graph.model.Graph;

/**
 * 图编译器（契约）。拓扑排序、DAG 执行计划、常量折叠。
 *
 * <p>非法图（类型不匹配/环/孤儿输出）应抛异常；实现应先经校验器诊断。</p>
 */
public interface GraphCompiler {
    CompiledGraph compile(Graph graph);
}
