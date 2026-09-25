package org.academy.api.client.render.shader.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlslWriterTest {
    @Test
    void writesIndentedLines() {
        var w = new GlslWriter();
        w.line("void main() {");
        w.push();
        w.line("float x = 1.0;");
        w.pop();
        w.line("}");
        assertEquals("void main() {\n    float x = 1.0;\n}\n", w.toString());
    }

    @Test
    void blankAndRaw() {
        var w = new GlslWriter();
        w.line("a");
        w.blank();
        w.raw("#define X 1");
        assertEquals("a\n\n#define X 1", w.toString());
    }

    @Test
    void popFloorsAtZero() {
        var w = new GlslWriter();
        w.pop();
        w.line("x");
        assertEquals("x\n", w.toString());
    }
}
