package org.academy.api.client.render.shader.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SwizzleTest {
    @Test
    void suffixesByComponentCount() {
        assertEquals(".x", Swizzle.of(1));
        assertEquals(".xy", Swizzle.of(2));
        assertEquals(".xyz", Swizzle.of(3));
        assertEquals(".xyzw", Swizzle.of(4));
    }

    @Test
    void invalidCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> Swizzle.of(0));
        assertThrows(IllegalArgumentException.class, () -> Swizzle.of(5));
    }
}
