package org.academy.api.client.render.graph.type;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 值（契约 + 实现）。不可变，按 {@link ValueType} 区分载体。
 *
 * <p>sealed 接口 + record 实现提供结构相等与类型化访问。曲线/渐变/网格等复合类型
 * 在 VFX 图（M5）中使用，Shader 图主要使用标量/向量/采样器。</p>
 */
public sealed interface Value permits Value.FloatVal, Value.Vec2Val, Value.Vec3Val, Value.Vec4Val,
        Value.ColorVal, Value.BoolVal, Value.IntVal, Value.SamplerVal, Value.TimeVal, Value.CurveVal,
        Value.GradientVal, Value.MeshVal, Value.StringVal {

    ValueType type();

    static Value of(float v) {
        return new FloatVal(v);
    }

    static Value of(int v) {
        return new IntVal(v);
    }

    static Value of(boolean v) {
        return new BoolVal(v);
    }

    static Value of(Vector2f v) {
        return new Vec2Val(v.x, v.y);
    }

    static Value of(Vector3f v) {
        return new Vec3Val(v.x, v.y, v.z);
    }

    static Value of(Vector4f v) {
        return new Vec4Val(v.x, v.y, v.z, v.w);
    }

    static Value color(float r, float g, float b, float a) {
        return new ColorVal(r, g, b, a);
    }

    static Value color(Vector4f rgba) {
        return new ColorVal(rgba.x, rgba.y, rgba.z, rgba.w);
    }

    static Value sampler(String path) {
        return new SamplerVal(path);
    }

    static Value curve(Curve curve) {
        return new CurveVal(curve);
    }

    static Value gradient(Gradient gradient) {
        return new GradientVal(gradient);
    }

    static Value mesh(String path) {
        return new MeshVal(path);
    }

    static Value string(String value) {
        return new StringVal(value);
    }

    default float asFloat() {
        throw new ClassCastException("not a float value: " + type());
    }

    default int asInt() {
        throw new ClassCastException("not an int value: " + type());
    }

    default boolean asBool() {
        throw new ClassCastException("not a bool value: " + type());
    }

    default Vector2f asVec2() {
        throw new ClassCastException("not a vec2 value: " + type());
    }

    default Vector3f asVec3() {
        throw new ClassCastException("not a vec3 value: " + type());
    }

    default Vector4f asVec4() {
        throw new ClassCastException("not a vec4 value: " + type());
    }

    default Vector4f asColor() {
        throw new ClassCastException("not a color value: " + type());
    }

    default String asSampler() {
        throw new ClassCastException("not a sampler value: " + type());
    }

    default Curve asCurve() {
        throw new ClassCastException("not a curve value: " + type());
    }

    default Gradient asGradient() {
        throw new ClassCastException("not a gradient value: " + type());
    }

    default String asMesh() {
        throw new ClassCastException("not a mesh value: " + type());
    }

    default String asString() {
        throw new ClassCastException("not a string value: " + type());
    }

    record FloatVal(float value) implements Value {
        @Override
        public ValueType type() {
            return ValueType.FLOAT;
        }

        @Override
        public float asFloat() {
            return value;
        }
    }

    record IntVal(int value) implements Value {
        @Override
        public ValueType type() {
            return ValueType.INT;
        }

        @Override
        public int asInt() {
            return value;
        }
    }

    record BoolVal(boolean value) implements Value {
        @Override
        public ValueType type() {
            return ValueType.BOOL;
        }

        @Override
        public boolean asBool() {
            return value;
        }
    }

    record Vec2Val(float x, float y) implements Value {
        @Override
        public ValueType type() {
            return ValueType.VEC2;
        }

        @Override
        public Vector2f asVec2() {
            return new Vector2f(x, y);
        }
    }

    record Vec3Val(float x, float y, float z) implements Value {
        @Override
        public ValueType type() {
            return ValueType.VEC3;
        }

        @Override
        public Vector3f asVec3() {
            return new Vector3f(x, y, z);
        }
    }

    record Vec4Val(float x, float y, float z, float w) implements Value {
        @Override
        public ValueType type() {
            return ValueType.VEC4;
        }

        @Override
        public Vector4f asVec4() {
            return new Vector4f(x, y, z, w);
        }
    }

    record ColorVal(float r, float g, float b, float a) implements Value {
        @Override
        public ValueType type() {
            return ValueType.COLOR;
        }

        @Override
        public Vector4f asColor() {
            return new Vector4f(r, g, b, a);
        }

        @Override
        public Vector4f asVec4() {
            return new Vector4f(r, g, b, a);
        }
    }

    record SamplerVal(String path) implements Value {
        @Override
        public ValueType type() {
            return ValueType.SAMPLER;
        }

        @Override
        public String asSampler() {
            return path;
        }
    }

    record TimeVal(float seconds) implements Value {
        @Override
        public ValueType type() {
            return ValueType.TIME;
        }

        @Override
        public float asFloat() {
            return seconds;
        }
    }

    record CurveVal(Curve curve) implements Value {
        @Override
        public ValueType type() {
            return ValueType.CURVE;
        }

        @Override
        public Curve asCurve() {
            return curve;
        }
    }

    record GradientVal(Gradient gradient) implements Value {
        @Override
        public ValueType type() {
            return ValueType.GRADIENT;
        }

        @Override
        public Gradient asGradient() {
            return gradient;
        }
    }

    record MeshVal(String path) implements Value {
        @Override
        public ValueType type() {
            return ValueType.MESH;
        }

        @Override
        public String asMesh() {
            return path;
        }
    }

    record StringVal(String value) implements Value {
        @Override
        public ValueType type() {
            return ValueType.STRING;
        }

        @Override
        public String asString() {
            return value;
        }
    }
}
