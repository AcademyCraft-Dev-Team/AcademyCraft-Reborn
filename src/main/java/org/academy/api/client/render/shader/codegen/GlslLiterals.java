package org.academy.api.client.render.shader.codegen;

import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;

/**
 * 图值 → GLSL 字面量表达式。
 */
public final class GlslLiterals {
    private GlslLiterals() {
    }

    public static Expr of(Value v) {
        return switch (v.type()) {
            case FLOAT -> new Expr(floatLit(v.asFloat()), ValueType.FLOAT);
            case INT -> new Expr(Integer.toString(v.asInt()), ValueType.INT);
            case BOOL -> new Expr(Boolean.toString(v.asBool()), ValueType.BOOL);
            case VEC2 -> {
                var x = v.asVec2();
                yield new Expr("vec2(" + floatLit(x.x) + ", " + floatLit(x.y) + ")", ValueType.VEC2);
            }
            case VEC3 -> {
                var x = v.asVec3();
                yield new Expr("vec3(" + floatLit(x.x) + ", " + floatLit(x.y) + ", " + floatLit(x.z) + ")",
                        ValueType.VEC3);
            }
            case VEC4, COLOR -> {
                var x = v.asVec4();
                yield new Expr("vec4(" + floatLit(x.x) + ", " + floatLit(x.y) + ", " + floatLit(x.z) + ", "
                        + floatLit(x.w) + ")", ValueType.VEC4);
            }
            case TIME -> new Expr(floatLit(v.asFloat()), ValueType.FLOAT);
            default -> throw new IllegalArgumentException("no GLSL literal for " + v.type());
        };
    }

    private static String floatLit(float f) {
        return f == Math.floor(f) && !Float.isInfinite(f) && Math.abs(f) < 1e6f ? (int) f + ".0" : Float.toString(f);
    }
}
