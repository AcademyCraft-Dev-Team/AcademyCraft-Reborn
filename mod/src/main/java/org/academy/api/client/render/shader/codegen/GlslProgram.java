package org.academy.api.client.render.shader.codegen;

/**
 * 生成结果：顶点 + 片段着色器源码。
 */
public record GlslProgram(String vertexSource, String fragmentSource) {
}
