package org.academy.api.client.render.graph.type;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 默认隐式转换（契约实现）。数值/颜色/布尔/整数族内可互转，向量间按分量填充/截断广播。
 *
 * <p>采样器、曲线、渐变、网格不与任何类型隐式互转（仅自身）。</p>
 */
public final class TypeConversions implements TypeConverter {
    public static final TypeConversions INSTANCE = new TypeConversions();

    private TypeConversions() {
    }

    @Override
    public boolean canConvert(ValueType from, ValueType to) {
        if (from == to) return true;
        if (isNumericish(from) && isNumericish(to)) return true;
        return from == ValueType.TIME && to == ValueType.FLOAT;
    }

    @Override
    public Value convert(Value value, ValueType to) {
        if (value.type() == to) return value;
        if (!canConvert(value.type(), to)) {
            throw new IllegalArgumentException("cannot convert " + value.type() + " -> " + to);
        }
        return switch (to) {
            case FLOAT -> Value.of(toFloat(value));
            case INT -> Value.of(toInt(value));
            case BOOL -> Value.of(toBool(value));
            case VEC2 -> Value.of(toVec2(value));
            case VEC3 -> Value.of(toVec3(value));
            case VEC4 -> Value.of(toVec4(value));
            case COLOR -> toColor(value);
            default -> throw new IllegalArgumentException("unsupported target " + to);
        };
    }

    private static boolean isNumericish(ValueType t) {
        return switch (t) {
            case FLOAT, INT, BOOL, VEC2, VEC3, VEC4, COLOR -> true;
            default -> false;
        };
    }

    private static float toFloat(Value v) {
        return switch (v.type()) {
            case FLOAT -> v.asFloat();
            case INT -> v.asInt();
            case BOOL -> v.asBool() ? 1f : 0f;
            case VEC2 -> v.asVec2().x;
            case VEC3 -> v.asVec3().x;
            case VEC4, COLOR -> v.asVec4().x;
            case TIME -> v.asFloat();
            default -> throw new IllegalArgumentException("not numeric: " + v.type());
        };
    }

    private static int toInt(Value v) {
        return (int) toFloat(v);
    }

    private static boolean toBool(Value v) {
        return toFloat(v) != 0f;
    }

    private static Vector2f toVec2(Value v) {
        return switch (v.type()) {
            case FLOAT, INT, BOOL, TIME -> {
                float f = toFloat(v);
                yield new Vector2f(f, f);
            }
            case VEC2 -> v.asVec2();
            case VEC3 -> {
                Vector3f x = v.asVec3();
                yield new Vector2f(x.x, x.y);
            }
            case VEC4, COLOR -> {
                Vector4f x = v.asVec4();
                yield new Vector2f(x.x, x.y);
            }
            default -> throw new IllegalArgumentException("not numeric: " + v.type());
        };
    }

    private static Vector3f toVec3(Value v) {
        return switch (v.type()) {
            case FLOAT, INT, BOOL, TIME -> {
                float f = toFloat(v);
                yield new Vector3f(f, f, f);
            }
            case VEC2 -> {
                Vector2f x = v.asVec2();
                yield new Vector3f(x.x, x.y, 0f);
            }
            case VEC3 -> v.asVec3();
            case VEC4, COLOR -> {
                Vector4f x = v.asVec4();
                yield new Vector3f(x.x, x.y, x.z);
            }
            default -> throw new IllegalArgumentException("not numeric: " + v.type());
        };
    }

    private static Vector4f toVec4(Value v) {
        return switch (v.type()) {
            case FLOAT, INT, BOOL, TIME -> {
                float f = toFloat(v);
                yield new Vector4f(f, f, f, f);
            }
            case VEC2 -> {
                Vector2f x = v.asVec2();
                yield new Vector4f(x.x, x.y, 0f, 1f);
            }
            case VEC3 -> {
                Vector3f x = v.asVec3();
                yield new Vector4f(x.x, x.y, x.z, 1f);
            }
            case VEC4 -> v.asVec4();
            case COLOR -> v.asColor();
            default -> throw new IllegalArgumentException("not numeric: " + v.type());
        };
    }

    private static Value toColor(Value v) {
        Vector4f x = toVec4(v);
        return Value.color(x.x, x.y, x.z, x.w);
    }
}
