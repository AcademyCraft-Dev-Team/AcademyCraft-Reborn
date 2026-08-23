package org.academy.api.client.render.shader.codegen;

/**
 * 向量分量 swizzle 后缀常量。
 */
public final class Swizzle {
    public static final String X = ".x";
    public static final String XY = ".xy";
    public static final String XYZ = ".xyz";
    public static final String XYZW = ".xyzw";

    private Swizzle() {
    }

    /** 按分量数返回 swizzle 后缀。 */
    public static String of(int components) {
        return switch (components) {
            case 1 -> X;
            case 2 -> XY;
            case 3 -> XYZ;
            case 4 -> XYZW;
            default -> throw new IllegalArgumentException("invalid component count: " + components);
        };
    }
}
