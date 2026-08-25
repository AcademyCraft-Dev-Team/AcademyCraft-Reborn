package org.academy.api.client.render.graph.type;

/**
 * 隐式类型转换规则（契约）。
 *
 * <p>端口连接与常量赋值时的自动转换（如 float→vec4 广播）。</p>
 */
public interface TypeConverter {
    boolean canConvert(ValueType from, ValueType to);

    Value convert(Value value, ValueType to);
}
