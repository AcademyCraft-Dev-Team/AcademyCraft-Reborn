package org.academy.api.client.render.shader.codegen;

/**
 * 带缩进的 GLSL 源拼接器。
 */
public final class GlslWriter {
    private final StringBuilder sb = new StringBuilder();
    private int indent;

    public GlslWriter line(String line) {
        sb.append("    ".repeat(indent)).append(line).append('\n');
        return this;
    }

    public GlslWriter blank() {
        sb.append('\n');
        return this;
    }

    /**
     * 追加原始文本（不追加换行/缩进）。
     */
    public GlslWriter raw(String text) {
        sb.append(text);
        return this;
    }

    public void push() {
        indent++;
    }

    public void pop() {
        indent = Math.max(0, indent - 1);
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
