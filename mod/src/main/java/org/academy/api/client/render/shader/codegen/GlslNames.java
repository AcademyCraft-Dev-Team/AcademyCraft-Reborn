package org.academy.api.client.render.shader.codegen;

/**
 * GLSL 标识符命名约定（生成器与 UniformLayout 共享）。
 */
public final class GlslNames {
    private GlslNames() {
    }

    public static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    public static String uniformName(String parameterId) {
        return "u_" + sanitize(parameterId);
    }

    public static String varName(String nodeId, String portId) {
        return "v_" + sanitize(nodeId) + "_" + sanitize(portId);
    }
}
