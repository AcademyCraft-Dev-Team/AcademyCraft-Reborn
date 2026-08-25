package org.academy.api.client.render.graph.type;

/**
 * 端口/参数的值类型（契约，见 MODULES.md MOD-01）。
 *
 * <p>Shader 图主要使用标量/向量/采样器；VFX 图额外使用曲线/渐变/网格等。</p>
 */
public enum ValueType {
    FLOAT,
    VEC2,
    VEC3,
    VEC4,
    COLOR,
    BOOL,
    INT,
    SAMPLER,
    TIME,
    CURVE,
    GRADIENT,
    MESH,
    STRING
}
