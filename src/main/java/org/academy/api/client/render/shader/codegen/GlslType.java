package org.academy.api.client.render.shader.codegen;

import org.academy.api.client.render.graph.type.ValueType;

/**
 * GLSL 类型映射与表达式转换。
 *
 * <p>标量→向量广播、向量→向量升/降维均使用 GLSL 构造器与 swizzle；非法转换抛异常。</p>
 */
public final class GlslType {
    private GlslType() {
    }

    public static String of(ValueType t) {
        return switch (t) {
            case FLOAT, TIME -> "float";
            case INT -> "int";
            case BOOL -> "bool";
            case VEC2 -> "vec2";
            case VEC3 -> "vec3";
            case VEC4, COLOR -> "vec4";
            case SAMPLER -> "sampler2D";
            case CURVE, GRADIENT, MESH, STRING -> throw new IllegalArgumentException("not a GLSL type: " + t);
        };
    }

    /** 把表达式转换到目标类型，插入必要的构造器/swizzle。 */
    public static Expr convert(Expr e, ValueType to) {
        if (e.type() == to) return e;
        return switch (to) {
            case FLOAT, TIME -> {
                if (isVector(e.type())) throw new IllegalArgumentException("cannot convert " + e.type() + " -> " + to);
                yield new Expr("float(" + e.code() + ")", ValueType.FLOAT);
            }
            case INT -> new Expr("int(" + e.code() + ")", ValueType.INT);
            case BOOL -> new Expr("bool(" + e.code() + ")", ValueType.BOOL);
            case VEC2 -> {
                if (isVector(e.type())) yield new Expr(e.code() + ".xy", ValueType.VEC2);
                yield new Expr("vec2(" + e.code() + ")", ValueType.VEC2);
            }
            case VEC3 -> {
                if (e.type() == ValueType.VEC4 || e.type() == ValueType.COLOR) {
                    yield new Expr(e.code() + ".xyz", ValueType.VEC3);
                }
                if (e.type() == ValueType.VEC2) {
                    yield new Expr("vec3(" + e.code() + ", 0.0)", ValueType.VEC3);
                }
                yield new Expr("vec3(" + e.code() + ")", ValueType.VEC3);
            }
            case VEC4, COLOR -> {
                if (e.type() == ValueType.VEC3) {
                    yield new Expr("vec4(" + e.code() + ", 1.0)", ValueType.VEC4);
                }
                if (e.type() == ValueType.VEC2) {
                    yield new Expr("vec4(" + e.code() + ", 0.0, 1.0)", ValueType.VEC4);
                }
                yield new Expr("vec4(" + e.code() + ")", ValueType.VEC4);
            }
            default -> throw new IllegalArgumentException("cannot convert " + e.type() + " -> " + to);
        };
    }

    private static boolean isVector(ValueType t) {
        return t == ValueType.VEC2 || t == ValueType.VEC3 || t == ValueType.VEC4 || t == ValueType.COLOR;
    }
}
