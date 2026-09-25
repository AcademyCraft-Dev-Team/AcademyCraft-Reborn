package org.academy.api.client.render.shader.codegen;

import org.academy.api.client.render.graph.model.GraphNode;

import java.util.Map;

/**
 * 节点 GLSL 代码生成器（契约）。给定节点实例与各输入端口表达式，返回各输出端口表达式。
 */
public interface GlslNodeGenerator {
    Map<String, Expr> generate(GraphNode node, Map<String, Expr> inputs, GlslGenContext ctx);
}
