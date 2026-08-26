package org.academy.api.client.render.shader.codegen;

import org.academy.api.client.render.graph.type.Curve;
import org.academy.api.client.render.graph.type.Gradient;

/**
 * 节点代码生成上下文（契约）。提供黑板参数 uniform 名解析与辅助函数注册。
 */
public interface GlslGenContext {
    String parameterUniform(String parameterId);

    void addHelper(String functionSource);

    /**
     * 返回参数对应的曲线数据；参数非 CURVE 或不存在返回 null。
     */
    default Curve curve(String parameterId) {
        return null;
    }

    /**
     * 返回参数对应的渐变数据；参数非 GRADIENT 或不存在返回 null。
     */
    default Gradient gradient(String parameterId) {
        return null;
    }

    /**
     * 返回纹理标识对应的 sampler uniform 名（如 {@code Sampler0}）。
     * 未在图中规划的标识抛 {@link IllegalStateException}。
     */
    default String samplerName(String identifier) {
        throw new IllegalStateException("no sampler binding for texture: " + identifier);
    }
}
