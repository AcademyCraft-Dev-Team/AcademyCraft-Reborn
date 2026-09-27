package org.academy.api.client.render.shader.codegen;

import org.academy.api.client.render.graph.type.ValueType;

/**
 * GLSL 表达式 IR：一段 GLSL 源码字符串 + 其图值类型。
 */
public record Expr(String code, ValueType type) {
}
