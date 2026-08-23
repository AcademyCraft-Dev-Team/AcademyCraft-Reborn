package org.academy.api.client.render.vfxgraph.model;

import org.academy.api.client.render.graph.type.ValueType;

/**
 * 粒子属性（M23）：attr-read 算子的读取目标，映射 {@code ParticleBuffer} 的字段。
 *
 * <p>每条属性带对应的 {@link ValueType}，供端口类型派生与类型转换校验。</p>
 */
public enum ParticleAttribute {
    POSITION(ValueType.VEC3, 3),
    VELOCITY(ValueType.VEC3, 3),
    SIZE(ValueType.FLOAT, 1),
    COLOR(ValueType.COLOR, 4),
    ALPHA(ValueType.FLOAT, 1),
    AGE(ValueType.FLOAT, 1),
    LIFETIME(ValueType.FLOAT, 1),
    ROTATION(ValueType.FLOAT, 1),
    MASS(ValueType.FLOAT, 1),
    SEED(ValueType.FLOAT, 1),
    LAYER(ValueType.INT, 1);

    private final ValueType valueType;
    /** 通道数（1/3/4），供逐通道读取。 */
    private final int channels;

    ParticleAttribute(ValueType valueType, int channels) {
        this.valueType = valueType;
        this.channels = channels;
    }

    public ValueType valueType() {
        return valueType;
    }

    public int channels() {
        return channels;
    }
}
