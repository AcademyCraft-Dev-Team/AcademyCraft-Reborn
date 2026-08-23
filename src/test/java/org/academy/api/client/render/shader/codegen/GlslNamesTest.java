package org.academy.api.client.render.shader.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GlslNamesTest {
    @Test
    void sanitizeReplacesInvalidChars() {
        assertEquals("a_b_c", GlslNames.sanitize("a b-c"));
        assertEquals("abc", GlslNames.sanitize("abc"));
        assertEquals("_", GlslNames.sanitize("!"));
    }

    @Test
    void uniformNamePrefixed() {
        assertEquals("u_strength", GlslNames.uniformName("strength"));
        assertEquals("u_my_param", GlslNames.uniformName("my param"));
    }

    @Test
    void varNameCombinesNodeAndPort() {
        assertEquals("v_node_out", GlslNames.varName("node", "out"));
        assertEquals("v_add_result_0", GlslNames.varName("add result", "0"));
    }
}
